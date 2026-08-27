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

## D4. (Placeholder) Recortes durante la ejecución

> Completar en vivo durante la jornada si se decide posponer o simplificar algún requerimiento.

- [ ] Ejemplo: "Se pospuso X por Y, impacto Z."

---

## D5. (Placeholder) Patrones de diseño aplicados

> Completar al implementar. Justificar cada patrón usado (ej. Repository, Adapter/Ports, Strategy para el proveedor de IA, etc.) y por qué se eligió sobre alternativas.
