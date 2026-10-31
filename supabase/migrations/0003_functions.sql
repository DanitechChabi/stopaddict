-- StopAddict — fonctions serveur pour les opérations atomiques.
-- Appelées exclusivement par les Edge Functions (service_role). SECURITY DEFINER
-- pour garantir l'atomicité (claim de code, activation) même sous concurrence.

-- redeem_invitation : consomme un code d'association et crée l'appareil.
-- Le passage pending -> redeemed et la création de l'appareil sont atomiques.
create or replace function redeem_invitation(
    p_code_hash       text,
    p_user_id         uuid,
    p_device_name     text,
    p_android_version text
) returns table (device_id uuid, admin_id uuid)
language plpgsql security definer set search_path = public as $$
declare
    v_inv       admin_invitations%rowtype;
    v_device_id uuid;
begin
    update admin_invitations
       set status = 'redeemed', used_at = now(), used_by = p_user_id
     where invitation_code_hash = p_code_hash
       and status = 'pending'
       and expires_at > now()
     returning * into v_inv;

    if not found then
        raise exception 'invitation_invalid';
    end if;

    insert into devices (user_id, admin_id, device_name, android_version, device_status)
    values (p_user_id, v_inv.admin_id, p_device_name, p_android_version, 'pending')
    returning id into v_device_id;

    return query select v_device_id, v_inv.admin_id;
end;
$$;

-- activate_on_user_consent : l'utilisateur consent ; l'administrateur ayant déjà
-- confirmé à la proposition, l'engagement devient actif. start_at/end_at sont
-- calculés à partir de l'heure SERVEUR. La signature est posée ensuite par
-- l'Edge Function (TS) via set_commitment_signature.
create or replace function activate_on_user_consent(
    p_commitment_id uuid,
    p_user_id       uuid
) returns commitments
language plpgsql security definer set search_path = public as $$
declare
    v_row commitments;
begin
    update commitments
       set user_consent_at = now(),
           status          = 'active',
           start_at        = now(),
           end_at          = now() + make_interval(days => duration_days)
     where id = p_commitment_id
       and user_id = p_user_id
       and status = 'pending_consent'
       and admin_confirmation_at is not null
     returning * into v_row;

    if not found then
        raise exception 'commitment_not_activatable';
    end if;

    return v_row;
end;
$$;

-- set_commitment_signature : enregistre la signature de l'engagement et crée la
-- version 1 de la politique (signée elle aussi). Insert-only pour policy_versions.
create or replace function set_commitment_signature(
    p_commitment_id    uuid,
    p_signed_payload   text,
    p_signature        text,
    p_policy_payload   jsonb,
    p_policy_signature text,
    p_actor            uuid
) returns void
language plpgsql security definer set search_path = public as $$
begin
    update commitments
       set signed_payload = p_signed_payload,
           signature      = p_signature
     where id = p_commitment_id;

    insert into policy_versions (commitment_id, version, payload, signature, created_by)
    values (p_commitment_id, 1, p_policy_payload, p_policy_signature, p_actor)
    on conflict (commitment_id, version) do nothing;
end;
$$;
