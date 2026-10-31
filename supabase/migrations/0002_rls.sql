-- StopAddict — Row Level Security
--
-- Principe : les clients (apps Android) ne LISENT que ce qui les concerne.
-- Les écritures sensibles (activation d'engagement, invitations, libérations,
-- signatures) passent par des Edge Functions utilisant la clé service_role,
-- qui contourne RLS. Les apps n'ont que la clé anon et ne peuvent donc pas
-- écrire directement sur ces tables.
--
-- Confidentialité : il n'existe AUCUNE table de contenu (URL, image, message).
-- L'administrateur ne peut donc rien lire de privé — ce n'est pas qu'une règle
-- RLS, c'est l'absence pure et simple de la donnée.

alter table profiles              enable row level security;
alter table admin_invitations     enable row level security;
alter table devices               enable row level security;
alter table commitments           enable row level security;
alter table commitment_amendments enable row level security;
alter table protection_policies   enable row level security;
alter table policy_versions       enable row level security;
alter table release_requests      enable row level security;
alter table block_stats_daily     enable row level security;
alter table security_events       enable row level security;
alter table device_sync           enable row level security;

-- Helpers ------------------------------------------------------------------
create or replace function auth_is_admin() returns boolean
language sql stable security definer set search_path = public as $$
    select exists (select 1 from profiles where id = auth.uid() and role = 'admin');
$$;

-- profiles -----------------------------------------------------------------
create policy profiles_self_read on profiles
    for select using (id = auth.uid());

-- Un utilisateur peut voir le profil de SON administrateur (nom, email),
-- afin de confirmer l'identité lors de l'association.
create policy profiles_admin_visible_to_supervised on profiles
    for select using (
        exists (select 1 from devices d where d.admin_id = profiles.id and d.user_id = auth.uid())
    );

create policy profiles_self_update on profiles
    for update using (id = auth.uid()) with check (id = auth.uid());

-- admin_invitations : l'administrateur gère les siennes.
create policy invitations_owner_all on admin_invitations
    for select using (admin_id = auth.uid());

-- devices ------------------------------------------------------------------
create policy devices_user_read on devices
    for select using (user_id = auth.uid());
create policy devices_admin_read on devices
    for select using (admin_id = auth.uid());
-- L'utilisateur peut mettre à jour des champs techniques de SON appareil
-- (jeton FCM, version Android, dernière sync). L'activation reste côté serveur.
create policy devices_user_update on devices
    for update using (user_id = auth.uid()) with check (user_id = auth.uid());

-- commitments : lecture par les deux parties, écriture par Edge Functions only.
create policy commitments_user_read on commitments
    for select using (user_id = auth.uid());
create policy commitments_admin_read on commitments
    for select using (admin_id = auth.uid());

-- commitment_amendments : lecture par les deux parties.
create policy amendments_read on commitment_amendments
    for select using (
        exists (select 1 from commitments c where c.id = commitment_id
                and (c.user_id = auth.uid() or c.admin_id = auth.uid()))
    );

-- protection_policies / policy_versions : lecture par les deux parties.
create policy policies_read on protection_policies
    for select using (
        exists (select 1 from commitments c where c.id = commitment_id
                and (c.user_id = auth.uid() or c.admin_id = auth.uid()))
    );
create policy policy_versions_read on policy_versions
    for select using (
        exists (select 1 from commitments c where c.id = commitment_id
                and (c.user_id = auth.uid() or c.admin_id = auth.uid()))
    );

-- release_requests : lecture par les deux parties ; l'utilisateur peut créer
-- une demande pour son propre engagement. La revue se fait côté serveur.
create policy release_read on release_requests
    for select using (
        exists (select 1 from commitments c where c.id = commitment_id
                and (c.user_id = auth.uid() or c.admin_id = auth.uid()))
    );
create policy release_user_insert on release_requests
    for insert with check (
        requested_by = auth.uid()
        and exists (select 1 from commitments c where c.id = commitment_id and c.user_id = auth.uid())
    );

-- block_stats_daily : compteurs agrégés, visibles des deux parties en lecture.
create policy block_stats_read on block_stats_daily
    for select using (
        exists (select 1 from devices d where d.id = device_id
                and (d.user_id = auth.uid() or d.admin_id = auth.uid()))
    );

-- security_events : visibles des deux parties en lecture.
create policy security_events_read on security_events
    for select using (
        exists (select 1 from devices d where d.id = device_id
                and (d.user_id = auth.uid() or d.admin_id = auth.uid()))
    );

-- device_sync : lecture par les deux parties.
create policy device_sync_read on device_sync
    for select using (
        exists (select 1 from devices d where d.id = device_id
                and (d.user_id = auth.uid() or d.admin_id = auth.uid()))
    );
