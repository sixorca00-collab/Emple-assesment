
-- forzamos que la sesion trabaje en UTC durante la migracion
SET TIME ZONE 'UTC';

-- extension vectorial en la MISMA base (requisito 4), no un motor aparte
CREATE EXTENSION IF NOT EXISTS vector;

-- pgcrypto solo para gen_random_uuid en versiones donde no es core
CREATE EXTENSION IF NOT EXISTS pgcrypto;


-- ============================================================
-- rw_user: credenciales e identidad de acceso
-- ============================================================
CREATE TABLE rw_user (
    id                 uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    email              text        NOT NULL,
    password_hash      text        NOT NULL,
    is_platform_admin  boolean     NOT NULL DEFAULT false,
    is_active          boolean     NOT NULL DEFAULT true,
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    deleted_at         timestamptz,
    -- formato minimo de correo, la unicidad real la da el indice parcial de abajo
    CONSTRAINT ck_rw_user_email_format CHECK (email ~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$'),
    -- nunca guardamos contrasenas en texto plano: el hash siempre tiene longitud de bcrypt/argon
    CONSTRAINT ck_rw_user_password_hash_len CHECK (char_length(password_hash) >= 20),
    CONSTRAINT ck_rw_user_deleted_after_created CHECK (deleted_at IS NULL OR deleted_at >= created_at)
);

-- indice unico parcial del correo: solo aplica a usuarios no borrados, asi se permite re-alta con el mismo correo
CREATE UNIQUE INDEX ux_rw_user_email_active
    ON rw_user (lower(email))
    WHERE deleted_at IS NULL;


-- ============================================================
-- rw_user_profile: perfil 1:1 con rw_user, separado para dejar minima la tabla de credenciales
-- ============================================================
CREATE TABLE rw_user_profile (
    -- la PK es tambien FK: garantiza el 1:1 exacto con rw_user
    user_id      uuid        PRIMARY KEY
                             REFERENCES rw_user (id) ON DELETE CASCADE, -- el perfil no tiene sentido sin su usuario
    display_name text        NOT NULL,
    job_title    text        NOT NULL,
    avatar_url   text,
    bio          text,
    updated_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_rw_user_profile_display_name_len CHECK (char_length(btrim(display_name)) BETWEEN 2 AND 80),
    CONSTRAINT ck_rw_user_profile_job_title_len   CHECK (char_length(btrim(job_title)) BETWEEN 2 AND 80)
);


-- ============================================================
-- rw_channel: canales de conversacion (publicos o privados)
-- ============================================================
CREATE TABLE rw_channel (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    name        text        NOT NULL,
    description text,
    is_private  boolean     NOT NULL DEFAULT false,
    created_by  uuid        NOT NULL
                            REFERENCES rw_user (id) ON DELETE RESTRICT, -- conservamos la autoria del canal; el usuario se soft-borra, no se elimina
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    deleted_at  timestamptz,
    CONSTRAINT ck_rw_channel_name_len CHECK (char_length(btrim(name)) BETWEEN 2 AND 80),
    CONSTRAINT ck_rw_channel_deleted_after_created CHECK (deleted_at IS NULL OR deleted_at >= created_at)
);

-- indice unico parcial: el nombre de canal es unico solo entre canales vigentes
CREATE UNIQUE INDEX ux_rw_channel_name_active
    ON rw_channel (lower(name))
    WHERE deleted_at IS NULL;


-- ============================================================
-- rw_channel_member: membresia usuario-canal (N:M resuelta)
-- ============================================================
CREATE TABLE rw_channel_member (
    channel_id uuid        NOT NULL REFERENCES rw_channel (id) ON DELETE CASCADE, -- la membresia no existe sin el canal
    user_id    uuid        NOT NULL REFERENCES rw_user (id)   ON DELETE CASCADE, -- la membresia no existe sin el usuario
    role       text        NOT NULL DEFAULT 'member',
    joined_at  timestamptz NOT NULL DEFAULT now(),
    -- PK natural compuesta: un usuario aparece una sola vez por canal
    CONSTRAINT pk_rw_channel_member PRIMARY KEY (channel_id, user_id),
    CONSTRAINT ck_rw_channel_member_role CHECK (role IN ('owner', 'admin', 'member'))
);

-- para responder rapido "en que canales esta este usuario" (vista de conversaciones, RLS)
CREATE INDEX ix_rw_channel_member_user ON rw_channel_member (user_id);


-- ============================================================
-- rw_message: mensajes (soft delete siempre, nunca borrado fisico)
-- ============================================================
CREATE TABLE rw_message (
    id           uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    channel_id   uuid        NOT NULL REFERENCES rw_channel (id) ON DELETE CASCADE, -- los mensajes siguen el ciclo de vida del canal
    sender_id    uuid        NOT NULL REFERENCES rw_user (id)   ON DELETE RESTRICT, -- se conserva la autoria; el usuario se soft-borra
    body         text        NOT NULL,
    -- estado de entrega desde la perspectiva del emisor (requisito de mensajeria en tiempo real)
    status       text        NOT NULL DEFAULT 'sent',
    -- nonce del cliente para deduplicar reenvios del mismo mensaje en tiempo real
    client_nonce uuid,
    created_at   timestamptz NOT NULL DEFAULT now(),
    edited_at    timestamptz,
    deleted_at   timestamptz,
    deleted_by   uuid        REFERENCES rw_user (id) ON DELETE SET NULL, -- quien borro es informativo, no debe bloquear
    -- columna de texto para busqueda full-text; la mantiene sincronizada un trigger (V4)
    search_tsv   tsvector,
    -- embedding para RAG (text-embedding-3-small = 1536 dimensiones); lo llena el backend
    embedding    vector(1536),
    CONSTRAINT ck_rw_message_body_len CHECK (char_length(body) BETWEEN 1 AND 8000),
    CONSTRAINT ck_rw_message_status   CHECK (status IN ('pending', 'sent', 'failed')),
    CONSTRAINT ck_rw_message_edited_after_created  CHECK (edited_at  IS NULL OR edited_at  >= created_at),
    CONSTRAINT ck_rw_message_deleted_after_created CHECK (deleted_at IS NULL OR deleted_at >= created_at),
    -- coherencia del soft delete: o estan los dos campos o ninguno
    CONSTRAINT ck_rw_message_soft_delete_pair CHECK ((deleted_at IS NULL) = (deleted_by IS NULL))
);

-- deduplicacion de reenvios: un nonce por emisor, solo cuando viene informado
CREATE UNIQUE INDEX ux_rw_message_sender_nonce
    ON rw_message (sender_id, client_nonce)
    WHERE client_nonce IS NOT NULL;

-- indice de keyset para el historial del canal: orden (created_at desc, id desc) sobre mensajes vivos
CREATE INDEX ix_rw_message_channel_keyset
    ON rw_message (channel_id, created_at DESC, id DESC)
    WHERE deleted_at IS NULL;

-- busqueda full-text
CREATE INDEX ix_rw_message_search_tsv ON rw_message USING gin (search_tsv);

-- busqueda vectorial por similitud coseno para el RAG
CREATE INDEX ix_rw_message_embedding ON rw_message USING hnsw (embedding vector_cosine_ops);


-- ============================================================
-- rw_message_read_receipt: estado de lectura por usuario y mensaje
-- ============================================================
CREATE TABLE rw_message_read_receipt (
    message_id uuid        NOT NULL REFERENCES rw_message (id) ON DELETE CASCADE, -- dato derivado: se va con el mensaje
    user_id    uuid        NOT NULL REFERENCES rw_user (id)   ON DELETE CASCADE, -- dato derivado: se va con el usuario
    read_at    timestamptz NOT NULL DEFAULT now(),
    -- PK compuesta: un usuario marca un mensaje como leido una sola vez
    CONSTRAINT pk_rw_message_read_receipt PRIMARY KEY (message_id, user_id)
);

CREATE INDEX ix_rw_message_read_receipt_user ON rw_message_read_receipt (user_id);


-- ============================================================
-- rw_copilot_query: bitacora de consultas al copiloto de IA
-- ============================================================
CREATE TABLE rw_copilot_query (
    id                uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           uuid        NOT NULL REFERENCES rw_user (id) ON DELETE CASCADE, -- historial privado del usuario
    question          text        NOT NULL,
    answer            text,
    model             text        NOT NULL,
    prompt_tokens     integer     NOT NULL DEFAULT 0,
    completion_tokens integer     NOT NULL DEFAULT 0,
    -- total derivado: evita inconsistencias al sumar consumo (Consulta 4)
    total_tokens      integer     GENERATED ALWAYS AS (prompt_tokens + completion_tokens) STORED,
    status            text        NOT NULL DEFAULT 'answered',
    created_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_rw_copilot_query_prompt_tokens     CHECK (prompt_tokens >= 0),
    CONSTRAINT ck_rw_copilot_query_completion_tokens CHECK (completion_tokens >= 0),
    CONSTRAINT ck_rw_copilot_query_question_len CHECK (char_length(btrim(question)) BETWEEN 1 AND 4000),
    CONSTRAINT ck_rw_copilot_query_status CHECK (status IN ('answered', 'refused_no_context', 'refused_permission', 'error'))
);

CREATE INDEX ix_rw_copilot_query_user ON rw_copilot_query (user_id, created_at DESC);


-- ============================================================
-- rw_copilot_citation: mensajes citados como fuente en una respuesta del copiloto
-- ============================================================
CREATE TABLE rw_copilot_citation (
    query_id   uuid        NOT NULL REFERENCES rw_copilot_query (id) ON DELETE CASCADE, -- la cita nace de la consulta
    message_id uuid        NOT NULL REFERENCES rw_message (id)       ON DELETE RESTRICT, -- la cita debe apuntar a un mensaje real (los mensajes se soft-borran)
    rank       integer     NOT NULL DEFAULT 1,
    created_at timestamptz NOT NULL DEFAULT now(),
    -- PK compuesta: un mensaje se cita una vez por respuesta
    CONSTRAINT pk_rw_copilot_citation PRIMARY KEY (query_id, message_id),
    CONSTRAINT ck_rw_copilot_citation_rank CHECK (rank >= 1)
);


-- ============================================================
-- rw_refresh_token: refresh tokens con rotacion (se guarda solo el hash)
-- ============================================================
CREATE TABLE rw_refresh_token (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     uuid        NOT NULL REFERENCES rw_user (id) ON DELETE CASCADE, -- estado de sesion: se va con el usuario
    token_hash  text        NOT NULL UNIQUE,
    issued_at   timestamptz NOT NULL DEFAULT now(),
    expires_at  timestamptz NOT NULL,
    revoked_at  timestamptz,
    replaced_by uuid        REFERENCES rw_refresh_token (id) ON DELETE SET NULL, -- enlace de la cadena de rotacion, solo informativo
    CONSTRAINT ck_rw_refresh_token_expiry CHECK (expires_at > issued_at),
    CONSTRAINT ck_rw_refresh_token_revoked_after_issued CHECK (revoked_at IS NULL OR revoked_at >= issued_at)
);

-- un usuario suele consultar sus tokens vigentes: indice parcial sobre los no revocados
CREATE INDEX ix_rw_refresh_token_user_active
    ON rw_refresh_token (user_id)
    WHERE revoked_at IS NULL;
