# DECISIONS.md

Registro de decisiones técnicas y recortes de alcance, con su justificación. Se actualiza durante la jornada, no solo al final.

---

## D1. Repositorio único (monorepo)

**Decisión:** un solo repositorio con `db/`, `backend/` y `frontend/` como carpetas independientes.

**Justificación:**
- El despliegue objetivo es `docker compose up` levantando los tres servicios como una unidad; separar en repos obligaría a submódulos o pasos manuales de sincronización sin beneficio real para un solo desarrollador.
- Facilita demostrar que el primer commit no contiene lógica previa a la jornada (una sola línea de tiempo de commits en vez de varias).
- Backend (Maven) y Frontend (npm) tienen build tools distintos, pero eso no requiere repos separados: cada uno mantiene su propio archivo de dependencias dentro de su carpeta.

**Alternativa descartada:** multi-repo (backend / frontend / infra). Se habría justificado con equipos o ciclos de despliegue independientes, que no aplican aquí.

---

## D2. Stack tecnológico

**Decisión:** Java 21 + Spring Boot 3 (backend), Angular 17+ standalone (frontend), PostgreSQL 15 + pgvector (datos).

**Justificación:**
- Java/Spring es el stack de mayor dominio del coder — prioriza velocidad de entrega dentro de las 8h sobre explorar un stack nuevo en el backend, que es donde vive la mayor parte de la calificación (RLS, funciones, SPs, Clean Architecture).
- Java 21 sobre 17: los **Records** simplifican los DTOs (request/response) a una sola línea inmutable, sin getters/constructores manuales — encaja directamente con el requisito de "casos de uso delgados" (menos código repetitivo que mantener y explicar en la sustentación).
- pgvector evita sumar un servicio de base vectorial adicional (Qdrant/Chroma) al `docker-compose`, cumpliendo el requisito de base vectorial con un solo motor de datos.
- Se usa `JdbcTemplate`/`jOOQ` en vez de JPA/Hibernate como capa de acceso principal: la lógica de negocio vive en funciones y SPs de Postgres, y el actor de RLS (`app.current_user_id`) debe fijarse por transacción con `SET LOCAL`, algo que un ORM con persistence-context complica innecesariamente.
- Flyway con SQL plano (no `ddl-auto` de Hibernate) porque el DDL debe quedar escrito y auditable a mano.

**Riesgo aceptado:** Angular es tecnología nueva para el coder.
**Mitigación:** se usan standalone components (sin NgModules), Signals nativos en vez de NgRx, Tailwind en vez de Angular Material, y ngx-translate en vez del i18n nativo — minimizando conceptos nuevos a aprender el mismo día. Ver recorte de alcance en D3.

---

## D3. Recorte de alcance en Frontend (MVP)

**Decisión:** priorizar funcionalidad sobre pulido visual en Angular si el tiempo se agota.

**Justificación:** el coder es nuevo en Angular; el riesgo de tiempo está concentrado ahí, no en el backend/BD. Ante escasez de tiempo, se recorta primero:
1. Animaciones/transiciones.
2. Detalle visual fino (más allá de responsive funcional con Tailwind).
3. STOMP/SockJS (se usa WebSocket plano de Spring en su lugar).

Nunca se recorta: RLS, validación de permisos en BD, soft delete, keyset pagination, ni el alcance de seguridad del copiloto RAG — son los requisitos "no negociables" del enunciado.

---

## D4. Proveedor de IA: Groq (chat) + OpenAI (embeddings)

**Decisión:** el copiloto usa **Groq** para las respuestas de chat (compatible con el formato de API de OpenAI, solo cambia `base_url` y API key) y **OpenAI** (`text-embedding-3-small`) para los embeddings del RAG.

**Justificación:**
- Groq ofrece la inferencia más rápida del mercado sobre modelos abiertos (Llama 3.3 70B / Llama 3.1 8B) con límites gratuitos generosos (hasta 14,400 solicitudes/día en el modelo 8B), ideal para demos en vivo sin preocuparse por rate limits.
- Se verificó en la documentación oficial de Groq (`console.groq.com/docs/models` y `/docs/api-reference`, agosto 2026) que **no exponen un endpoint de embeddings** — su catálogo es deliberadamente solo de chat/inferencia. Por eso los embeddings quedan en un proveedor separado.
- Ambos quedan detrás de dos puertos (`ChatPort`, `EmbeddingPort`) en el dominio, cumpliendo el requisito de "proveedor de IA intercambiable" del punto 8 sin acoplar el dominio a ningún SDK concreto — cambiar cualquiera de los dos proveedores después es solo config (`.env`), no código.

**Placeholder para el día de la jornada:** confirmar si Groq agregó soporte de embeddings antes de implementar (su catálogo cambia rápido); si no, mantener OpenAI como fallback ya decidido acá.

---

## D5. (Placeholder) Recortes durante la ejecución

> Completar en vivo durante la jornada si se decide posponer o simplificar algún requerimiento.

- [ ] Ejemplo: "Se pospuso X por Y, impacto Z."

---

## D6. Patrones de diseño aplicados (backend)

Estado: parcial — cubre el esqueleto + autenticación (D2 bloque 5-6). Se ampliará con mensajería, RAG y WebSocket.

- **Ports & Adapters (Hexagonal / Clean Architecture).** El dominio define interfaces (`UserRepository`, `RefreshTokenRepository`, `ConversationRepository`, `PasswordHasher`, `AccessTokenPort`, `OpaqueTokenGenerator`, `TokenHasher`) sin ninguna dependencia de Spring ni de un driver. `infrastructure` provee los adaptadores concretos (`Jdbc*Repository`, `BCryptPasswordHasher`, `JjwtAccessTokenAdapter`, `Sha256TokenHasher`, `SecureRandomOpaqueTokenGenerator`). Permite testear casos de uso sin infraestructura y cambiar de adaptador (p. ej. jOOQ en vez de JdbcTemplate, o Argon2 en vez de BCrypt) sin tocar dominio ni aplicación. Alternativa descartada: repos JPA anotados en el dominio — acopla el dominio al ORM y a su ciclo de sesión.
- **Repository.** Cada agregado de lectura/escritura se expone como un repositorio con métodos de intención (`findByEmail`, `markRotated`, `revokeAllActiveForUser`) en vez de un DAO genérico; el SQL parametrizado vive solo en el adaptador.
- **Adapter.** `BCryptPasswordHasher` y `JjwtAccessTokenAdapter` adaptan librerías de terceros (spring-security-crypto, jjwt) a los puertos del dominio; el resto del código nunca importa esos tipos.
- **Strategy (implícito, vía puertos).** `AccessTokenPort` / `PasswordHasher` / `TokenHasher` son puntos de sustitución en runtime por configuración; es la misma base que usará `ChatPort`/`EmbeddingPort` para el proveedor de IA intercambiable del req. 8.
- **Command.** La entrada de cada caso de uso es un `record` inmutable (`LoginCommand`, `RefreshCommand`, `LogoutCommand`); los controllers mapean DTO REST → command y nunca pasan entidades del framework web hacia adentro.
- **Aspect / Interceptor (AOP).** `TransactionActorAspect` fija `app.current_user_id` (actor de RLS) al inicio de cada caso de uso transaccional autenticado, sin repetir el `SET LOCAL` en cada método. Ordenado con `@EnableTransactionManagement(order = 0)` + `@Order(50)` para ejecutarse **dentro** de la transacción. Alternativa descartada: llamada manual en cada repositorio/caso de uso — fácil de olvidar y difícil de auditar.
- **Chain of Responsibility (filtros servlet).** `CorrelationIdFilter` (correlación + MDC) → `JwtAuthenticationFilter` (token → `SecurityContext`) → cadena de Spring Security.
- **Rotación de refresh token con detección de reuso.** El refresh se guarda solo como hash SHA-256; en cada uso se revoca el anterior (`revoked_at` + `replaced_by`) y se emite uno nuevo. Presentar un refresh ya revocado revoca toda la cadena del usuario en una transacción `REQUIRES_NEW` (`TokenReuseGuard`), para que la revocación se confirme aunque la petición se rechace.

### Decisiones puntuales de esta entrega
- **Spring Boot 3.3.4** (no 3.3.5): es la versión disponible en el entorno de build; sin impacto funcional.
- **`java.time.Clock` inyectable** en vez de `Instant.now()` directo: hace testeable la expiración de tokens.
- **Access token corto (15 min por defecto)** con claims `sub`, `is_platform_admin`, `name`, `job_title`, `type=access`, HS256. `is_platform_admin` en el token es solo un atajo para cortar rutas admin; la autoridad sigue siendo `rw_is_platform_admin()` en la BD.
- **`spring.flyway.enabled=false` por defecto** en la app: el rol `riwi_app` no puede crear roles, así que las migraciones las corre un superusuario bootstrap (docker-compose / Flyway CLI). En los tests se ejecuta Flyway como el superusuario del contenedor y el backend bajo prueba se conecta como `riwi_app` (sujeto a RLS).
- **Tests de integración `*IT` incluidos en `mvn test`** (config de surefire) para que la verificación sea un solo comando.

---

## D7. Mensajería: API REST + tiempo real

**Decisión:** los casos de uso de mensajería no reimplementan lógica de permisos ni de negocio en Java; delegan en las funciones/vista de la BD (`rw_post_message`, `rw_edit_message`, `rw_soft_delete_message`, `rw_mark_channel_read`, `rw_user_conversation`) y en RLS.

**Puntos concretos de esta entrega:**
- **Keyset pagination en los dos listados.** Historial de canal: cursor `(created_at, id)` descendente sobre el índice parcial `ix_rw_message_channel_keyset` (Consulta 1). Conversaciones: cursor `(COALESCE(last_message_at,'epoch'), channel_id)` descendente — el centinela `'epoch'` evita el caso `NULL` en la comparación de tuplas sin recurrir a `NULLS LAST`. Nunca se usa `OFFSET`; se pide una fila extra (`limit + 1`) para saber si hay página siguiente. El cursor viaja al cliente como token opaco base64 (`<instant>|<uuid>`), decodificado en la capa `interfaces` (`CursorCodec`).
- **`POST /channels` genera el `id` del canal en la aplicación** (no `RETURNING` en el `INSERT`): Postgres aplica la política RLS de `SELECT` a la cláusula `RETURNING`, y el creador todavía no es miembro (canal potencialmente privado), así que `RETURNING` fallaría con `42501`. Se inserta el canal, luego la membresía `owner`, y recién entonces se hace el `SELECT` (ya visible por RLS).
- **`GET /channels/{id}/messages` de un no-miembro responde 403, no 200 vacío.** Se añade un chequeo explícito `SELECT rw_is_channel_member(:id)` en el adaptador antes de la query de historial. La RLS por sí sola devolvería 0 filas (también correcto), pero el requisito 9.1 pide rechazo explícito.
- **`POST /channels/{id}/members` es opcional en el enunciado pero se implementó.** Autorización en SQL (`rw_is_channel_admin`), alta idempotente (`ON CONFLICT DO NOTHING`), rol limitado a `member`/`admin` desde la API (nunca `owner`). `rw_channel_member` no tiene RLS, por eso el guard vive en el adaptador y no solo en una política.
- **Mapeo de errores de las funciones a HTTP:** `42501`/`28000` → 403 (`NotAuthorizedException`), `P0002` → 404 (`ResourceNotFoundException`), `55000` → 409 (`InvalidStateException`). La traducción (`DbFunctionErrors`) lee el `SQLState` de la `SQLException` más específica; el cuerpo de error y el correlation id son los ya existentes.
- **Ciclo de estados del mensaje (requisito 7):** `pending` lo asume el cliente al enviar (optimista). El backend inserta el mensaje ya como `sent` vía `rw_post_message`; la respuesta REST y el evento WebSocket lo llevan como `sent`. Si la operación falla (error de la función, red), el cliente marca `failed` y puede reintentar con el mismo `client_nonce`.
- **Dedup de reenvíos:** `client_nonce` (uuid) opcional en el body. `rw_post_message` hace `SELECT` por `(sender_id, client_nonce)` antes de insertar (índice único parcial `ux_rw_message_sender_nonce`); un segundo POST con el mismo nonce devuelve el mensaje existente, mismo `id`.
- **WebSocket plano de Spring (D3), endpoint `/ws/messages`.** Handshake autenticado con el **mismo access token JWT** pasado como **query param `access_token`** — la API `WebSocket` del navegador no permite cabeceras personalizadas, así que el header `Authorization` no es viable para el cliente Angular. `JwtHandshakeInterceptor` verifica el token y fija el `userId` en los atributos de la sesión; `/ws/**` queda `permitAll` en Spring Security porque su autenticación es el interceptor, no el filtro Bearer.
- **Emisión de eventos:** al publicar un mensaje, `PostMessageUseCase` registra un `afterCommit` que llama a `MessageBroadcastPort`. Se emite **después del commit** para que un receptor que reaccione al evento ya pueda leer el mensaje en el historial. El adaptador (`WebSocketMessageBroadcaster`) resuelve los destinatarios con `SELECT user_id FROM rw_channel_member WHERE channel_id = ?` — la pertenencia se valida en el servidor/SQL, nunca se confía en el cliente, y un no-miembro conectado no recibe nada.
- **Registro de sesiones en memoria (`WebSocketSessionRegistry`).** Suficiente para un despliegue de una instancia (el del assessment). Escalar a varias instancias requeriría un bus (Redis pub/sub) detrás del mismo `MessageBroadcastPort` — es cambio de adaptador, no de dominio.

**Fuera de alcance de esta entrega (backend):** edición/borrado de canales, expulsar miembros, indicadores de "escribiendo", entrega garantizada / cola offline de eventos WebSocket, y el reintento automático de mensajes `failed` (es responsabilidad del cliente).

---

## D8. Búsqueda de mensajes por API (Consulta 2)

**Decisión:** `GET /messages/search` delega la Consulta 2 tal cual está en `db/queries/02_message_search_headline.sql` (`websearch_to_tsquery` + `ts_headline` con `StartSel=<mark>`), sin reimplementar el resaltado ni el filtro de permisos en Java.

**Puntos concretos:**
- **Keyset sobre relevancia, no sobre `(created_at, id)`.** El orden de la Consulta 2 es `ts_rank DESC, id DESC`, así que el cursor debe codificar `(rank, id)` — no sirve el `Cursor(timestamp, id)` de mensajería (D7). Se añadió un modelo de dominio propio `SearchCursor(double rank, UUID id)` y un `SearchCursorCodec` (token opaco base64 `"<rank>|<uuid>"`), en paralelo al `CursorCodec` existente en vez de forzar una abstracción común: son dos dimensiones de orden distintas y mezclarlas ocultaría el criterio. El `rank` viaja como `double` y en SQL se compara con `CAST(:afterRank AS real)` para igualar el tipo de `ts_rank` (float4) y que la comparación de tuplas sea exacta. Nunca se usa `OFFSET`; se pide `limit + 1` para saber si hay página siguiente.
- **Permisos sólo por RLS.** La política `p_rw_message_select` ya filtra por membresía del actor fijado por transacción; no se agrega chequeo redundante en Java. A diferencia del historial de canal (D7), aquí un no-miembro **no** recibe 403: una búsqueda que no encuentra nada visible devuelve `200` con lista vacía. Test `PrivateChannelLeakIT` + `MessageSearchApiIT` cubren que un actor no ve mensajes de canales privados ajenos aunque contengan el término.
- **`channelId` opcional.** Restringe a un canal con `(CAST(:channelId AS uuid) IS NULL OR m.channel_id = :channelId)`; si el actor no es miembro de ese canal, la RLS deja el resultado vacío (no error).
- **`q` vacío o sólo espacios → 400** (`InvalidInputException` → `INVALID_INPUT`), validado en el caso de uso. Tope de página `size = 50`, default `20`.
- **Config de text search parametrizable** (`riwi.search.text-config`, default `spanish`) — debe coincidir con la del trigger `trg_rw_message_search_sync`; se pasa como `CAST(:lang AS regconfig)`, sigue siendo SQL parametrizado.
- **Patrón:** `MessageSearchRepository` es un puerto separado de `MessageRepository` (ISP): la búsqueda es una capacidad de lectura distinta, con su propio modelo de resultado (`SearchHit`/`SearchResultPage`).

**Fuera de alcance:** ranking por recencia combinado con relevancia, filtros por emisor/fecha, y resaltado en la búsqueda vectorial del RAG (esa es la Consulta 3).

---

## D9. Copiloto de IA (RAG): recuperación, permisos y honestidad

**Decisión:** el copiloto vive detrás de dos puertos de dominio (`ChatPort`, `EmbeddingPort`) sin ningún SDK; los adaptadores HTTP (`GroqChatAdapter`, `OpenAiEmbeddingAdapter`) usan `RestClient` y se configuran 100% por entorno. El dominio y los casos de uso nunca importan HTTP ni un proveedor concreto — cambiar de proveedor es solo `.env`.

**Endpoints (todos autenticados, actor del JWT):**
- `POST /copilot/query` — `{ question }` → `{ answer, status, citations[], usage }`. Valida pregunta no vacía (400).
- `GET /copilot/usage` — Consulta 4. El actor ve lo suyo; `is_platform_admin` desglosa por usuario (`?userId=`) o ve todo. Rango opcional `?from=&to=` (ISO-8601).
- `GET /copilot/history` — historial del propio actor, keyset sobre `(created_at, id)` reusando `CursorCodec`.
- `POST /internal/embeddings/backfill` — solo `is_platform_admin` (guard por claim del JWT en el controller, mismo criterio que el resto de rutas admin; 403 si no).

**Puertos y adaptadores nuevos:**
- `ChatPort` → `GroqChatAdapter` (`/chat/completions`, formato OpenAI; captura `usage.prompt_tokens`/`completion_tokens` y el `model` real de la respuesta).
- `EmbeddingPort` → `OpenAiEmbeddingAdapter` (`/embeddings`, valida que la dimensión devuelta = 1536).
- `CopilotContextRepository` → `JdbcCopilotContextRepository` (Consulta 3 literal + `rw_copilot_context_exists_elsewhere`).
- `CopilotQueryRepository` → `JdbcCopilotQueryRepository` (persistencia consulta+citas en la misma transacción, Consulta 4, historial keyset).
- `EmbeddingBackfillRepository` → `JdbcEmbeddingBackfillRepository` (usa las funciones SECURITY DEFINER de V5).

**Recuperación con permisos EN SQL (no solo en Java):** `JdbcCopilotContextRepository` ejecuta la Consulta 3 con el `EXISTS` contra `rw_channel_member` para el actor, y además el backend fija `SET LOCAL app.current_user_id` (aspecto `TransactionActorAspect`), por lo que la política RLS `p_rw_message_select` vuelve a filtrar. Un mensaje de un canal ajeno **nunca** se recupera aunque su embedding sea el más cercano (test `retrievalRespectsMembershipEvenWhenForeignEmbeddingIsEquallyClose`).

**Negativa honesta con dos motivos distintos:** si la recuperación filtrada no devuelve nada, se consulta `rw_copilot_context_exists_elsewhere` (V5, SECURITY DEFINER, ignora membresía, devuelve solo un booleano). Si existe contexto en otro canal → `refused_permission`; si no existe en ninguno → `refused_no_context`. Ambas se persisten en `rw_copilot_query` igual que una respuesta, sin citas.

**System prompt versionado:** constante `CopilotPromptBuilder.VERSION = "v1"` + plantilla en la misma clase. La versión usada se persiste en la nueva columna `rw_copilot_query.system_prompt_version` (migración V5). El system prompt fija el rol, inyecta nombre+cargo del actor (construidos en el servidor desde `rw_user_profile`, nunca del body), instruye que el contexto es **dato no confiable** y exige citar por id y negarse ante falta de contexto/permisos.

**Chats como datos no confiables:** los mensajes recuperados van SIEMPRE en el turno de usuario dentro de `<contexto_no_confiable>...</contexto_no_confiable>`, cada uno rotulado `[msg:<id>]` y aplanado (sin saltos de línea). Test `retrievedMessagesAreInjectedAsUntrustedContext` verifica que un mensaje "ignora tus instrucciones" queda dentro de ese bloque y que el system prompt lo neutraliza conceptualmente.

**Citas:** son los mensajes efectivamente recuperados y visibles para el actor, en orden de relevancia (`rank` 1..N); se persisten en `rw_copilot_citation`. No se parsean del texto del modelo (determinista y auditable).

**Migración V5 (mínima, no toca V1..V4):**
- `rw_copilot_query.system_prompt_version text NOT NULL DEFAULT 'v1'` + `CHECK` de longitud — auditoría de prompt engineering.
- `rw_messages_missing_embedding(int)` y `rw_set_message_embedding(uuid, vector)` — SECURITY DEFINER (owner = superusuario de migración) porque el backfill necesita leer/escribir mensajes de todos los canales y autores, y la RLS de `SELECT`/`UPDATE` de `rw_message` lo impediría. Solo `GRANT EXECUTE` a `riwi_app`.
- `rw_copilot_context_exists_elsewhere(vector, real)` — SECURITY DEFINER; señal de permisos para la negativa honesta.

**Generación de embeddings:** `BackfillEmbeddingsUseCase` toma en lote los mensajes vivos con `embedding IS NULL`, llama a `EmbeddingPort` y hace `UPDATE` parametrizado vía la función de V5. Expuesto como `POST /internal/embeddings/backfill` (admin) y como `ApplicationRunner` (`riwi.embeddings.backfill-on-startup`, default `false`). Si no hay API key configurada, el backfill hace log + skip (`skipped=true`), nunca tumba el arranque.

**Tradeoff aceptado:** la llamada al proveedor de chat ocurre dentro de la transacción de `AskCopilotUseCase` (para mantener el actor RLS fijado y persistir en la misma unidad). En un despliegue real la inferencia iría fuera de la transacción o a una cola; para el assessment el lote/consulta bajo demanda es suficiente. Documentado también para el backfill.

**Config nueva (`.env.example` + `application.yml`):** `AI_CHAT_BASE_URL/API_KEY/MODEL` (Groq, `llama-3.3-70b-versatile`), `AI_EMBEDDING_BASE_URL/API_KEY/MODEL/DIMENSIONS` (OpenAI, `text-embedding-3-small`, 1536), `COPILOT_TOP_K` (6), `COPILOT_MIN_SIMILARITY` (0.3), `EMBEDDINGS_BACKFILL_ON_STARTUP` (false).

**Tests (Testcontainers, puertos de IA mockeados — nunca se llama a Groq/OpenAI):** `FakeAiConfig` inyecta un `EmbeddingPort` determinista (mismo texto → mismo vector) y un `RecordingChatPort` (texto fijo, cuenta tokens, guarda el último prompt). Cubren: recuperación respeta membresía, `refused_no_context`, `refused_permission`, citas = mensajes recuperados + persistencia de `rw_copilot_query`/`rw_copilot_citation`, Consulta 4 (suma de tokens y aislamiento por actor / desglose admin), historial keyset sin fuga entre usuarios, contexto no confiable delimitado, `question` vacía → 400, y backfill admin-only que puebla embeddings faltantes.

**Fuera de alcance:** inferencia asíncrona/cola, re-embedding incremental automático tras editar un mensaje (el trigger ya invalida el vector; el backfill lo recalcula bajo demanda), streaming de la respuesta del copiloto, y la verificación de la llamada HTTP real a Groq/OpenAI (no se testea, solo los adaptadores quedan cableados por config).

---

## D10. Despliegue (`docker compose`) y cierre de agujeros del copiloto

**Decisión:** `docker compose up` levanta `db + migrator + backend + frontend` de una sola vez, en limpio, siguiendo el `README.md`.

**docker-compose:**
- **db** `pgvector/pgvector:pg16`, volumen nombrado `db_data`, healthcheck `pg_isready`. Puerto/credenciales del `.env`.
- **migrator** — imagen propia (`db/Dockerfile`, `FROM flyway/flyway:10-alpine` + `apk add postgresql-client`). One-shot: corre Flyway `V1..V6` como `riwi_root`, luego `ALTER ROLE riwi_app PASSWORD` con la password real de `DB_APP_PASSWORD` (V2 lo crea con una por defecto), luego carga `seed.json` reutilizando `db/seed_loader.sql`. **Idempotente**: el seed solo se carga si `rw_user` está vacío (o `SEED_FORCE=true`), así `docker compose up` dos veces no pisa datos de runtime. `backend` hace `depends_on: migrator: service_completed_successfully`.
- **backend** — `backend/Dockerfile` multi-stage (Maven build → `eclipse-temurin:21-jre-jammy` + `curl` para el healthcheck). Se conecta como `riwi_app`. Arranca con `EMBEDDINGS_BACKFILL_ON_STARTUP=true` + `EMBEDDINGS_BACKFILL_MODE=all` fijados en el compose (no en `.env`), para que tras `up` con API keys el corpus del seed quede con embeddings reales.
- **frontend** — `frontend/Dockerfile` multi-stage (`node:20-alpine` `npm ci && npm run build` → `nginx:1.27-alpine` sirviendo `dist/frontend/browser`), `nginx.conf` con `try_files $uri $uri/ /index.html`.
- **Comando manual de migraciones/seed:** `docker compose run --rm [-e SEED_FORCE=true] migrator` o `scripts/db.sh {migrate|seed}` (documentado en `README` §3).

**`API_BASE_URL` del frontend:** `api.config.ts` es una constante **build-time**. Como no se puede tocar `frontend/src/`, el `frontend/Dockerfile` hace `sed` sobre una copia del archivo dentro del contenedor de build, tomando el valor del build-arg `API_BASE_URL` (default `http://localhost:8080`, que ya sirve para el compose local). Se eligió build-arg + `sed` por ser lo más simple; la alternativa (generar `env.js` en el entrypoint de nginx) exigía cambios en `index.html` y en el código Angular.

**Agujeros del copiloto cerrados:**
- **B1 — el adaptador de embeddings ahora envía `"dimensions"`** en el body (`AI_EMBEDDING_DIMENSIONS`, default 1536). Sin esto, Gemini (`gemini-embedding-001`) devuelve 3072 y todo falla. Sigue validando que la respuesta tenga esa dimensión. Test unitario `OpenAiEmbeddingAdapterTest` con `MockRestServiceServer` enlazado al `RestClient.Builder` (por eso el adaptador ahora recibe el builder inyectado en vez de crearlo). 
- **B2 — modo `EMBEDDINGS_BACKFILL_MODE=missing|all`** (default `missing`). `all` re-embebe todos los mensajes vivos vía la nueva función `rw_messages_for_reembedding` (V6, SECURITY DEFINER) y sobrescribe los embeddings **sintéticos** que `seed_loader.sql` genera para los tests. No se tocó `seed_loader.sql` ni los tests existentes (siguen con `DeterministicEmbeddingPort` + vectores sintéticos); el modo `all` es aditivo.
- **B3 — `GET /internal/copilot/status`** (solo `is_platform_admin`): `totalMessages`, `messagesWithEmbedding` (función V6 `rw_message_embedding_stats`), `embeddingModel`, `chatModel`, y `embeddingProviderReachable` / `chatProviderReachable` (ping real: un embedding de "ping" y un chat de un turno mínimo, `true` si respondió).
- **B4 — `CopilotLiveIT`** anotado `@EnabledIfEnvironmentVariable` para `AI_CHAT_API_KEY` y `AI_EMBEDDING_API_KEY`: no corre en `mvn test` sin keys. Con keys: dimensión real == `AI_EMBEDDING_DIMENSIONS`, chat real con `usage` > 0, y end-to-end (seed → backfill `all` real → `POST /copilot/query`) con `answered`+citas para una pregunta del corpus y `refused_no_context` fuera de contexto. Comando: `mvn -f backend/pom.xml test -Dtest=CopilotLiveIT`.
- **B5 — model id de Groq:** `.env.example` deja el comentario con el fallback `llama-3.1-8b-instant` (límite diario más alto) por si `llama-3.3-70b-versatile` está deprecado el día del demo.

**Migración nueva V6 (`db/migrations/V6__copilot_ops.sql`):** `rw_messages_for_reembedding(int)` y `rw_message_embedding_stats()`, ambas SECURITY DEFINER con `GRANT EXECUTE` solo a `riwi_app`. No toca V1..V5.

**Verificado:** `mvn -f backend/pom.xml test` en verde (50 tests, `CopilotLiveIT` saltado sin keys). `docker compose config` válido.

**Fuera de alcance:** hot-reload de `API_BASE_URL` sin rebuild del frontend, TLS/reverse-proxy de producción, y correr `CopilotLiveIT` en CI (requiere secretos).
