# Backend StopAddict (Supabase)

## Migrations

| Fichier | Rôle |
|---|---|
| `migrations/0001_schema.sql` | Types, tables, index, contraintes. Dates en UTC. |
| `migrations/0002_rls.sql` | Row Level Security : chaque partie ne voit que ce qui la concerne. |

Tables : `profiles`, `admin_invitations`, `devices`, `commitments`,
`commitment_amendments`, `protection_policies`, `policy_versions`,
`release_requests`, `block_stats_daily`, `security_events`, `device_sync`.

## Appliquer

Avec le CLI Supabase et un projet déjà lié :

```bash
supabase db push        # applique les migrations
```

Ou directement sur une base PostgreSQL de test :

```bash
psql "$DATABASE_URL" -f migrations/0001_schema.sql
psql "$DATABASE_URL" -f migrations/0002_rls.sql
```

## Règles de confidentialité et de sécurité

- **Aucune table de contenu.** Pas d'URL, pas d'image, pas de message. L'administrateur
  ne peut donc rien lire de privé — la donnée n'existe pas.
- **Écritures sensibles côté serveur uniquement.** Activation d'engagement, invitations,
  libérations et signatures passeront par des Edge Functions utilisant `service_role`
  (étape 2). Les applications n'ont que la clé **anon** et ne peuvent pas écrire ces tables.
- **RLS activée sur toutes les tables.** Un administrateur ne voit que les appareils qu'il
  supervise ; un utilisateur, que les siens.
- **Aucun secret dans l'APK.** La clé privée de signature (Ed25519) reste dans les secrets
  Supabase ; l'application n'embarque que la clé publique pour vérifier engagements et politiques.

## À venir (étape 2)

- Edge Functions : `create_invitation`, `redeem_invitation`, `activate_commitment`,
  `request_release`, `review_release`, `emergency_release`, `sync`, `report_event`.
- Signature Ed25519 des engagements et des versions de politique.
- Tests pgTAP des règles RLS (un admin ne lit pas les appareils d'un autre, etc.).
