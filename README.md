# Riwi Co. — Plataforma de mensajería

Mensajería interna con búsqueda, tiempo real y un copiloto de IA (RAG) que solo responde
con información que el usuario autenticado tiene permitido ver.

- **Base de datos**: PostgreSQL 15+ con `pgvector` (relacional + vectorial en un solo motor). Migraciones Flyway, RLS sobre canales y mensajes.
- **Backend**: Java 21 + Spring Boot 3, Clean Architecture, acceso con `JdbcTemplate` (nunca JPA), JWT con refresh rotativo.
- **Frontend**: Angular 18 standalone + Tailwind + ngx-translate (ES/EN), servido por nginx.
- **IA**: Groq (chat del copiloto, formato OpenAI) + un proveedor de embeddings aparte (OpenAI / Gemini / etc.), ambos detrás de puertos del dominio e intercambiables por `.env`.

---

## 1. Requisitos previos

- Docker + Docker Compose v2 (`docker compose version`).
- Dos API keys para que el copiloto responda de verdad:
  - `AI_CHAT_API_KEY` — Groq (`https://console.groq.com`).
  - `AI_EMBEDDING_API_KEY` — proveedor de embeddings (OpenAI por defecto; también sirve Gemini vía su endpoint compatible con OpenAI).
- Sin las keys el sistema levanta igual: mensajería, búsqueda y auth funcionan; el copiloto responde `refused_no_context` (no hay embeddings reales).

---

## 2. Levantar el proyecto en limpio

```bash
git clone <repo> && cd Emple-assesment
cp .env.example .env
# editar .env: POSTGRES_PASSWORD, DB_APP_PASSWORD, JWT_SECRET (>=32 chars),
#              AI_CHAT_API_KEY, AI_EMBEDDING_API_KEY
docker compose up --build
```

Qué hace `docker compose up`:

1. **db** — Postgres con pgvector, volumen nombrado `db_data`, healthcheck `pg_isready`.
2. **migrator** (one-shot) — corre Flyway `V1..V6` como superusuario `riwi_root`, fija la password real del rol de aplicación `riwi_app` y carga `db/seed.json` (solo si la base está vacía).
3. **backend** — espera a que `db` esté *healthy* y `migrator` termine con éxito; se conecta como `riwi_app` (sujeto a RLS). Al arrancar re-embebe todo el corpus del seed con el proveedor real (`EMBEDDINGS_BACKFILL_ON_STARTUP=true`, `EMBEDDINGS_BACKFILL_MODE=all`).
4. **frontend** — build de Angular servido por nginx con fallback SPA.

### URLs

| Servicio | URL | Puerto (`.env`) |
|---|---|---|
| Frontend | http://localhost:4200 | `FRONTEND_PORT` |
| Backend REST | http://localhost:8080 | `SERVER_PORT` |
| Swagger UI | http://localhost:8080/swagger-ui.html | |
| Health | http://localhost:8080/health | |
| Postgres | localhost:5432 | `POSTGRES_PORT` |

---

## 3. Migraciones y seed a mano

El servicio `migrator` también se puede invocar solo:

```bash
# migraciones + seed solo si la base está vacía (igual que el arranque automático)
docker compose run --rm migrator
scripts/db.sh migrate

# migraciones + forzar recarga del corpus (TRUNCATE + reload de seed.json)
docker compose run --rm -e SEED_FORCE=true migrator
scripts/db.sh seed
```

Detalle de la capa de datos (roles, RLS, las 4 consultas): `db/README.md`.

### Re-embedear el corpus manualmente (necesita API key de embeddings)

```bash
# como admin de plataforma (juan.olarte@riwi.io)
TOKEN=$(curl -s localhost:8080/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"juan.olarte@riwi.io","password":"Password123!"}' | jq -r .accessToken)

curl -s -X POST "localhost:8080/internal/embeddings/backfill?mode=all" -H "Authorization: Bearer $TOKEN"
```

### Verificar que el copiloto está cableado

```bash
curl -s localhost:8080/internal/copilot/status -H "Authorization: Bearer $TOKEN" | jq
# { totalMessages, messagesWithEmbedding, embeddingModel, chatModel,
#   embeddingProviderReachable, chatProviderReachable }
```

---

## 4. Tests

```bash
# suite completa contra Postgres real (Testcontainers). CopilotLiveIT se salta sin API keys.
mvn -f backend/pom.xml test
```

Incluye los 2 tests obligatorios: `NonMemberRejectionIT` (rechazo a usuario no miembro) y
`PrivateChannelLeakIT` (no filtración de mensajes de canales privados ajenos).

### Smoke opt-in contra los proveedores reales

```bash
# exportar AI_CHAT_API_KEY, AI_EMBEDDING_API_KEY (y opcionalmente AI_EMBEDDING_BASE_URL/MODEL/DIMENSIONS)
mvn -f backend/pom.xml test -Dtest=CopilotLiveIT
```

`CopilotLiveIT` verifica: dimensión real del embedding == `AI_EMBEDDING_DIMENSIONS`, chat real con
`usage` de tokens > 0, y un end-to-end (seed → backfill `all` real → `POST /copilot/query`) que
responde `answered` con citas para una pregunta del corpus y `refused_no_context` para una fuera de contexto.

---

## 5. Runbook de verificación del demo

Todos los usuarios del seed tienen la contraseña **`Password123!`**.

| Email | Nombre | Cargo | Admin | Canales |
|---|---|---|---|---|
| `juan.olarte@riwi.io` | Juan Olarte | CTO | sí | general, engineering, hr-confidential, random, contractor-onboarding |
| `camila.restrepo@riwi.io` | Camila Restrepo | Product Manager | no | general, product-planning, random |
| `andres.gomez@riwi.io` | Andres Gomez | Backend Engineer | no | general, engineering, product-planning, random |
| `valentina.ruiz@riwi.io` | Valentina Ruiz | Frontend Engineer | no | general, engineering, product-planning, random |
| `sebastian.marin@riwi.io` | Sebastian Marin | Data Analyst | no | general, engineering, random |
| `laura.cardona@riwi.io` | Laura Cardona | HR Business Partner | no | general, hr-confidential, random |
| `diego.torres@contractor.io` | Diego Torres | External Contractor | no | random, contractor-onboarding |
| `mariana.lopez@riwi.io` | Mariana Lopez | QA Engineer | no | engineering |

Pasos (≤ 5 min):

1. **Registro / login** — registrar un usuario nuevo en `/register` (auto-login), o entrar como `andres.gomez@riwi.io`.
2. **Enviar un mensaje** — en `engineering`, escribir un mensaje: pasa por `pendiente → enviado`; abrir otra sesión (p. ej. `valentina.ruiz@riwi.io`) y ver que llega en tiempo real por WebSocket.
3. **Búsqueda con resaltado** — buscar `keyset` o `presupuesto`: los resultados muestran el término resaltado (`<mark>`), ordenados por relevancia.
4. **Copiloto con citas** — como `andres.gomez@riwi.io` preguntar *"¿cuál es el presupuesto de Q3 para infraestructura?"* → responde con el dato y una cita al mensaje fuente de `product-planning`.
5. **Copiloto con negativa por permiso** — como `diego.torres@contractor.io` (no está en `product-planning`) preguntar lo mismo → `refused_permission`: *"esa información pertenece a canales a los que no tienes acceso"*. El filtro vive en la Consulta 3 (SQL) + RLS, no solo en Java.

---

## 6. Frontend / `API_BASE_URL`

`frontend/src/app/shared/config/api.config.ts` expone `API_BASE_URL` como **constante de build-time**.
El `frontend/Dockerfile` la resuelve con un `sed` sobre una copia del código dentro del contenedor de build
(el repo no se toca), tomando el valor del build-arg `API_BASE_URL` (default `http://localhost:8080`).
`docker-compose.yml` pasa `API_BASE_URL` desde `.env`; debe apuntar al puerto público del backend (`SERVER_PORT`).
Se eligió build-arg + `sed` por ser lo más simple y no requerir cambios en `frontend/src/`.

---

## 7. Documentación

- `ARCHITECTURE.md` — contenedores, 4 capas y regla de dependencias, flujo del actor JWT → RLS, doble filtro del RAG.
- `DECISIONS.md` — decisiones técnicas D1–D10.
- `db/README.md`, `db/NORMALIZACION.md` — capa de datos y normalización 1FN→3FN.
- `docs/openapi.yaml` — contrato OpenAPI 3 versionado, regenerado por `OpenApiContractIT`.

---

## 8. Documentación de la API (OpenAPI / Swagger)

El backend publica su propio contrato OpenAPI 3 con springdoc; no hay que exportar nada a mano.

| Recurso | URL (backend corriendo) |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Contrato JSON | http://localhost:8080/v3/api-docs |
| Contrato YAML | http://localhost:8080/v3/api-docs.yaml |

El contrato versionado en el repo vive en `docs/openapi.yaml`.

Todas las operaciones exigen el esquema `bearer-jwt` (HTTP Bearer, formato JWT) salvo `/auth/**`
y `/health`, que son públicas. En Swagger UI el access token se pega con el botón **Authorize**.

### Regenerar `docs/openapi.yaml`

`OpenApiContractIT` arranca el contexto real, pide `/v3/api-docs.yaml` y sobrescribe el archivo:

```bash
mvn -f backend/pom.xml test -Dtest=OpenApiContractIT
```

Requiere Docker (Testcontainers levanta Postgres con pgvector).
