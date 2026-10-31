# Edge Functions StopAddict

Toutes les écritures sensibles passent par ici (service_role), jamais directement
depuis les applications. Les fonctions valident le JWT de l'appelant côté serveur
et vérifient son rôle.

| Fonction | Appelant | Rôle |
|---|---|---|
| `create-invitation` | admin | Génère un code d'association à usage unique (code clair renvoyé une seule fois). |
| `redeem-invitation` | user | Consomme le code, crée l'appareil (statut `pending`), renvoie l'identité de l'admin. |
| `propose-commitment` | admin | Propose un engagement (`pending_consent`) + politique par défaut. |
| `consent-commitment` | user | Consentement explicite → engagement actif, signé Ed25519 (engagement + politique v1). |
| `request-release` | user | Demande de libération : `standard` (décision admin) ou `emergency` (auto après 72 h). |
| `review-release` | admin | Statue sur une demande standard : `approved` (clôt l'engagement) / `refused` / `needs_confirmation`. |
| `cancel-release` | user | Annule sa demande d'urgence pendant le délai de réflexion. |
| `sync` | user | Renvoie l'engagement signé, la politique courante et l'heure serveur (ancre temporelle) ; enregistre la synchro. |
| `report-event` | user | Remonte des événements techniques et des compteurs **agrégés** (jamais de contenu). |

### Libération automatique (urgence)

`process_due_emergency_releases()` (migration `0004`) libère les demandes d'urgence
dont le délai de 72 h est écoulé. À planifier, au choix :

```sql
-- pg_cron, toutes les 10 minutes :
select cron.schedule('sa-due-releases', '*/10 * * * *', $$select process_due_emergency_releases()$$);
```

ou une Edge Function planifiée appelant cette fonction.

## Modules partagés (`_shared/`)

- `http.ts` — CORS, garde de méthode, réponses JSON.
- `clients.ts` — clients Supabase ; `requireCaller` valide le JWT et renvoie le profil.
- `codes.ts` — génération et hachage SHA-256 des codes d'association.
- `canonical.ts` — JSON canonique (clés triées) : la signature porte sur des octets déterministes.
- `signing.ts` — signature/vérification Ed25519 (Web Crypto).

## Clés de signature

```bash
./scripts/gen-signing-key.sh
```

Place la clé **privée** dans les secrets du projet (jamais dans le dépôt) et la
clé **publique** dans l'app / `.env` :

```bash
supabase secrets set SIGNING_PRIVATE_KEY_PKCS8_B64="..."
supabase secrets set INVITATION_TTL_MINUTES=15
supabase secrets set RELEASE_DELAY_HOURS=72
```

`SUPABASE_URL`, `SUPABASE_ANON_KEY` et `SUPABASE_SERVICE_ROLE_KEY` sont injectées
automatiquement par Supabase dans les Edge Functions.

## Vérifier et déployer

```bash
# Vérification de types (embarque Deno) :
supabase functions serve            # local, ou :
deno check supabase/functions/**/*.ts

# Déploiement :
supabase functions deploy create-invitation
supabase functions deploy redeem-invitation
supabase functions deploy propose-commitment
supabase functions deploy consent-commitment
```

> Note : la validation de types n'a pas été exécutée dans l'environnement de
> développement (Deno non disponible). Elle se fait au déploiement via le CLI
> Supabase. La logique cryptographique (Ed25519 + JSON canonique) a, elle, été
> validée par un test de bout en bout (signature/vérification et rejet d'un
> message altéré).
