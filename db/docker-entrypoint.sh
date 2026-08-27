#!/bin/sh
# migrator: Flyway migrate + password real del rol de app + carga del seed (idempotente)
set -e

JDBC_URL="jdbc:postgresql://${POSTGRES_HOST}:${POSTGRES_PORT}/${POSTGRES_DB}"
PSQL_URL="postgresql://${POSTGRES_USER}:${POSTGRES_PASSWORD}@${POSTGRES_HOST}:${POSTGRES_PORT}/${POSTGRES_DB}"

# esperamos a que Postgres acepte conexiones
echo "[migrator] esperando a Postgres en ${POSTGRES_HOST}:${POSTGRES_PORT}..."
until pg_isready -h "${POSTGRES_HOST}" -p "${POSTGRES_PORT}" -U "${POSTGRES_USER}" >/dev/null 2>&1; do
  sleep 1
done

# migraciones Flyway como superusuario bootstrap (unica forma de crear el rol riwi_app)
echo "[migrator] aplicando migraciones Flyway..."
flyway -url="${JDBC_URL}" -user="${POSTGRES_USER}" -password="${POSTGRES_PASSWORD}" \
  -locations="filesystem:/flyway/sql" -baselineOnMigrate=true -connectRetries=10 migrate

# V2 crea riwi_app con password por defecto; aqui fijamos la real de DB_APP_PASSWORD
echo "[migrator] fijando la password del rol de aplicacion ${DB_APP_USER}..."
psql "${PSQL_URL}" -v ON_ERROR_STOP=1 -c \
  "ALTER ROLE ${DB_APP_USER} WITH LOGIN PASSWORD '${DB_APP_PASSWORD}'"

# seed solo si la base esta vacia (o SEED_FORCE=true): evita pisar datos de runtime en cada `up`
USER_COUNT=$(psql "${PSQL_URL}" -tAc "SELECT count(*) FROM rw_user" 2>/dev/null || echo 0)
if [ "${USER_COUNT}" = "0" ] || [ "${SEED_FORCE}" = "true" ]; then
  echo "[migrator] cargando el corpus seed.json (idempotente: trunca y recarga)..."
  psql "${PSQL_URL}" -v ON_ERROR_STOP=1 -v seed_path=/seed/seed.json -f /seed/seed_loader.sql
else
  echo "[migrator] rw_user ya tiene ${USER_COUNT} filas; se omite el seed (usa SEED_FORCE=true para forzar)."
fi

echo "[migrator] listo."
