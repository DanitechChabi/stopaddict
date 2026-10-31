-- StopAddict — création automatique du profil à l'inscription.
-- À l'insertion dans auth.users, on crée la ligne profiles correspondante.
-- Le rôle vient des métadonnées d'inscription (role = 'admin' pour l'app Admin),
-- par défaut 'user'. Côté client, seule l'app Admin demande le rôle admin.

create or replace function handle_new_user() returns trigger
language plpgsql security definer set search_path = public, auth as $$
begin
    insert into public.profiles (id, email, role, display_name)
    values (
        new.id,
        new.email,
        coalesce((new.raw_user_meta_data ->> 'role')::user_role, 'user'),
        new.raw_user_meta_data ->> 'display_name'
    )
    on conflict (id) do nothing;
    return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function handle_new_user();
