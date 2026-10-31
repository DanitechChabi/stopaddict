-- Tests RLS et fonctions serveur de StopAddict.
-- À exécuter après _local_bootstrap.sql puis les migrations 0001..0004, sur une
-- base jetable. Chaque assertion imprime 'PASS …' ou 'FAIL …'.
-- (Sur un vrai projet Supabase, préférer pgTAP via `supabase test db`.)

\pset tuples_only on
\pset format unaligned

-- ---------------------------------------------------------------------------
-- Jeu de données (en superutilisateur : contourne la RLS)
-- ---------------------------------------------------------------------------
-- L'insertion dans auth.users déclenche la création automatique du profil
-- (migration 0005), avec le rôle tiré des métadonnées.
insert into auth.users(id,email,raw_user_meta_data) values
 ('11111111-1111-1111-1111-111111111111','adminA@x.co','{"role":"admin"}'),
 ('22222222-2222-2222-2222-222222222222','adminB@x.co','{"role":"admin"}'),
 ('33333333-3333-3333-3333-333333333333','userA@x.co','{"role":"user"}'),
 ('44444444-4444-4444-4444-444444444444','userB@x.co','{"role":"user"}');
select case when (select role::text from profiles where id='11111111-1111-1111-1111-111111111111')='admin'
         and (select role::text from profiles where id='33333333-3333-3333-3333-333333333333')='user'
            then 'PASS' else 'FAIL' end||' f0 trigger crée les profils avec le bon rôle';
insert into devices(id,user_id,admin_id,device_name,device_status) values
 ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa','33333333-3333-3333-3333-333333333333','11111111-1111-1111-1111-111111111111','Tel A','active'),
 ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb','44444444-4444-4444-4444-444444444444','22222222-2222-2222-2222-222222222222','Tel B','active'),
 ('cafe0000-0000-0000-0000-000000000001','44444444-4444-4444-4444-444444444444','22222222-2222-2222-2222-222222222222','Tel neuf','active');
insert into commitments(id,device_id,admin_id,user_id,duration_days,status,start_at,end_at,user_consent_at,admin_confirmation_at) values
 ('cccccccc-cccc-cccc-cccc-cccccccccccc','aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa','11111111-1111-1111-1111-111111111111','33333333-3333-3333-3333-333333333333',90,'active',now(),now()+interval '90 days',now(),now()),
 ('dddddddd-dddd-dddd-dddd-dddddddddddd','bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb','22222222-2222-2222-2222-222222222222','44444444-4444-4444-4444-444444444444',30,'active',now(),now()+interval '30 days',now(),now());
insert into block_stats_daily(device_id,day,category,block_count) values
 ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',current_date,'pornography',5),
 ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',current_date,'pornography',9);

-- ---------------------------------------------------------------------------
-- RLS : isolement administrateur / utilisateur
-- ---------------------------------------------------------------------------
begin; set local role authenticated; select set_config('request.jwt.claims','{"sub":"11111111-1111-1111-1111-111111111111"}',true);
select case when count(*)=1 and bool_and(id='aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa') then 'PASS' else 'FAIL' end||' t1 adminA voit uniquement son appareil' from devices; rollback;

begin; set local role authenticated; select set_config('request.jwt.claims','{"sub":"11111111-1111-1111-1111-111111111111"}',true);
select case when count(*)=0 then 'PASS' else 'FAIL' end||' t2 adminA ne voit pas l''appareil de B' from devices where id='bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'; rollback;

begin; set local role authenticated; select set_config('request.jwt.claims','{"sub":"33333333-3333-3333-3333-333333333333"}',true);
select case when count(*)=1 then 'PASS' else 'FAIL' end||' t3 userA voit son appareil' from devices where id='aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'; rollback;

begin; set local role authenticated; select set_config('request.jwt.claims','{"sub":"44444444-4444-4444-4444-444444444444"}',true);
select case when count(*)=0 then 'PASS' else 'FAIL' end||' t4 userB ne voit pas l''appareil de A' from devices where id='aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'; rollback;

begin; set local role authenticated; select set_config('request.jwt.claims','{"sub":"11111111-1111-1111-1111-111111111111"}',true);
select case when count(*)=1 and bool_and(id='cccccccc-cccc-cccc-cccc-cccccccccccc') then 'PASS' else 'FAIL' end||' t5 adminA voit uniquement son engagement' from commitments; rollback;

begin; set local role authenticated; select set_config('request.jwt.claims','{"sub":"44444444-4444-4444-4444-444444444444"}',true);
select case when count(*)=0 then 'PASS' else 'FAIL' end||' t6 userB ne voit pas l''engagement de A' from commitments where id='cccccccc-cccc-cccc-cccc-cccccccccccc'; rollback;

begin; set local role authenticated; select set_config('request.jwt.claims','{"sub":"33333333-3333-3333-3333-333333333333"}',true);
select case when count(*)=1 then 'PASS' else 'FAIL' end||' t7 userA voit le profil de son admin' from profiles where id='11111111-1111-1111-1111-111111111111'; rollback;

begin; set local role authenticated; select set_config('request.jwt.claims','{"sub":"33333333-3333-3333-3333-333333333333"}',true);
select case when count(*)=0 then 'PASS' else 'FAIL' end||' t8 userA ne voit pas le profil de l''admin B' from profiles where id='22222222-2222-2222-2222-222222222222'; rollback;

begin; set local role authenticated; select set_config('request.jwt.claims','{"sub":"11111111-1111-1111-1111-111111111111"}',true);
select case when count(*)=1 and bool_and(device_id='aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa') then 'PASS' else 'FAIL' end||' t9 adminA voit uniquement ses stats agrégées' from block_stats_daily; rollback;

begin; set local role authenticated; select set_config('request.jwt.claims','{"sub":"11111111-1111-1111-1111-111111111111"}',true);
select case when auth_is_admin() then 'PASS' else 'FAIL' end||' t10a auth_is_admin vrai pour adminA'; rollback;
begin; set local role authenticated; select set_config('request.jwt.claims','{"sub":"33333333-3333-3333-3333-333333333333"}',true);
select case when not auth_is_admin() then 'PASS' else 'FAIL' end||' t10b auth_is_admin faux pour userA'; rollback;

-- ---------------------------------------------------------------------------
-- Fonctions serveur
-- ---------------------------------------------------------------------------
select bump_block_stats('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',current_date,'pornography',3);
select case when block_count=8 then 'PASS' else 'FAIL' end||' f1 bump_block_stats incrémente (5+3=8)'
  from block_stats_daily where device_id='aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa' and category='pornography';

select status from release_commitment('cccccccc-cccc-cccc-cccc-cccccccccccc') \gset r_
select case when :'r_status'='released_early' then 'PASS' else 'FAIL' end||' f2 release_commitment clôt l''engagement';
do $$ begin perform release_commitment('cccccccc-cccc-cccc-cccc-cccccccccccc');
  raise notice 'FAIL f3 double release aurait dû échouer';
exception when others then raise notice 'PASS f3 double release rejeté (%)', sqlerrm; end $$;

insert into admin_invitations(admin_id,invitation_code_hash,expires_at,status)
 values ('11111111-1111-1111-1111-111111111111','HASHTEST',now()+interval '15 min','pending');
select device_id from redeem_invitation('HASHTEST','33333333-3333-3333-3333-333333333333','Nouveau Tel','14') \gset rd_
select case when :'rd_device_id' is not null then 'PASS' else 'FAIL' end||' f4 redeem_invitation crée l''appareil';
do $$ begin perform redeem_invitation('HASHTEST','33333333-3333-3333-3333-333333333333','Tel','14');
  raise notice 'FAIL f5 code réutilisé aurait dû échouer';
exception when others then raise notice 'PASS f5 code à usage unique (%)', sqlerrm; end $$;

insert into commitments(id,device_id,admin_id,user_id,duration_days,status,admin_confirmation_at)
 values ('ba5e0000-0000-0000-0000-000000000001','cafe0000-0000-0000-0000-000000000001','22222222-2222-2222-2222-222222222222','44444444-4444-4444-4444-444444444444',60,'pending_consent',now());
select status,(end_at::date-start_at::date) as days
  from activate_on_user_consent('ba5e0000-0000-0000-0000-000000000001','44444444-4444-4444-4444-444444444444') \gset ac_
select case when :'ac_status'='active' and :'ac_days'=60 then 'PASS' else 'FAIL' end||' f6 activate -> active + end_at=+60j';
do $$ begin
  insert into commitments(device_id,admin_id,user_id,duration_days,status,admin_confirmation_at)
   values ('cafe0000-0000-0000-0000-000000000001','22222222-2222-2222-2222-222222222222','44444444-4444-4444-4444-444444444444',30,'pending_consent',now());
  raise notice 'FAIL f8 deux engagements vivants acceptés';
exception when unique_violation then raise notice 'PASS f8 un seul engagement vivant par appareil'; end $$;
