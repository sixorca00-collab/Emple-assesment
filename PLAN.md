# Plan Técnico - Assessment Empleabilidad Cohorte 6

## 1. Estrategia de Repositorio: Monorepo

**Decisión: un solo repositorio.**

Razones:
- El entregable pide `docker compose up` levantando DB + Backend + Frontend como una unidad — con multi-repo eso implica submódulos o scripts de sincronización, complejidad que no aporta nada en 8h.
- Una condición de invalidación es que el **primer commit no contenga lógica previa al inicio de la jornada**. Un solo repo hace trivial demostrar la línea de tiempo real de commits; con varios repos hay que demostrarlo N veces.
- `ARCHITECTURE.md` y `DECISIONS.md` documentan el sistema como un todo (Clean Architecture, RLS, RAG). Con un solo repo, la documentación y el código evolucionan juntos.
- Backend (Java/Spring) y Frontend (Angular) son proyectos con build tools distintos (Maven/Gradle vs npm) pero eso no es problema en un monorepo: cada uno vive en su carpeta con su propio `pom.xml`/`package.json`, y el `docker-compose.yml` en la raíz orquesta ambos.
- Multi-repo solo se justifica con equipos/despliegues independientes — aquí hay un solo coder y un solo despliegue.

## 2. Estructura de Carpetas Propuesta

```
/ (raíz del repo)
├── docker-compose.yml
├── .env.example
├── README.md
├── ARCHITECTURE.md
├── DECISIONS.md
├── docs/
│   ├── MER.png (o .pdf)
│   └── openapi.yaml (o Postman collection exportada)
├── db/
│   ├── migrations/          # V1__init.sql, V2__rls.sql, V3__triggers.sql (Flyway)
│   ├── seed.json
│   └── queries/             # las 4 consultas SQL requeridas, documentadas
├── backend/                  # proyecto Maven/Gradle independiente
│   ├── pom.xml
│   └── src/main/java/.../
│       ├── domain/           # entidades, value objects, puertos (interfaces) - 0 dependencias de Spring
│       ├── application/      # casos de uso - orquestan, no contienen SQL
│       ├── infrastructure/   # adapters: JdbcTemplate/jOOQ, JWT, proveedor IA, WebSocket
│       └── interfaces/       # controllers REST, DTOs, filtros/middlewares
│   └── src/test/java/.../    # integración con Testcontainers (Postgres real)
└── frontend/                  # proyecto Angular independiente
    └── src/app/
        ├── features/          # chat, copiloto, perfil
        ├── i18n/               # es.json, en.json (ngx-translate)
        └── shared/
```

## 3. Stack Tecnológico Recomendado

| Capa | Elección | Justificación |
|---|---|---|
| Base de datos | **PostgreSQL 15+** con extensión **pgvector** | Un único motor sirve como BD relacional y vectorial: cumple el requisito 4 sin sumar un servicio extra (Qdrant/Chroma) al `docker-compose`. |
| Búsqueda de texto | `tsvector` + `ts_headline` nativo | Resuelve la Consulta 2 (resaltado de término) sin librerías externas. |
| Migraciones | **Flyway** con SQL plano numerado (`V1__*.sql`) | El DDL debe ser escrito a mano y auditable; Flyway solo ejecuta scripts, no los genera (a diferencia de Hibernate `ddl-auto`). |
| Backend runtime | **Java 21 + Spring Boot 3** | Tu stack de mayor dominio; los Records de 21 simplifican los DTOs; acelera el desarrollo dentro de las 8h. |
| Seguridad | **Spring Security** + `jjwt` | Access token corto + refresh con rotación; filtros de Spring Security para extraer el `user_id` únicamente del token. |
| Acceso a datos | **`JdbcTemplate`** (o `jOOQ` si prefieres SQL tipado) — **evitar JPA/Hibernate como capa principal** | La lógica vive en funciones/SPs de Postgres y depende de `app.current_user_id` fijado por transacción (`SET LOCAL`); un ORM con sesión/persistence-context complica ese control fino. `JdbcTemplate` + un interceptor de transacción que ejecuta el `SET LOCAL` es explícito y simple. |
| Contexto de actor en RLS | Filtro Spring (`OncePerRequestFilter`) + aspecto `@Transactional` que hace `SET LOCAL app.current_user_id = ?` al abrir cada transacción | Propaga el actor autenticado del JWT hacia Postgres sin tocar cada caso de uso manualmente. |
| Tiempo real | **WebSocket plano de Spring** (`spring-boot-starter-websocket`, sin STOMP) | Menos piezas nuevas que aprender junto con Angular; STOMP/SockJS se puede añadir después si sobra tiempo. |
| IA / RAG | Interfaz propia `AiProvider` (puerto en `domain`) + adaptador HTTP a **OpenAI** (o Anthropic) en `infrastructure` | Cumple "proveedor intercambiable": el dominio y los casos de uso nunca importan el SDK concreto. |
| Embeddings | OpenAI `text-embedding-3-small`, guardado en columna `vector` (pgvector) | Barato, rápido de integrar vía HTTP simple (no requiere el SDK oficial si prefieres `RestClient`/`WebClient`). |
| Frontend | **Angular 17+ (standalone components)** | Standalone evita el boilerplate de `NgModule`, más fácil de aprender rápido siendo nuevo en el framework. |
| Estado | **Signals** de Angular (nativo) en vez de NgRx | Un concepto nuevo (Angular) menos otro concepto nuevo (NgRx) que aprender el mismo día. |
| Estilos | **Tailwind CSS** | Evita aprender la API/temas de Angular Material bajo presión de tiempo; responsive rápido con clases utilitarias. |
| i18n | **ngx-translate** | Permite cambiar idioma en runtime sin rebuild (a diferencia del i18n nativo de Angular, que exige compilaciones separadas por locale). |
| HTTP/WebSocket client | `HttpClient` de Angular + `WebSocket` nativo del navegador | Sin librerías adicionales que aprender. |
| Testing backend | **JUnit 5 + Testcontainers (Postgres real)** | Es el estándar de facto en el ecosistema Spring para el requisito "pruebas contra Postgres real, no mocks". |
| Contenedores | **Docker Compose** (db, backend, frontend) | Requisito explícito de despliegue en limpio. |

## 4. Ruta de Trabajo Sugerida (8h, con 3 descansos de 20 min)

Ajustada para reservar más colchón en frontend por la curva de aprendizaje de Angular. Prioriza siempre lo que pesa más en la rúbrica: **lógica en BD (RLS, funciones, SPs)** y **seguridad del RAG**.

| # | Bloque | Tiempo aprox. | Entregable parcial |
|---|---|---|---|
| 1 | Setup: estructura de carpetas, `docker-compose.yml` esqueleto (Postgres + placeholders backend/frontend), primer commit limpio | 20 min | Repo inicializado, sin lógica previa |
| 2 | Modelado: MER + `seed.json` + justificación 1FN→3FN | 45 min | `docs/MER.png`, `db/seed.json` |
| 3 | DDL: tablas `rw_*`, PKs/FKs con `ON DELETE`, `CHECK`, índice único parcial, `timestamptz` | 45 min | `db/migrations/V1__init.sql` |
| 4 | Lógica crítica en BD: RLS + rol de app + `app.current_user_id`, funciones transaccionales, SPs (consulta/edición-eliminación usuarios), vista de conversaciones, trigger de vector | 90 min | `V2__rls.sql`, `V3__functions.sql`, `V4__triggers.sql` |
| — | *Descanso 20 min* | | |
| 5 | Backend Spring Boot - esqueleto Clean Architecture: dominio + casos de uso + adapters (JdbcTemplate, JWT) + endpoints auth/canales/mensajes con keyset pagination | 90 min | API funcional con auth |
| 6 | RAG: adaptador de embeddings, endpoint del copiloto, recuperación con permisos aplicados en SQL (Consulta 3), citas | 60 min | Copiloto respondiendo con contexto propio |
| — | *Descanso 20 min* | | |
| 7 | Frontend Angular: `ng new` standalone + Tailwind, 3 zonas mínimas, envío de mensajes con estados, i18n con ngx-translate | 70 min | UI funcional conectada al backend (prioriza funcional sobre pulido) |
| 8 | Tests obligatorios (no-miembro rechazado, no fuga de canal privado) con Testcontainers | 30 min | Tests en verde |
| — | *Descanso 20 min* | | |
| 9 | Documentación final: `README.md`, `ARCHITECTURE.md`, `DECISIONS.md`, export OpenAPI/Postman, revisión de `.env.example` | 30 min | Repo listo para clonar y levantar |
| 10 | Buffer / grabación del video demo (≤5 min) | 20–30 min | Evidencia entregada |

## 5. Riesgos a Vigilar Durante la Ejecución
- No dejar rastro de `OFFSET` en ninguna consulta de listados (usar siempre keyset).
- Verificar que **ningún** query del backend concatene strings SQL (todo parametrizado vía `JdbcTemplate`/`jOOQ`).
- Confirmar que el JWT es la única fuente del `user_id` en cada request protegido (nunca leerlo del body).
- Si el tiempo aprieta, recortar primero pulido visual de Angular (por ser tecnología nueva), nunca la lógica de RLS/permisos.
- Probar manualmente el caso "usuario sin acceso a canal" contra el copiloto antes de grabar el video.
