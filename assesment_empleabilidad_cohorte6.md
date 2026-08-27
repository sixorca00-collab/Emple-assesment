# Assesment Empleabilidad - Cohorte 6

## Propósito
El propósito de esta prueba técnica es evaluar la capacidad del coder para construir una solución fullstack profesional basada en una plataforma interna de mensajería, integrando base de datos relacional, backend, frontend, autenticación, seguridad a nivel de datos e inteligencia artificial con recuperación aumentada por contexto (RAG). 

La prueba busca evidenciar:
- Análisis de negocio
- Normalización hasta 3FN
- Lógica crítica dentro de PostgreSQL
- Arquitectura limpia (Clean Architecture)
- Experiencia de usuario responsiva
- Copiloto de IA que responda únicamente con información permitida para el usuario autenticado

---

## Metodología y Reglas

### Tiempo y Duración
- **Duración:** 8 horas continuas en jornada observada.
- **Descansos:** 3 descansos de 20 minutos (no cuentan dentro del tiempo oficial).
- **Priorización:** El coder debe priorizar los requisitos del MVP, justificar recortes en `DECISIONS.md` y demostrar criterio técnico durante la ejecución.

### Rúbricas de Evaluación
- Consulta en Moodle la rúbrica oficial de la prueba para validar los criterios de evaluación y ponderación.

### Reglas General
1. **Prohibido el plagio.**
2. La prueba es estrictamente individual.
3. Se permite el uso de documentación oficial, ejemplos de código abierto y herramientas de IA como apoyo, evitando el plagio.
4. El coder debe tener la capacidad de explicar su código durante la sustentación.

### Requerimientos para la Sustentación Técnica
- Entregar un documento explicativo de todos los requerimientos técnicos descritos en la prueba.

---

## Descripción del Proyecto

**Riwi Co. S.A.S.** requiere modernizar su comunicación interna mediante una plataforma de mensajería organizada, segura y consistente. El sistema debe administrar:
- Usuarios y perfiles
- Mensajes y estados de lectura
- Búsqueda de conversaciones
- Consultas a un copiloto de IA

Adicionalmente, los mensajes deben poderse eliminar o editar conservando sus estados originales en caso de fallo. 

> ⚠️ **Requisito No Negociable:** Ningún usuario debe poder leer, buscar o consultar mediante el copiloto contenido al cual no tenga acceso explícito.

---

## Requerimientos Técnicos

### 1. Análisis, Normalización y Modelo de Datos
- **Modelo Entidad-Relación (MER):** Construir el MER especificando entidades, atributos, claves primarias (PK), claves foráneas (FK), cardinalidades y la justificación del tipo de clave elegido.
- **Corpus & Normalización:** Crear un archivo `seed.json` que identifique entidades, relaciones y reglas de negocio implícitas, documentando el proceso de normalización hasta Primera (1FN), Segunda (2FN) y Tercera Forma Normal (3FN).

### 2. Implementación de Base de Datos en PostgreSQL
- Base de datos en PostgreSQL 15+ nombrada como `bd_nombre_apellido_clan`.
- **Nomenclatura:** Todos los nombres de tablas y columnas deben estar en inglés e iniciar con el prefijo `rw_`.
- **DDL:** Incluir DDL completo, PKs, FKs con `ON DELETE` explícito y justificado, `UNIQUE`, al menos un índice único parcial, `NOT NULL`, `CHECK` y fechas `timestamptz` en UTC.

### 3. Lógica de Negocio en la Base de Datos
- **Funciones Transaccionales:** Garantizar validación de permisos en la BD sin dejar rastros parciales ante errores.
- **Row Level Security (RLS):** Activar RLS sobre canales y mensajes usando un rol de aplicación sin `BYPASSRLS` y un actor fijado por transacción mediante `app.current_user_id`.
- **Vistas:** Crear la vista de conversaciones del usuario.
- **Procedimientos Almacenados:** Mínimo dos SP:
  1. Consulta de usuarios.
  2. Edición y eliminación de usuarios.

### 4. Búsqueda, Recuperación de Contexto y Seguridad
- **Delimitación de Permisos:** El copiloto solo recupera mensajes de canales donde el actor sea miembro (sin acceso a mensajes globales ajenos).
- **Base Vectorial & Embeddings:** Usar una base vectorial para almacenar mensajes y un motor de embeddings para su recuperación con el LLM.
- **Triggers:** Incorporar al menos un trigger para mantener consistente el vector de búsqueda.
- **Restricciones:** Prohibido el borrado físico de mensajes (usar soft delete), la concatenación de SQL (prevenir SQL Injection) y la paginación con `OFFSET` (usar keyset pagination).

### 5. Backend y API REST
- **Clean Architecture:** Capas explícitas con dependencias apuntando hacia el dominio. El dominio no debe depender de frameworks web ni drivers de BD.
- **Casos de Uso Delgados:** Validar entrada, invocar funciones de BD y mapear resultados.
- **Principios SOLID & Patrones:** Demostrar SOLID en código y justificar cualquier patrón de diseño aplicado.
- **API REST:** Respuestas con códigos HTTP correctos, manejo uniforme de errores, identificador de correlación y paginación por keyset.

### 6. Autenticación y Autorización
- Inicio de sesión validando contraseñas contra hashes seguros.
- **JWT:** Access token de vida corta y Refresh Token con rotación (almacenado de forma segura).
- **Seguridad:** Proteger rutas obteniendo el ID de usuario exclusivamente desde el token (nunca del body de la petición). Propagar el actor autenticado a las funciones de BD y políticas RLS.

### 7. Frontend
- **Interfaz (3 zonas mínimas):** Conversación, Panel del copiloto y Perfil de usuario.
- **Mensajería en tiempo real:** Envío de mensajes con estados `pendiente`, `enviado` y `fallido`.
- **UX/UI:** Carga diferida de historial preservando scroll, estados de carga, pantalla vacía y error.
- **Diseño & i18n:** Diseño responsivo (Mobile/Desktop), soporte multilenguaje (Español/Inglés) sin cadenas de texto incrustadas (*hardcoded*) en componentes.

### 8. Copiloto de IA
- **Enfoque RAG:** Recuperar contexto perteneciente únicamente al actor que realiza la consulta.
- **Citas & Honestidad:** Respuestas con citas a mensajes fuente y negativa honesta ante falta de contexto.
- **Contexto de Usuario:** El copiloto conoce nombre y cargo del usuario autenticado (construido en el servidor desde el token).
- **Abstracción:** Proveedor de IA intercambiable mediante interfaz estándar (ej. OpenAI SDK).
- **Prompt Engineering:** System prompt versionado, tratamiento de chats como datos no confiables y negativas explícitas por falta de permisos o fuera de alcance.

### 9. QA, Evidencias y Extras
- **Pruebas Automatizadas:** Mínimo 2 pruebas contra PostgreSQL real:
  1. Verificar rechazo a usuario no miembro.
  2. Confirmar que no retorna mensajes de canales privados ajenos.
- **Evidencias Video/Capturas:** Video demo de máx. 5 minutos enfocado a pitch comercial mostrando: login, envío de mensaje, búsqueda, RAG con citas y negativa por falta de permisos.

### 10. Despliegue
- `docker compose up` debe levantar Base de Datos, Backend y Frontend.
- Comando documentado para migraciones y carga del `seed.json`.
- Archivo `.env.example` sin secretos reales. El proyecto debe levantarse en limpio siguiendo el `README.md`.

### 11. Consultas y Funciones SQL Requeridas
- **Consulta 1:** Historial de mensajes de un canal con paginación por keyset.
- **Consulta 2:** Búsqueda de mensajes con resaltado del término encontrado.
- **Consulta 3:** Recuperación de contexto para el copiloto con permisos aplicados en SQL.
- **Consulta 4:** Consumo acumulado del copiloto por usuario.

---

## Entregables
- Scripts SQL: DDL, DML, carga inicial, consultas, funciones, triggers, vistas, procedimientos almacenados y políticas RLS.
- Modelo Entidad Relación (PDF/Imagen), `seed.json` original y archivos de arquitectura.
- Documentación API (Swagger/OpenAPI publicado o colección de Postman exportada).
- Archivos de documentación: `README.md`, `ARCHITECTURE.md`, `DECISIONS.md`, evidencias de ejecución y URL del repositorio.

---

## Criterios de Aceptación y Condiciones de Invalidación

### Criterios de Aceptación
- Modelo en 3FN que representa el negocio.
- Lógica crítica centralizada en PostgreSQL (transacciones, RLS, SP, funciones, triggers).
- Carga correcta del corpus de datos.
- Funcionamiento end-to-end de API, JWT, Frontend responsivo, i18n y copiloto RAG.
- Mensajería funcionando en tiempo real.

### Condiciones que Invalidan la Prueba ❌
- Almacenamiento de contraseñas en texto plano.
- Primer commit contiene lógica previa al inicio de la jornada.
- El coder no puede explicar el código entregado o el repositorio es clon/derivado de un proyecto existente.

---
*Riwi Co. S.A.S. | www.riwi.io | Cl. 16 #55-129 | Tel: 301 7325327*
