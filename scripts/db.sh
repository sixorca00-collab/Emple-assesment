#!/usr/bin/env bash
# helper de base de datos: corre migraciones y/o (re)carga el seed via el servicio migrator del compose.
#
#   scripts/db.sh migrate   -> Flyway migrate + seed solo si la base esta vacia (comportamiento de `docker compose up`)
#   scripts/db.sh seed       -> Flyway migrate + fuerza la recarga del corpus seed.json
set -euo pipefail
cd "$(dirname "$0")/.."

case "${1:-migrate}" in
  migrate)
    docker compose run --rm migrator
    ;;
  seed)
    docker compose run --rm -e SEED_FORCE=true migrator
    ;;
  *)
    echo "uso: scripts/db.sh [migrate|seed]" >&2
    exit 1
    ;;
esac
