#!/usr/bin/env bash
# Monte une base PostgreSQL éphémère, applique les migrations et joue les tests
# RLS/fonctions. Aucune dépendance réseau. Sortie non nulle si un test échoue.
#
# Usage : supabase/tests/run_local.sh
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
MIG="$HERE/../migrations"
PGBIN="$(ls -d /usr/lib/postgresql/*/bin 2>/dev/null | sort -V | tail -1)"
[ -n "$PGBIN" ] || { echo "PostgreSQL introuvable"; exit 1; }

PORT=$(( (RANDOM % 1000) + 54000 ))
BASE="$(mktemp -d /tmp/stopaddict_pg.XXXXXX)"
RUNUSER=""
# initdb refuse de tourner en root : basculer sur l'utilisateur postgres.
if [ "$(id -u)" = "0" ]; then RUNUSER="postgres"; chown -R postgres "$BASE"; fi
run() { if [ -n "$RUNUSER" ]; then su "$RUNUSER" -c "$*"; else bash -c "$*"; fi; }

cleanup() {
  run "$PGBIN/pg_ctl -D '$BASE/data' stop -m immediate" >/dev/null 2>&1 || true
  rm -rf "$BASE"
}
trap cleanup EXIT

run "$PGBIN/initdb -D '$BASE/data' -U postgres --auth=trust" >/dev/null
run "$PGBIN/pg_ctl -D '$BASE/data' -o '-p $PORT -k /tmp' -l '$BASE/pg.log' start" >/dev/null
sleep 2

PSQL="psql -h /tmp -p $PORT -U postgres -d postgres -v ON_ERROR_STOP=1 -q"
$PSQL -f "$HERE/_local_bootstrap.sql"
for f in 0001_schema 0002_rls 0003_functions 0004_release_and_stats 0005_new_user_profile; do
  $PSQL -f "$MIG/$f.sql"
done
# Supabase accorde automatiquement ces privilèges à anon/authenticated/service_role ;
# sur une base nue, on les accorde ici (la RLS reste la vraie barrière).
$PSQL -c "grant select,insert,update,delete on all tables in schema public to anon, authenticated;
          grant usage,select on all sequences in schema public to anon, authenticated;"

OUT="$(psql -h /tmp -p $PORT -U postgres -d postgres -q -f "$HERE/rls_and_functions_test.sql" 2>&1)"
echo "$OUT" | grep -E "PASS|FAIL" || true

PASS=$(echo "$OUT" | grep -c PASS || true)
FAIL=$(echo "$OUT" | grep -c FAIL || true)
ERRORS=$(echo "$OUT" | grep -cE "ERROR|denied" || true)
EXPECTED=19
echo "----------------------------------------"
echo "PASS=$PASS  FAIL=$FAIL  ERREURS=$ERRORS  (attendu PASS=$EXPECTED)"
# Succès seulement si tous les tests passent, aucun n'échoue, et aucune erreur
# SQL inattendue (ex. permission denied) n'a fait sauter une assertion.
[ "$FAIL" = "0" ] && [ "$ERRORS" = "0" ] && [ "$PASS" = "$EXPECTED" ]
