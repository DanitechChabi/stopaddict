# StopAddict

Protection Android volontaire contre les contenus pornographiques et sexuellement
explicites, avec **administration externe** (une personne de confiance supervise) et
**engagement à durée verrouillée**.

> StopAddict est un outil de protection **consenti et transparent**, pas un outil de
> surveillance. Il n'enregistre ni ne transmet aucun contenu privé (voir
> [`docs/LIMITATIONS.md`](docs/LIMITATIONS.md), section 4).

## Composants

- **StopAddict** (`app-user`, `com.stopaddict.app`) — application de la personne protégée.
- **StopAddict Admin** (`app-admin`, `com.stopaddict.admin`) — application de l'administrateur.
- **Backend Supabase** (`supabase/`) — référence des engagements, RLS stricte, Edge Functions.

Les deux applications sont en Kotlin / Jetpack Compose / Material 3.

## État d'avancement

| Étape | Contenu | État |
|---|---|---|
| 1 | Socle multi-modules, modèle de domaine, backend SQL + RLS | **en cours** — `core:model` compile et teste au vert ; SQL écrit |
| 2 | Edge Functions, signatures Ed25519, tests RLS | **livré** — 9 Edge Functions ; crypto Ed25519 validée ; 18 tests RLS/fonctions au vert sur PostgreSQL 16 réel |
| 3 | Application Admin | à venir |
| 4 | Application utilisateur | à venir |
| 5 | Moteur VPN / DNS | à venir |
| 6 | Device Owner / DPC | à venir |
| 7 | Détection IA (image, vidéo, texte) | à venir |
| 8 | Protocoles de test par application, APK de test | à venir |

## Architecture (cible)

```
core/model        Modèle de domaine Kotlin pur, testable sans Android
core/designsystem Material 3, thème vert profond
core/network      Client Supabase (Ktor) + vérification des signatures
core/data         Room, DataStore, dépôts
core/security     Android Keystore, chiffrement local, horloge fiable
feature-user/*    welcome, pairing, permissions, dashboard, commitment, release
feature-admin/*   auth, devices, invitations, commitments, requests
protection/vpn    VpnService (DNS uniquement), moteur de règles, listes de blocage
protection/dpc    DeviceAdminReceiver, politiques, provisionnement
protection/ai     ImageClassifier, VideoClassifier, TextClassifier (LiteRT)
protection/watchdog  redémarrage, WorkManager, contrôle de santé
supabase/*        migrations SQL, Edge Functions, tests
```

## Principes non négociables

1. Jamais de statut « Protection active » si un composant requis ne tourne pas réellement.
2. Aucun contenu privé enregistré ou transmis (pas de table pour ça).
3. Aucun secret Supabase dans l'APK ; la clé publique de signature seulement.
4. La durée d'un engagement ne change jamais sans consentement explicite, tracé des deux côtés.
5. Jamais de contournement des mécanismes de sécurité Android ; le téléphone reste utilisable
   (appels d'urgence, mises à jour, fonctions essentielles préservés).

## Compiler et tester (ce qui est disponible aujourd'hui)

```bash
./gradlew :core:model:test
```

Le modèle de domaine est pur JVM : il compile et se teste sans SDK Android.

### Note sur le dépôt Maven

Dans certains environnements, Maven Central renvoie des 429. `settings.gradle.kts`
pointe donc d'abord sur le miroir Google Cloud de Maven Central. Retirez cette ligne
pour un build classique.

## Backend Supabase

Voir [`supabase/README.md`](supabase/README.md). Les migrations créent le schéma, les
types, les index, les contraintes et la politique RLS. Aucune donnée de contenu n'est
stockée ; l'administrateur n'accède qu'aux données agrégées et techniques.

## Documentation

- [`docs/LIMITATIONS.md`](docs/LIMITATIONS.md) — limites techniques réelles, par capacité.
