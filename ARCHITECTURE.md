# ARCHITECTURE.md

Arquitectura de la plataforma de mensajería Riwi Co.

---

## 1. Contenedores (`docker compose`)

```
                       navegador
                          |
                          v
              +-----------------------+
              |  frontend (nginx)     |  :FRONTEND_PORT -> :80
              |  Angular SPA estatica |  try_files -> /index.html
              +-----------+-----------+
                          | HTTP (API_BASE_URL, build-time)
                          v
              +-----------------------+        +------------------------+
              |  backend (Spring)     |  ----> |  Groq  (ChatPort)      |
              |  :SERVER_PORT         |  ----> |  OpenAI/Gemini (Embed) |
              |  se conecta como      |        +------------------------+
              |  riwi_app (RLS ON)    |
              +-----------+-----------+
                          | JDBC
                          v
              +-----------------------+
              |  db (pgvector:pg16)   |  volumen db_data
              |  RLS + funciones + SP |
              +-----------+-----------+
                          ^
                          | Flyway migrate + ALTER ROLE + seed
              +-----------------------+
              |  migrator (one-shot)  |  corre como riwi_root, luego termina
              +-----------------------+
```

Orden de arranque: `db` (healthy) → `migrator` (completed successfully) → `backend` → `frontend`.

---

## 2. Clean Architecture — 4 capas y regla de dependencias

`backend/src/main/java/com/riwi/messaging/`

```
interfaces  ->  application  ->  domain  <-  infrastructure
```

Las flechas son "depende de". **Todo apunta hacia `domain`**; `domain` no depende de nadie.

| Capa | Contenido | Reglas |
|---|---|---|
| `domain` | entidades / value objects (`records`), puertos (`UserRepository`, `ChatPort`, `EmbeddingPort`, `CopilotContextRepository`, ...), excepciones de negocio | cero imports de Spring, JDBC o cualquier driver/SDK |
| `application` | casos de uso delgados (`*UseCase`), comandos (`records`), `CopilotPromptBuilder` | validan entrada, invocan puertos, mapean; **sin SQL**, sin HTTP |
| `infrastructure` | adaptadores: `Jdbc*Repository` (`JdbcTemplate`, SQL parametrizado), `GroqChatAdapter` / `OpenAiEmbeddingAdapter` (`RestClient`), JWT, WebSocket, `TransactionActorAspect`, `@ConfigurationProperties` | implementa los puertos del dominio; único lugar con SQL/HTTP |
| `interfaces` | controllers REST, DTOs (`records`), filtros (`CorrelationIdFilter`), `GlobalExceptionHandler` | traduce HTTP ↔ comandos/resultados; nunca pasa tipos de framework hacia adentro |

Patrones (justificables en la sustentación): **Ports & Adapters**, **Repository**, **Adapter** (envuelven jjwt / RestClient / BCrypt), **Strategy vía puertos** (proveedor de IA intercambiable), **Command** (entrada de cada caso de uso), **Aspect** (actor de RLS por transacción). Detalle en `DECISIONS.md` D6–D10.

---

## 3. Flujo del actor autenticado (JWT → RLS)

```
Authorization: Bearer <access token>
        |
        v
JwtAuthenticationFilter  --- verifica firma HS256, extrae claims
        |
        v
SecurityContext  <- principal = TokenClaims(userId, platformAdmin, name, jobTitle)
        |
        v
@Transactional en un *UseCase  --> abre transaccion
        |
        v
TransactionActorAspect (@Before, @Order(50), dentro de la tx)
        |  SELECT rw_set_current_user(<userId>)   -- equivale a SET LOCAL app.current_user_id
        v
Postgres: rw_current_user_id() alimenta las politicas RLS
        |
        v
p_rw_channel_select / p_rw_message_select / ...  filtran por membresia del actor
```

- El `userId` sale **exclusivamente** del claim `sub` del JWT, nunca del body.
- El `SET LOCAL` se hace una sola vez por transacción, en el aspecto — nunca a mano en cada caso de uso.
- El rol `riwi_app` es `NOSUPERUSER NOBYPASSRLS`: no puede saltarse las políticas.

---

## 4. Doble filtro del copiloto RAG

La pregunta pesa como "no confiable"; el contexto se restringe **dos veces**:

1. **En SQL (Consulta 3, `db/queries/03_copilot_context_retrieval.sql`)** — el `SELECT` de recuperación
   vectorial incluye `EXISTS (SELECT 1 FROM rw_channel_member WHERE channel_id = m.channel_id AND user_id = :actor_id)`.
   El permiso está escrito en la consulta, no delegado al código Java.
2. **Por RLS** — la misma transacción tiene el actor fijado, así que `p_rw_message_select` vuelve a
   filtrar `rw_message` por membresía. Un mensaje de un canal ajeno no se recupera aunque su embedding
   sea el más cercano (test `retrievalRespectsMembershipEvenWhenForeignEmbeddingIsEquallyClose`).

Negativa honesta con dos motivos:

- recuperación filtrada vacía + `rw_copilot_context_exists_elsewhere` = `true` → `refused_permission`
- recuperación filtrada vacía + no existe en ningún canal → `refused_no_context`

El contexto recuperado se inyecta en el turno *user* dentro de `<contexto_no_confiable>...</contexto_no_confiable>`,
cada fragmento rotulado `[msg:<id>]`. El system prompt está versionado (`CopilotPromptBuilder.VERSION`,
persistido en `rw_copilot_query.system_prompt_version`) e incluye nombre y cargo del actor construidos
en el servidor desde `rw_user_profile`.

---

## 5. Modelo de datos (resumen)

Tablas `rw_*` (todo en inglés, `timestamptz` en UTC, soft delete, sin `DELETE` físico de mensajes):

- `rw_user` 1–1 `rw_user_profile`
- `rw_channel` 1–N `rw_channel_member` N–1 `rw_user`  (tabla puente de membresía)
- `rw_channel` 1–N `rw_message` (soft delete, `status`, `embedding vector(1536)`, `search_tsv tsvector`)
- `rw_message` 1–N `rw_message_read_receipt`
- `rw_copilot_query` 1–N `rw_copilot_citation` → `rw_message`
- `rw_refresh_token` (hash SHA-256, rotación + `replaced_by`)

Objetos de lógica en BD: vista `rw_user_conversation`; funciones transaccionales
`rw_post_message` / `rw_edit_message` / `rw_soft_delete_message` / `rw_mark_channel_read`;
stored procedures de usuarios `rw_search_users` (keyset) y `rw_update_user` / `rw_delete_user`;
triggers `trg_rw_message_search_sync` (mantiene `search_tsv`, invalida `embedding` al editar),
`trg_rw_message_block_hard_delete`, `*_touch` (`updated_at`).

Proceso de normalización 1FN → 2FN → 3FN: `db/NORMALIZACION.md`.

---

## 6. Puntos de extensión

| Quiero cambiar... | Toco... | No toco... |
|---|---|---|
| proveedor de chat (Groq → otro) | `AI_CHAT_BASE_URL/API_KEY/MODEL` en `.env` | dominio, casos de uso |
| proveedor de embeddings (OpenAI → Gemini) | `AI_EMBEDDING_BASE_URL/API_KEY/MODEL/DIMENSIONS` | dominio, `ChatPort`/`EmbeddingPort` |
| acceso a datos (`JdbcTemplate` → jOOQ) | nuevos adaptadores en `infrastructure` | puertos del dominio |
| broadcast en tiempo real (memoria → Redis pub/sub) | adaptador de `MessageBroadcastPort` | `PostMessageUseCase` |
| dimensión del vector | `AI_EMBEDDING_DIMENSIONS` + `vector(N)` en una migración nueva | resto del backend |
