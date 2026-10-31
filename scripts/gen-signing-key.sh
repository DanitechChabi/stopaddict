#!/usr/bin/env bash
# Génère la paire de clés Ed25519 de signature des engagements.
#
# - La clé PRIVÉE (PKCS8 DER base64) est un SECRET SERVEUR : à placer dans les
#   secrets Supabase, jamais dans le dépôt ni dans l'APK.
# - La clé PUBLIQUE (SPKI DER base64) est embarquée dans l'app pour vérifier.
#
# Usage : ./scripts/gen-signing-key.sh
set -euo pipefail

tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT

openssl genpkey -algorithm ed25519 -out "$tmp" 2>/dev/null

priv="$(openssl pkey -in "$tmp" -outform DER | base64 -w0)"
pub="$(openssl pkey -in "$tmp" -pubout -outform DER | base64 -w0)"

cat <<EOF
# --- SECRET SERVEUR (à définir via 'supabase secrets set', jamais commiter) ---
SIGNING_PRIVATE_KEY_PKCS8_B64=$priv

# --- PUBLIC (SIGNING_PUBLIC_KEY_SPKI_B64 dans .env / embarqué dans l'app) ---
SIGNING_PUBLIC_KEY_SPKI_B64=$pub
EOF
