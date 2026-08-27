# Normalizacion del modelo de datos - Riwi Co.

Documento textual del proceso de analisis y normalizacion hasta 3FN.
El diagrama Entidad-Relacion (MER) se entrega aparte; aqui va solo la parte textual/tabular.

Convenciones: tablas y columnas en ingles, prefijo `rw_`, fechas `timestamptz` en UTC.

---

## 1. Analisis del negocio y reglas implicitas

Del enunciado y del corpus (`seed.json`) se extraen estas reglas:

- **R1.** Un usuario tiene credenciales (correo, hash de contrasena) y un perfil (nombre visible, cargo). El correo identifica al usuario para el login pero puede cambiar.
- **R2.** Nunca se guardan contrasenas en texto plano.
- **R3.** Un canal es publico o privado. Los canales privados solo son accesibles para sus miembros.
- **R4.** Un usuario puede pertenecer a muchos canales; un canal tiene muchos miembros. Un usuario aparece una sola vez por canal, con un rol (`owner` / `admin` / `member`).
- **R5.** Un mensaje pertenece a exactamente un canal y tiene exactamente un autor.
- **R6.** Los mensajes se editan y se eliminan conservando su estado original; la eliminacion es logica (soft delete), nunca fisica.
- **R7.** Un mensaje tiene un estado de entrega (`pending` / `sent` / `failed`) desde la perspectiva del emisor (mensajeria en tiempo real).
- **R8.** El estado de lectura es por usuario y por mensaje: un usuario marca un mensaje como leido una sola vez, en un instante.
- **R9.** El copiloto registra cada consulta de un usuario: pregunta, respuesta, modelo usado y tokens consumidos (prompt y completion).
- **R10.** Una respuesta del copiloto puede citar varios mensajes como fuente; un mismo mensaje se cita una vez por respuesta, con un orden.
- **R11.** El copiloto solo puede usar como contexto mensajes de canales donde el usuario que pregunta es miembro.
- **R12.** La autenticacion usa access token corto + refresh token con rotacion; del refresh token se almacena solo su hash.
- **R13.** Toda marca de tiempo se guarda en UTC.

---

## 2. Entidades y atributos (forma no normalizada -> 1FN)

### 2.1 Punto de partida (no normalizado)

Una vision ingenua "todo en una fila de mensaje" tendria:

```
mensaje(canal_nombre, canal_privado, autor_correo, autor_nombre, autor_cargo,
        cuerpo, estado, creado, editado, borrado,
        [lista de usuarios que leyeron], [lista de citas del copiloto], ...)
```

Problemas: grupos repetitivos (listas de lectores/citas), y datos de canal y de autor repetidos en cada mensaje.

### 2.2 Primera Forma Normal (1FN)

Regla: dominios atomicos, sin grupos repetitivos, cada tabla con clave.

- Se eliminan las listas: "usuarios que leyeron" pasa a una tabla propia `rw_message_read_receipt` (una fila por lector/mensaje); "citas" pasa a `rw_copilot_citation`.
- Se separan las entidades independientes: `rw_user`, `rw_channel`, `rw_message`.
- Cada tabla recibe clave primaria.
- Fechas como `timestamptz` unico (valor atomico), no texto libre.

### 2.3 Segunda Forma Normal (2FN)

Regla: en tablas con clave compuesta, ningun atributo no-clave depende de solo parte de la clave.

- `rw_channel_member (channel_id, user_id)`: los unicos atributos no-clave son `role` y `joined_at`, y ambos dependen del par completo (el rol es del usuario *en ese canal*). Cumple 2FN.
- `rw_message_read_receipt (message_id, user_id)`: `read_at` depende del par completo. Cumple 2FN.
- `rw_copilot_citation (query_id, message_id)`: `rank` y `created_at` dependen del par completo. Cumple 2FN.
- Datos que dependian solo del canal (`name`, `is_private`) o solo del autor (`display_name`, `job_title`) **no** viven en `rw_message`: estan en `rw_channel` y `rw_user_profile`. Se referencian por FK.

### 2.4 Tercera Forma Normal (3FN)

Regla: ningun atributo no-clave depende transitivamente de la clave (todo atributo depende de la clave, de toda la clave y de nada mas que la clave).

- `rw_message`: `sender_id` y `channel_id` son FK; `display_name` del autor o `name` del canal serian dependencias transitivas -> se excluyen y se obtienen por JOIN. `total_tokens` no aplica aqui.
- `rw_copilot_query`: `total_tokens` = `prompt_tokens + completion_tokens` es una dependencia derivada. Se modela como **columna generada** (`GENERATED ALWAYS AS ... STORED`), no como dato editable independiente: evita el riesgo de inconsistencia sin perder la comodidad de sumarlo (Consulta 4).
- `rw_user` vs `rw_user_profile`: se separan credenciales de perfil. Ambas comparten PK (`user_id`), relacion 1:1 estricta. Motivo: mantener minima y auditable la tabla con el hash de contrasena, y permitir evolucionar el perfil sin tocar la tabla de acceso. No introduce redundancia (cada dato en un solo lugar).
- `rw_refresh_token`: `replaced_by` apunta a otro token (cadena de rotacion). No hay atributos transitivos; `token_hash` es el unico dato sensible y se guarda hasheado.

Todas las tablas quedan en 3FN.

---

## 3. Tablas, claves y cardinalidades

| Tabla | PK | Tipo de clave | Justificacion del tipo de clave | FKs (accion ON DELETE) | Cardinalidad |
|---|---|---|---|---|---|
| `rw_user` | `id` (uuid) | Sintetica | El correo (candidato natural) puede cambiar; una PK inmutable evita propagar el cambio por todas las FK. `uuid` (no serial) para no filtrar volumen de altas y para poder generar ids en el cliente/tests. Unicidad del correo garantizada por indice unico parcial `lower(email) WHERE deleted_at IS NULL`. | - | 1 usuario : 1 perfil ; 1 : N mensajes ; 1 : N membresias |
| `rw_user_profile` | `user_id` (uuid) | Natural = FK | La PK es la misma FK a `rw_user`: fuerza el 1:1 exacto y elimina una columna id redundante. | `user_id` -> `rw_user(id)` **CASCADE** (el perfil no existe sin su usuario) | 1 : 1 con `rw_user` |
| `rw_channel` | `id` (uuid) | Sintetica | El nombre es mutable y solo unico entre canales vigentes (indice parcial `lower(name) WHERE deleted_at IS NULL`); PK estable independiente del nombre. | `created_by` -> `rw_user(id)` **RESTRICT** (se conserva la autoria; los usuarios se soft-borran, no se eliminan) | 1 canal : N miembros ; 1 : N mensajes |
| `rw_channel_member` | (`channel_id`, `user_id`) | Natural compuesta | La relacion N:M no necesita id propio: el par ya es unico y es la forma natural de consultarla ("es X miembro de Y"). | `channel_id` -> `rw_channel(id)` **CASCADE** ; `user_id` -> `rw_user(id)` **CASCADE** (la membresia es una relacion, no existe sin sus dos extremos) | N usuarios : M canales |
| `rw_message` | `id` (uuid) | Sintetica | Alto volumen, se referencia desde acuses y citas; `uuid` para ids estables generables en tests y para keyset pagination `(created_at, id)`. | `channel_id` -> `rw_channel(id)` **CASCADE** (los mensajes siguen el ciclo de vida del canal) ; `sender_id` -> `rw_user(id)` **RESTRICT** (integridad de autoria) ; `deleted_by` -> `rw_user(id)` **SET NULL** (dato informativo, no debe bloquear) | 1 canal : N mensajes ; 1 autor : N mensajes |
| `rw_message_read_receipt` | (`message_id`, `user_id`) | Natural compuesta | Un usuario lee un mensaje una sola vez; el par es la clave natural y evita una columna id inutil. | `message_id` -> `rw_message(id)` **CASCADE** ; `user_id` -> `rw_user(id)` **CASCADE** (dato derivado: si desaparece el mensaje o el usuario, el acuse no tiene sentido) | N mensajes : M lectores |
| `rw_copilot_query` | `id` (uuid) | Sintetica | Entidad de bitacora con vida propia; se referencia desde las citas. | `user_id` -> `rw_user(id)` **CASCADE** (historial privado del usuario) | 1 usuario : N consultas ; 1 consulta : N citas |
| `rw_copilot_citation` | (`query_id`, `message_id`) | Natural compuesta | Una respuesta cita un mensaje una vez; el par es unico. `rank` da el orden de la cita. | `query_id` -> `rw_copilot_query(id)` **CASCADE** (la cita nace de la consulta) ; `message_id` -> `rw_message(id)` **RESTRICT** (una cita debe apuntar a un mensaje real; los mensajes se soft-borran, no se eliminan) | N consultas : M mensajes |
| `rw_refresh_token` | `id` (uuid) | Sintetica | Se rota con frecuencia; `token_hash` es unico pero es un secreto, no una buena PK visible. | `user_id` -> `rw_user(id)` **CASCADE** (estado de sesion) ; `replaced_by` -> `rw_refresh_token(id)` **SET NULL** (enlace de cadena de rotacion, informativo) | 1 usuario : N tokens |

---

## 4. Restricciones de integridad relevantes

- **Indices unicos parciales** (uno de los requisitos): `ux_rw_user_email_active` y `ux_rw_channel_name_active` -> la unicidad de correo y de nombre de canal aplica solo a filas no borradas, permitiendo re-alta con el mismo valor.
- **CHECK**: formato de correo; longitud minima del hash de contrasena (nunca texto plano); longitudes de `display_name`, `job_title`, `name`, `body`, `question`; dominio de `status` y `role`; `edited_at >= created_at`; `deleted_at >= created_at`; coherencia del soft delete (`deleted_at` y `deleted_by` van juntos o ninguno); tokens `>= 0`; `expires_at > issued_at`.
- **Soft delete**: `rw_message` no tiene politica RLS de `DELETE`, se revoca el privilegio `DELETE` y un trigger `BEFORE DELETE` aborta cualquier intento -> el borrado fisico de mensajes es imposible por diseno.
- **Derivados controlados**: `total_tokens` es columna generada; `search_tsv` lo mantiene un trigger; ninguno es editable a mano.
- **Keyset**: el indice `ix_rw_message_channel_keyset (channel_id, created_at DESC, id DESC) WHERE deleted_at IS NULL` soporta la paginacion por keyset sin `OFFSET`.

---

## 5. Seguridad a nivel de datos (contexto del modelo)

- La pertenencia a un canal (`rw_channel_member`) es la unica llave de acceso a los mensajes: las politicas RLS de `rw_channel` y `rw_message` la consultan via `rw_is_channel_member()` usando el actor fijado por transaccion (`app.current_user_id`).
- La Consulta 3 (contexto del copiloto) ademas repite el filtro de membresia de forma explicita en el `WHERE` (no solo confia en RLS ni en el codigo Java), cumpliendo R11.
- `rw_copilot_query` guarda el consumo por usuario para la Consulta 4; no expone contenido de canales.
