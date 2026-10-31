-- Stubs reproduisant ce que Supabase fournit (schéma auth, auth.uid(), rôles),
-- pour jouer les migrations et les tests sur une base PostgreSQL ordinaire.
-- Sur un vrai projet Supabase, ne PAS exécuter ce fichier : auth existe déjà.

create schema if not exists auth;
create table if not exists auth.users (
    id uuid primary key default gen_random_uuid(),
    email text,
    raw_user_meta_data jsonb default '{}'::jsonb
);
create or replace function auth.uid() returns uuid
language sql stable as $$
  select nullif(current_setting('request.jwt.claims', true)::jsonb ->> 'sub', '')::uuid;
$$;

do $$ begin
  if not exists (select from pg_roles where rolname='anon') then create role anon nologin; end if;
  if not exists (select from pg_roles where rolname='authenticated') then create role authenticated nologin; end if;
  if not exists (select from pg_roles where rolname='service_role') then create role service_role nologin bypassrls; end if;
end $$;
grant usage on schema public, auth to anon, authenticated, service_role;
