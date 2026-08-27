# Capa de base de datos - Riwi Co.

PostgreSQL 15+ con la extension **pgvector** (misma base, no un motor aparte).
Todo el DDL esta escrito a mano y versionado con **Flyway** (SQL plano numerado).

```
db/
├── migrations/
│   ├── V1__init.sql          -- tablas rw_*, PK/FK con ON DELETE, CHECK, indices unicos parciales, pgvector, tsvector/vector
│   ├── V2__rls.sql           -- rol de aplicacion sin BYPASSRLS + RLS sobre canales y mensajes + helpers de identidad
│   ├── V3__functions.sql     -- funciones transaccionales, vista de conversaciones, 2 stored procedures de usuarios
│   ├── V4__triggers.sql      -- trigger de sincronizacion de search_tsv, bloqueo de borrado fisico, updated_at
│   ├── V5__copilot.sql       -- version de prompt, backfill de embeddings, senal de "contexto en otro canal"
│   └── V6__copilot_ops.sql   -- re-embedding total (modo all) + cobertura de embeddings para el readiness
├── queries/                  -- las 4 consultas de la seccion 11, cada una documentada en su cabecera
├── seed.json                 -- corpus inicial (usuarios, canales, membresias, mensajes, lecturas, consultas al copiloto)
├── seed_loader.sql           -- utilitario que carga seed.json (NO es una migracion)
├── Dockerfile                -- imagen del servicio "migrator" del docker-compose (Flyway + psql)
├── docker-entrypoint.sh      -- migrate + ALTER ROLE riwi_app + carga idempotente del seed
├── NORMALIZACION.md          -- proceso 1FN -> 2FN -> 3FN (texto/tablas)
└── README.md
```

En `docker compose`, el servicio **migrator** hace todo esto automaticamente (ver `README.md` raiz);
las opciones de abajo son para correrlo a mano o depurar.

## Roles y arranque

- Las migraciones y la carga de seed se ejecutan con un **superusuario bootstrap** (el `POSTGRES_USER` del contenedor). Ese superusuario NO debe llamarse `riwi_app`.
- `V2__rls.sql` crea/asegura el rol de aplicacion **`riwi_app`** como `NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE`. El backend se conecta **siempre** como `riwi_app`, por lo que queda sujeto a RLS.
- Si el superusuario bootstrap se llamara `riwi_app` (no recomendado), PostgreSQL no permite quitarle `SUPERUSER` y RLS no lo restringiria: V2 emite un `WARNING` en ese caso.
- El actor autenticado se fija por transaccion:
  ```sql
  SET LOCAL app.current_user_id = '<uuid del JWT>';
  -- o, desde una funcion:  SELECT rw_set_current_user('<uuid>');
  ```

Variables (`.env`, ver `.env.example` en la raiz): `POSTGRES_DB`, `POSTGRES_USER` (bootstrap), `POSTGRES_PASSWORD`.
El nombre de la base **no** esta hardcodeado en ningun script; sale de `POSTGRES_DB`.

## Aplicar las migraciones

### Opcion A - Flyway CLI

```bash
flyway \
  -url="jdbc:postgresql://$POSTGRES_HOST:$POSTGRES_PORT/$POSTGRES_DB" \
  -user="$POSTGRES_USER" \
  -password="$POSTGRES_PASSWORD" \
  -locations="filesystem:db/migrations" \
  migrate
```

### Opcion B - Spring Boot (backend)

El backend incluye Flyway; al arrancar aplica `db/migrations` automaticamente contra la base configurada.

### Opcion C - psql directo (sin Flyway, util para depurar)

```bash
for f in db/migrations/V1__init.sql db/migrations/V2__rls.sql db/migrations/V3__functions.sql db/migrations/V4__triggers.sql; do
  psql "postgresql://$POSTGRES_USER:$POSTGRES_PASSWORD@$POSTGRES_HOST:$POSTGRES_PORT/$POSTGRES_DB" -v ON_ERROR_STOP=1 -f "$f"
done
```

## Cargar el corpus (`seed.json`)

Desde la **raiz del repo**, con el superusuario bootstrap:

```bash
psql "postgresql://$POSTGRES_USER:$POSTGRES_PASSWORD@$POSTGRES_HOST:$POSTGRES_PORT/$POSTGRES_DB" \
  -v ON_ERROR_STOP=1 -f db/seed_loader.sql
```

Ruta alternativa del archivo:

```bash
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -v seed_path=/ruta/a/seed.json -f db/seed_loader.sql
```

Notas:

- `seed_loader.sql` es **idempotente**: hace `TRUNCATE ... CASCADE` y recarga.
- Genera **embeddings sinteticos** (aleatorios) para que la Consulta 3 sea probable sin llamar al proveedor de IA. El backend los sobrescribe con embeddings reales (Gemini `gemini-embedding-001`, recortados a 1536 dimensiones) al indexar los mensajes.
- Todas las contrasenas del corpus son `Password123!` (hash bcrypt cost 10).
- Tambien funciona ejecutado como `riwi_app`: la funcion fija el actor por fila (`rw_set_current_user`) para cumplir las politicas RLS de insercion.

## Las 4 consultas requeridas (`db/queries/`)

| Archivo | Consulta | Puntos clave |
|---|---|---|
| `01_channel_history_keyset.sql` | Historial de un canal | Keyset sobre `(created_at DESC, id DESC)`, sin `OFFSET`; excluye soft delete; RLS filtra por membresia |
| `02_message_search_headline.sql` | Busqueda con resaltado | `websearch_to_tsquery` + `ts_headline` (`<mark>`); keyset por `(rank, id)`; RLS impide filtrar canales ajenos |
| `03_copilot_context_retrieval.sql` | Contexto RAG del copiloto | Filtro de permisos **en SQL** (`EXISTS` contra `rw_channel_member` con `:actor_id`) + RLS; orden por distancia coseno (indice HNSW) |
| `04_copilot_usage_per_user.sql` | Consumo del copiloto por usuario | Agrega `total_tokens` (columna generada); rango de fechas opcional parametrizado |

Los parametros van como placeholders `:nombre` (bind del backend via `JdbcTemplate` con `NamedParameterJdbcTemplate`). Para probarlos con `psql`, define las variables con `-v` y envuelve en `BEGIN; SELECT rw_set_current_user('<uuid>'); \i archivo.sql; COMMIT;` (el actor RLS debe vivir dentro de la misma transaccion).

## Verificacion rapida con Docker

```bash
docker run -d --name riwi_db -e POSTGRES_USER=riwi_root -e POSTGRES_PASSWORD=root \
  -e POSTGRES_DB=riwi -p 5432:5432 pgvector/pgvector:pg16
# aplicar migraciones (opcion C) + cargar seed, luego:
psql "postgresql://riwi_app:riwi_app@localhost:5432/riwi" -c \
  "BEGIN; SELECT rw_set_current_user('11111111-1111-1111-1111-000000000007');
   SELECT count(*) FROM rw_message WHERE channel_id='22222222-2222-2222-2222-000000000003'; COMMIT;"
# -> 0 filas: Diego (contratista) no es miembro de product-planning
```

## Objetos principales

- **Vista** `rw_user_conversation` (`security_invoker`): conversaciones del actor con ultimo mensaje y `unread_count`.
- **Funciones transaccionales**: `rw_post_message`, `rw_edit_message`, `rw_soft_delete_message`, `rw_mark_channel_read` (validan permisos en la BD; una excepcion revierte todo).
- **Stored procedures de usuarios**: `rw_search_users(...)` (consulta con keyset) y `rw_update_user(...)` / `rw_delete_user(...)` (edicion y soft delete conservando estados originales).
- **Helpers RLS**: `rw_current_user_id()`, `rw_set_current_user()`, `rw_is_channel_member()`, `rw_is_channel_admin()`, `rw_is_platform_admin()`.
- **Triggers**: `trg_rw_message_search_sync` (mantiene `search_tsv`, invalida `embedding` al editar), `trg_rw_message_block_hard_delete`, `trg_rw_*_touch` (`updated_at`).
