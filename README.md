# Riwi Co. — Internal Messaging Platform

## 1. Overview

Riwi Co. is an internal messaging platform: organized channels, real-time delivery, full-text
search with highlighting, and an AI copilot (RAG) that only answers with information the
authenticated user is allowed to see. Every access rule is enforced in the database
(PostgreSQL Row Level Security) — not just in application code.

Stack:

| Layer | Technology |
|---|---|
| Database | PostgreSQL 16 + `pgvector` (relational + vector store in one engine), Flyway migrations, RLS on channels and messages |
| Backend | Java 21 / Spring Boot 3.3, Clean Architecture (4 layers), `JdbcTemplate` (never JPA), JWT access + rotating refresh |
| Frontend | Angular 18 standalone + Tailwind + ngx-translate (ES/EN), served by nginx |
| AI | Groq for copilot chat (OpenAI-compatible), Google Gemini for embeddings — both behind domain ports, swappable via `.env` |

See [`ARCHITECTURE.md`](ARCHITECTURE.md) for the container/layer diagram and the JWT -> RLS flow,
and [`DECISIONS.md`](DECISIONS.md) for every technical decision and scope cut.

---

## 2. Prerequisites

Only Docker is required — everything (backend build, frontend build, migrations) runs in containers.

| Tool | Minimum version | Check |
|---|---|---|
| Docker Engine | 24 | `docker --version` |
| Docker Compose | v2.20 | `docker compose version` |

No local JDK, Maven, Node or PostgreSQL is needed to run the platform. They are only needed
to run the test suites (section 9).

---

## 3. Quick start (fresh machine)

```bash
# 1. clone and enter the repo
git clone <repo-url> Emple-assesment
cd Emple-assesment

# 2. create your env file from the template
cp .env.example .env

# 3. edit .env (see the table below), then bring everything up
docker compose up --build
```

### 3.1 What to edit in `.env`

| Variable | What to put | How to get it |
|---|---|---|
| `POSTGRES_PASSWORD` | any strong string | your choice |
| `DB_APP_PASSWORD` | any strong string (password of the RLS-bound `riwi_app` role) | your choice |
| `JWT_SECRET` | at least 32 characters | `openssl rand -base64 32` |
| `AI_CHAT_API_KEY` | Groq API key (copilot chat) | https://console.groq.com/keys — free, no credit card |
| `AI_EMBEDDING_API_KEY` | Google AI Studio API key (embeddings) | https://aistudio.google.com/apikey — free, no credit card |

The platform still boots without the two AI keys: messaging, search and auth work; the copilot
just answers `refused_no_context` because there are no real embeddings.

### 3.2 Port conflicts

Defaults: PostgreSQL `5432`, backend `8080`, frontend `4200`. If any is taken, remap in `.env`:

| Variable | Default | Example remap |
|---|---|---|
| `POSTGRES_PORT` | `5432` | `55432` |
| `SERVER_PORT` | `8080` | `18080` |
| `FRONTEND_PORT` | `4200` | `14200` |
| `API_BASE_URL` | `http://localhost:8080` | `http://localhost:18080` |

`API_BASE_URL` **must match `SERVER_PORT`** — it is the URL the browser uses to reach the backend.
It is baked into the frontend at **build time**, so after changing it you must rebuild:
`docker compose up --build`.

### 3.3 What to expect on `docker compose up --build`

1. **db** — Postgres + pgvector starts, `pg_isready` healthcheck turns healthy.
2. **migrator** (one-shot) — runs Flyway `V1`–`V6` as the bootstrap superuser, sets the real
   password of the `riwi_app` role, then loads `db/seed.json` (only if the database is empty).
3. **backend** — waits for `db` healthy + `migrator` success, connects as `riwi_app` (subject to
   RLS). On startup it **re-embeds the whole seed corpus with the real provider**
   (`EMBEDDINGS_BACKFILL_ON_STARTUP=true`, `EMBEDDINGS_BACKFILL_MODE=all`, ~1 min, needs the AI keys).
4. **frontend** — Angular build served by nginx with SPA fallback.

---

## 4. URLs

With the defaults, and with the remap example from 3.2 (`55432 / 18080 / 14200`):

| Resource | Default | Remapped example |
|---|---|---|
| Frontend | http://localhost:4200 | http://localhost:14200 |
| Backend health | http://localhost:8080/health | http://localhost:18080/health |
| Swagger UI | http://localhost:8080/swagger-ui.html | http://localhost:18080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs | http://localhost:18080/v3/api-docs |
| OpenAPI YAML | http://localhost:8080/v3/api-docs.yaml | http://localhost:18080/v3/api-docs.yaml |
| PostgreSQL | localhost:5432 | localhost:55432 |

The versioned copy of the contract lives in [`docs/openapi.yaml`](docs/openapi.yaml).

---

## 5. Test users

All seed users share the password **`Password123!`**.

| Email | Name | Job title | Platform admin | Channels |
|---|---|---|---|---|
| `juan.olarte@riwi.io` | Juan Olarte | CTO | **yes (admin)** | general, engineering, hr-confidential, random, contractor-onboarding |
| `camila.restrepo@riwi.io` | Camila Restrepo | Product Manager | no | general, product-planning, random |
| `andres.gomez@riwi.io` | Andres Gomez | Backend Engineer | no | general, engineering, product-planning, random |
| `valentina.ruiz@riwi.io` | Valentina Ruiz | Frontend Engineer | no | general, engineering, product-planning, random |
| `sebastian.marin@riwi.io` | Sebastian Marin | Data Analyst | no | general, engineering, random |
| `laura.cardona@riwi.io` | Laura Cardona | HR Business Partner | no | general, hr-confidential, random |
| `diego.torres@contractor.io` | Diego Torres | External Contractor | no | **random, contractor-onboarding only (limited access)** |
| `mariana.lopez@riwi.io` | Mariana Lopez | QA Engineer | no | engineering |

`juan.olarte@riwi.io` is the platform admin (user management, internal endpoints).
`diego.torres@contractor.io` is the limited-access contractor used to demo the copilot permission refusal.

---

## 6. API reference

Base URL: `http://localhost:${SERVER_PORT}` (default `8080`). All responses carry an
`X-Correlation-Id` header (echoed from the request or generated). Errors use a uniform JSON body
(`ErrorResponse`: `code`, `message`, `correlationId`).

Auth column: **public** = no token · **bearer** = valid access token · **admin** = access token
whose `is_platform_admin` claim is `true` (returns `403` otherwise).

### Auth — `/auth`

| Method / path | Description | Auth |
|---|---|---|
| `POST /auth/login` | Email + password -> access + refresh token pair | public |
| `POST /auth/register` | Create account, returns token pair (auto-login) | public |
| `POST /auth/refresh` | Rotate the refresh token, returns a new pair | public (refresh in body) |
| `POST /auth/logout` | Revoke the presented refresh token (idempotent) | public (refresh in body) |

### Profile — `/me`

| Method / path | Description | Auth |
|---|---|---|
| `GET /me` | Authenticated user profile + count of visible conversations | bearer |

### Channels — `/channels`

| Method / path | Description | Auth |
|---|---|---|
| `GET /channels` | List the actor's conversations (keyset) | bearer |
| `POST /channels` | Create a channel; actor becomes owner | bearer |
| `POST /channels/{channelId}/members` | Add a member (channel owner/admin only) | bearer |
| `GET /channels/{channelId}/messages` | Channel history, keyset pagination (Query 1); RLS excludes foreign channels | bearer |
| `POST /channels/{channelId}/messages` | Post a message, broadcast over WebSocket after commit | bearer |
| `POST /channels/{channelId}/read` | Mark other people's live messages in the channel as read | bearer |

### Messages — `/messages`

| Method / path | Description | Auth |
|---|---|---|
| `GET /messages/search` | Full-text search with `<mark>` highlighting (Query 2); RLS limits hits to the actor's channels | bearer |
| `PATCH /messages/{messageId}` | Edit a message (author only) | bearer |
| `DELETE /messages/{messageId}` | Soft delete a message (never physical) | bearer |

### Search

See `GET /messages/search` above. Params: `q`, optional `channelId`, `cursor`, `size`.

### Copilot — `/copilot`

| Method / path | Description | Auth |
|---|---|---|
| `POST /copilot/query` | Ask the copilot; only uses context from channels where the actor is a member. Returns `answer`, `status` (`answered` / `refused_no_context` / `refused_permission`), `citations[]`, `usage` | bearer |
| `GET /copilot/usage` | Cumulative token usage (Query 4). Actor sees own; admin can break down by `?userId=`. Optional `?from=&to=` (ISO-8601) | bearer (admin for breakdown) |
| `GET /copilot/history` | The actor's own query history, keyset | bearer |

### Users — `/users`

| Method / path | Description | Auth |
|---|---|---|
| `GET /users` | User directory, keyset pagination. Backed by the `rw_search_users` stored procedure, which restricts rows/columns for non-admins. Params: `q`, `cursor`, `size`, `includeInactive` | bearer (fields limited unless admin) |
| `PATCH /users/{id}` | Edit a user (`rw_update_user` SP: self or admin; `isActive` admin-only) | bearer (self or admin) |
| `DELETE /users/{id}` | Soft delete a user (`rw_delete_user` SP), revokes refresh tokens, keeps messages | admin |

### Internal — `/internal`

| Method / path | Description | Auth |
|---|---|---|
| `POST /internal/embeddings/backfill?mode=missing\|all` | Batch-generate message embeddings | admin |
| `GET /internal/copilot/status` | Copilot readiness: `totalMessages`, `messagesWithEmbedding`, `embeddingModel`, `chatModel`, `embeddingProviderReachable`, `chatProviderReachable` | admin |

### Infra

| Method / path | Description | Auth |
|---|---|---|
| `GET /health` | Liveness (`{"status":"UP"}`) | public |

### Pagination

Every list endpoint uses **keyset pagination** — never `OFFSET`. Pass `?cursor=<opaque>&size=<n>`.
The `cursor` is an opaque base64 token returned as `nextCursor` in the previous page; omit it for
the first page. When `nextCursor` is `null` there are no more pages.

### WebSocket

Real-time message events: `ws://<host>:<SERVER_PORT>/ws/messages?access_token=<jwt>`
(browsers cannot set headers on a WebSocket handshake, so the access token travels as a query
param and is verified during the handshake). Frames are JSON:
`{ "type": "message.created", "message": { ...MessageView } }`. Only members of the target
channel receive the event.

### curl examples

```bash
BASE=http://localhost:8080

# --- login (public) ---
TOKEN=$(curl -s $BASE/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"andres.gomez@riwi.io","password":"Password123!"}' | jq -r .accessToken)

# --- list conversations, grab a channel id ---
CH=$(curl -s $BASE/channels -H "Authorization: Bearer $TOKEN" \
  | jq -r '.items[] | select(.channelName=="engineering") | .channelId')

# --- send a message ---
curl -s -X POST "$BASE/channels/$CH/messages" -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"body":"Deploying the keyset pagination fix now","clientNonce":"'"$(uuidgen)"'"}'

# --- search with highlighting ---
curl -s "$BASE/messages/search?q=keyset&size=10" -H "Authorization: Bearer $TOKEN" | jq '.items[].snippet'

# --- ask the copilot ---
curl -s -X POST "$BASE/copilot/query" -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"question":"What is the Q3 infrastructure budget?"}' | jq '{answer,status,citations}'

# --- list users as the platform admin ---
ADMIN=$(curl -s $BASE/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"juan.olarte@riwi.io","password":"Password123!"}' | jq -r .accessToken)
curl -s "$BASE/users?size=20" -H "Authorization: Bearer $ADMIN" | jq '.items'
```

---

## 7. Demo runbook (<= 5 min)

1. **Register / login** — register a new user at `/register` (auto-login), or log in as
   `andres.gomez@riwi.io` / `Password123!`.
2. **Send a message (real time)** — open two browser sessions (e.g. `andres.gomez@riwi.io` and
   `valentina.ruiz@riwi.io`), both in the `engineering` channel. Send from one: it goes
   `pending -> sent`, and appears in the other session instantly over WebSocket.
3. **Search with highlighting** — search `keyset` or `presupuesto`: results show the term wrapped
   in `<mark>`, ordered by relevance.
4. **Copilot with a citation** — as `andres.gomez@riwi.io` ask
   *"What is the Q3 infrastructure budget?"* -> `answered` with the figure and a citation to the
   source message in `product-planning`.
5. **Copilot permission refusal** — as `diego.torres@contractor.io` (not a member of
   `product-planning`) ask the same question -> `refused_permission`. The filter lives in Query 3
   (SQL) plus RLS, not only in Java.
6. **User management as admin** — log in as `juan.olarte@riwi.io`, open `/admin/users`: list,
   edit and soft-delete users (backed by the DB stored procedures).

---

## 8. Database migrations & seed (manual)

The `migrator` service can be run on its own:

```bash
# migrate + load seed only if the DB is empty (same as `docker compose up`)
scripts/db.sh migrate
# equivalent to: docker compose run --rm migrator

# migrate + force a full reload of the corpus
scripts/db.sh seed
# equivalent to: docker compose run --rm -e SEED_FORCE=true migrator
```

Re-embed the corpus by hand (needs `AI_EMBEDDING_API_KEY`), as the platform admin:

```bash
BASE=http://localhost:8080
TOKEN=$(curl -s $BASE/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"juan.olarte@riwi.io","password":"Password123!"}' | jq -r .accessToken)

curl -s -X POST "$BASE/internal/embeddings/backfill?mode=all" -H "Authorization: Bearer $TOKEN" | jq
```

Data-layer details (roles, RLS policies, the 4 required queries, triggers, views, SPs):
[`db/README.md`](db/README.md). Normalization 1NF -> 3NF: [`db/NORMALIZACION.md`](db/NORMALIZACION.md).

---

## 9. Tests

### Backend (requires Docker — Testcontainers spins up real Postgres + pgvector)

```bash
mvn -f backend/pom.xml test
```

58 tests, including the 2 mandatory RLS tests:
`NonMemberRejectionIT` (non-member is rejected) and
`PrivateChannelLeakIT` (no leakage of foreign private-channel messages).

`CopilotLiveIT` is opt-in and skipped without keys. To run it against the real providers:

```bash
export AI_CHAT_API_KEY=...            # Groq
export AI_EMBEDDING_API_KEY=...       # Gemini
mvn -f backend/pom.xml test -Dtest=CopilotLiveIT
```

`OpenApiContractIT` regenerates and versions `docs/openapi.yaml`:

```bash
mvn -f backend/pom.xml test -Dtest=OpenApiContractIT
```

### Frontend

```bash
cd frontend
npm ci
npm test
```

---

## 10. Project structure

```
Emple-assesment/
├── db/                        PostgreSQL: everything the DB owns
│   ├── migrations/            Flyway V1..V6 (init, RLS, functions, triggers, copilot, copilot-ops)
│   ├── queries/               the 4 required SQL queries (history keyset, search headline,
│   │                          copilot context retrieval, copilot usage per user)
│   ├── seed.json              corpus: users, channels, memberships, messages, receipts, copilot logs
│   ├── seed_loader.sql        idempotent loader used by the migrator
│   ├── Dockerfile             migrator image (Flyway + psql)
│   ├── README.md              roles, RLS, view, stored procedures
│   └── NORMALIZACION.md       1NF -> 3NF walkthrough
├── backend/                   Java 21 / Spring Boot 3 — Clean Architecture
│   └── src/main/java/com/riwi/messaging/
│       ├── domain/            entities, value objects, ports — no Spring, no JDBC
│       ├── application/       thin use cases (auth, messaging, copilot, user)
│       ├── infrastructure/    adapters: persistence (JdbcTemplate), ai (Groq/Gemini), rls,
│       │                      security (JWT), websocket, config
│       └── interfaces/        REST controllers + DTO records
├── frontend/                  Angular 18 standalone (chat, copilot, profile, auth, admin)
├── docs/openapi.yaml          versioned OpenAPI 3 contract (regenerated by OpenApiContractIT)
├── scripts/db.sh              migrate / seed helper around the migrator service
├── docker-compose.yml         db + migrator + backend + frontend
├── .env.example               config template, no secrets
├── ARCHITECTURE.md            containers, 4 layers, JWT -> RLS flow, RAG double filter
├── DECISIONS.md               technical decisions D1..D10 + closing updates
└── PLAN.md                    execution plan
```

---

## 11. Architecture summary

Clean Architecture with dependencies pointing inward to `domain` (which has zero framework or
driver imports). Use cases in `application` are thin: validate input, call a port, map the result —
no SQL. Concrete adapters (JdbcTemplate repositories, JWT, AI HTTP clients, WebSocket) live in
`infrastructure`; REST controllers and DTO `record`s in `interfaces`.

The actor is taken **only** from the JWT (`SecurityContext`), never from a request body. A single
aspect (`TransactionActorAspect`) propagates it to PostgreSQL as `SET LOCAL app.current_user_id`
at the start of every authenticated transaction, so RLS policies filter every channel/message
read and write. The copilot RAG applies permissions **twice**: once in SQL (Query 3 joins channel
membership) and again through RLS — a foreign message is never retrieved even if its embedding is
the closest match. The AI provider is swappable purely by env: `ChatPort` -> Groq,
`EmbeddingPort` -> Gemini, no domain or use-case change.

Full details: [`ARCHITECTURE.md`](ARCHITECTURE.md).

---

## 12. Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Browser console: `Access-Control-Allow-Origin` missing / CORS error, page shows `[vite] connecting` | You opened the dev `ng serve` on `localhost:4200`, not the dockerized frontend | Open the dockerized frontend on `http://localhost:${FRONTEND_PORT}`. If it persists, the backend container was not rebuilt: `docker compose up -d --build backend` |
| Copilot returns `500` with `model ... does not exist` | Groq rotated the model id | Set a current model in `AI_CHAT_MODEL` (`openai/gpt-oss-120b`, `openai/gpt-oss-20b`, `qwen/qwen3.8-27b`; check https://console.groq.com/docs/models for the live list), then `docker compose up -d --force-recreate backend` |
| `docker compose up` fails: port `5432` / `8080` / `4200` already in use | Another service holds the port | Remap `POSTGRES_PORT` / `SERVER_PORT` / `FRONTEND_PORT` in `.env` (and keep `API_BASE_URL` aligned with `SERVER_PORT`), then `docker compose up --build` — see section 3.2 |
| Copilot answers `refused_no_context` to everything | The embedding backfill did not run (missing `AI_EMBEDDING_API_KEY`) | Set `AI_EMBEDDING_API_KEY`, recreate the backend, or run the manual backfill (section 8). Check `GET /internal/copilot/status` as admin: `messagesWithEmbedding` should be > 0 and `embeddingProviderReachable` `true` |

---
