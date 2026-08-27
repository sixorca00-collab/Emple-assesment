# migrator one-shot: corre Flyway (V1..Vn) como superusuario bootstrap,
# fija la password real del rol de aplicacion y carga el seed. Idempotente.
FROM flyway/flyway:10-alpine

# psql para el ALTER ROLE y la carga del seed (la imagen de Flyway no lo trae)
USER root
RUN apk add --no-cache postgresql-client

# migraciones SQL planas numeradas + utilitario de datos + corpus
COPY db/migrations /flyway/sql
COPY db/seed.json /seed/seed.json
COPY db/seed_loader.sql /seed/seed_loader.sql
COPY db/docker-entrypoint.sh /usr/local/bin/riwi-migrate.sh
RUN chmod +x /usr/local/bin/riwi-migrate.sh

ENTRYPOINT ["/usr/local/bin/riwi-migrate.sh"]
