-- StopAddict — schéma initial
-- Toutes les dates sont stockées en UTC (timestamptz).
-- Le serveur est la référence : aucune durée d'engagement n'est calculée à partir
-- de l'horloge locale du téléphone.

create extension if not exists "pgcrypto";

-- -----------------------------------------------------------------------------
-- Rôles applicatifs
-- -----------------------------------------------------------------------------
create type user_role as enum ('user', 'admin');
create type management_mode as enum ('standard', 'device_owner');
create type device_status as enum ('pending', 'active', 'offline', 'released', 'wiped');
create type commitment_status as enum ('pending_consent', 'active', 'completed', 'released_early', 'cancelled');
create type invitation_status as enum ('pending', 'redeemed', 'revoked', 'expired');
create type release_status as enum ('pending', 'approved', 'refused', 'needs_confirmation', 'auto_approved');
create type release_kind as enum ('standard', 'emergency');
create type event_severity as enum ('info', 'warning', 'critical');

-- -----------------------------------------------------------------------------
-- profiles : étend auth.users (Supabase Auth)
-- -----------------------------------------------------------------------------
create table profiles (
    id          uuid primary key references auth.users (id) on delete cascade,
    email       text not null,
    role        user_role not null default 'user',
    display_name text,
    created_at  timestamptz not null default now()
);

-- -----------------------------------------------------------------------------
-- admin_invitations : codes d'association à usage unique, à durée de vie courte.
-- Seul le hash du code est stocké ; le code clair ne vit que dans le QR / l'écran.
-- -----------------------------------------------------------------------------
create table admin_invitations (
    id                   uuid primary key default gen_random_uuid(),
    admin_id             uuid not null references profiles (id) on delete cascade,
    invitation_code_hash text not null,
    label                text,
    expires_at           timestamptz not null,
    used_at              timestamptz,
    used_by              uuid references profiles (id),
    status               invitation_status not null default 'pending',
    created_at           timestamptz not null default now()
);
create index admin_invitations_admin_idx on admin_invitations (admin_id);
create unique index admin_invitations_code_idx on admin_invitations (invitation_code_hash);

-- -----------------------------------------------------------------------------
-- devices : un appareil protégé, lié à un utilisateur et à son administrateur.
-- -----------------------------------------------------------------------------
create table devices (
    id              uuid primary key default gen_random_uuid(),
    user_id         uuid not null references profiles (id) on delete cascade,
    admin_id        uuid not null references profiles (id) on delete restrict,
    device_name     text not null,
    android_version text,
    management_mode management_mode not null default 'standard',
    device_status   device_status not null default 'pending',
    fcm_token       text,
    last_sync_at    timestamptz,
    created_at      timestamptz not null default now()
);
create index devices_user_idx on devices (user_id);
create index devices_admin_idx on devices (admin_id);

-- -----------------------------------------------------------------------------
-- commitments : l'engagement. La période fait foi côté serveur.
-- user_consent_at et admin_confirmation_at doivent tous deux être renseignés
-- pour qu'un engagement passe à 'active' (double consentement).
-- -----------------------------------------------------------------------------
create table commitments (
    id                   uuid primary key default gen_random_uuid(),
    device_id            uuid not null references devices (id) on delete cascade,
    admin_id             uuid not null references profiles (id) on delete restrict,
    user_id              uuid not null references profiles (id) on delete cascade,
    start_at             timestamptz,
    end_at               timestamptz,
    duration_days        integer not null check (duration_days between 1 and 366),
    status               commitment_status not null default 'pending_consent',
    user_consent_at      timestamptz,
    admin_confirmation_at timestamptz,
    signature            text,            -- signature Ed25519 serveur (base64)
    signed_payload       text,            -- payload canonique signé
    created_at           timestamptz not null default now(),
    constraint end_after_start check (end_at is null or start_at is null or end_at > start_at)
);
create index commitments_device_idx on commitments (device_id);
-- un seul engagement actif ou en attente de consentement par appareil
create unique index commitments_one_live_per_device
    on commitments (device_id)
    where status in ('pending_consent', 'active');

-- -----------------------------------------------------------------------------
-- commitment_amendments : toute modification d'un engagement actif.
-- Trace le consentement explicite des deux parties. On ne modifie jamais
-- commitments.end_at sans une ligne 'applied' ici.
-- -----------------------------------------------------------------------------
create table commitment_amendments (
    id                 uuid primary key default gen_random_uuid(),
    commitment_id      uuid not null references commitments (id) on delete cascade,
    requested_by       uuid not null references profiles (id),
    old_end_at         timestamptz not null,
    new_end_at         timestamptz not null,
    user_consent_at    timestamptz,
    admin_consent_at   timestamptz,
    applied_at         timestamptz,
    created_at         timestamptz not null default now()
);
create index commitment_amendments_commitment_idx on commitment_amendments (commitment_id);

-- -----------------------------------------------------------------------------
-- protection_policies : politique courante d'un engagement (dénormalisée pour lecture).
-- L'historique signé et versionné est dans policy_versions.
-- -----------------------------------------------------------------------------
create table protection_policies (
    id                  uuid primary key default gen_random_uuid(),
    commitment_id       uuid not null references commitments (id) on delete cascade,
    dns_filter_enabled  boolean not null default true,
    image_filter_enabled boolean not null default false,
    video_filter_enabled boolean not null default false,
    text_filter_enabled boolean not null default false,
    strictness_level    smallint not null default 2 check (strictness_level between 1 and 3),
    blocked_categories  text[] not null default array['pornography','adult_sites'],
    current_version     integer not null default 1,
    updated_at          timestamptz not null default now(),
    unique (commitment_id)
);

-- Versions immuables et signées de la politique. On insère, on ne met jamais à jour.
create table policy_versions (
    id                  uuid primary key default gen_random_uuid(),
    commitment_id       uuid not null references commitments (id) on delete cascade,
    version             integer not null,
    payload             jsonb not null,  -- copie figée de la politique
    signature           text not null,   -- Ed25519 base64
    created_by          uuid not null references profiles (id),
    created_at          timestamptz not null default now(),
    unique (commitment_id, version)
);

-- -----------------------------------------------------------------------------
-- release_requests : demandes de libération (standard 72 h, ou urgence).
-- -----------------------------------------------------------------------------
create table release_requests (
    id              uuid primary key default gen_random_uuid(),
    commitment_id   uuid not null references commitments (id) on delete cascade,
    requested_by    uuid not null references profiles (id),
    kind            release_kind not null default 'standard',
    reason_category text,
    status          release_status not null default 'pending',
    auto_release_at timestamptz,          -- demande standard : now() + 72 h
    reviewed_at     timestamptz,
    reviewed_by     uuid references profiles (id),
    created_at      timestamptz not null default now()
);
create index release_requests_commitment_idx on release_requests (commitment_id);

-- -----------------------------------------------------------------------------
-- block_stats_daily : compteurs AGRÉGÉS uniquement. Aucune URL, aucun contenu.
-- C'est la seule donnée de blocage visible par l'administrateur.
-- -----------------------------------------------------------------------------
create table block_stats_daily (
    id          uuid primary key default gen_random_uuid(),
    device_id   uuid not null references devices (id) on delete cascade,
    day         date not null,
    category    text not null,
    block_count integer not null default 0,
    unique (device_id, day, category)
);
create index block_stats_device_day_idx on block_stats_daily (device_id, day);

-- -----------------------------------------------------------------------------
-- security_events : incidents techniques (VPN coupé, horloge incohérente,
-- redémarrage, désinstallation détectée, réinitialisation signalée…).
-- -----------------------------------------------------------------------------
create table security_events (
    id          uuid primary key default gen_random_uuid(),
    device_id   uuid not null references devices (id) on delete cascade,
    event_type  text not null,
    severity    event_severity not null default 'info',
    detail      text,
    created_at  timestamptz not null default now()
);
create index security_events_device_idx on security_events (device_id, created_at desc);

-- -----------------------------------------------------------------------------
-- device_sync : état de synchronisation politique / appareil.
-- -----------------------------------------------------------------------------
create table device_sync (
    id                  uuid primary key default gen_random_uuid(),
    device_id           uuid not null references devices (id) on delete cascade,
    last_policy_version integer not null default 0,
    sync_status         text not null default 'pending',
    updated_at          timestamptz not null default now(),
    unique (device_id)
);
