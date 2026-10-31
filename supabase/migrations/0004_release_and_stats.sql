-- StopAddict — fonctions de libération d'engagement et de compteurs agrégés.

-- release_commitment : clôt un engagement de façon anticipée.
-- Statut final 'released_early'. Libère le verrou unique (un seul engagement
-- vivant par appareil) pour permettre un futur engagement.
create or replace function release_commitment(
    p_commitment_id uuid
) returns commitments
language plpgsql security definer set search_path = public as $$
declare
    v_row commitments;
begin
    update commitments
       set status = 'released_early'
     where id = p_commitment_id
       and status = 'active'
     returning * into v_row;

    if not found then
        raise exception 'commitment_not_active';
    end if;

    return v_row;
end;
$$;

-- bump_block_stats : incrémente de façon atomique un compteur de blocages
-- AGRÉGÉ (par appareil, jour et catégorie). Aucune URL, aucun contenu.
create or replace function bump_block_stats(
    p_device_id uuid,
    p_day       date,
    p_category  text,
    p_count     integer
) returns void
language plpgsql security definer set search_path = public as $$
begin
    insert into block_stats_daily (device_id, day, category, block_count)
    values (p_device_id, p_day, p_category, greatest(p_count, 0))
    on conflict (device_id, day, category)
    do update set block_count = block_stats_daily.block_count + greatest(excluded.block_count, 0);
end;
$$;

-- process_due_emergency_releases : libère les demandes d'urgence dont le délai
-- de réflexion (72 h) est écoulé et qui n'ont pas été annulées. Destinée à être
-- appelée périodiquement (pg_cron ou Edge Function planifiée).
create or replace function process_due_emergency_releases()
returns integer
language plpgsql security definer set search_path = public as $$
declare
    r record;
    n integer := 0;
begin
    for r in
        select rr.id as release_id, rr.commitment_id
          from release_requests rr
         where rr.kind = 'emergency'
           and rr.status = 'pending'
           and rr.auto_release_at is not null
           and rr.auto_release_at <= now()
    loop
        update release_requests
           set status = 'auto_approved', reviewed_at = now()
         where id = r.release_id;

        update commitments
           set status = 'released_early'
         where id = r.commitment_id
           and status = 'active';

        insert into security_events (device_id, event_type, severity, detail)
        select c.device_id, 'emergency_release_auto', 'warning',
               'Libération automatique après délai de réflexion'
          from commitments c where c.id = r.commitment_id;

        n := n + 1;
    end loop;
    return n;
end;
$$;
