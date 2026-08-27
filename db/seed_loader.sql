-- seed_loader.sql
-- Carga db/seed.json en la base. NO es una migracion Flyway: es utilitario de datos.
-- Uso (desde la raiz del repo):
--   psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f db/seed_loader.sql
-- Ruta alternativa del corpus:
--   psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -v seed_path=/ruta/seed.json -f db/seed_loader.sql

\if :{?seed_path}
\else
  \set seed_path db/seed.json
\endif

-- leemos el archivo a una variable; psql escapa el contenido de forma segura al usar :'...'
\set seed_content `cat :seed_path`


-- ============================================================
-- Funcion de carga: idempotente (trunca y recarga)
-- ============================================================
CREATE OR REPLACE FUNCTION rw_load_seed(
    p_seed                 jsonb,
    p_synthetic_embeddings boolean DEFAULT true
)
RETURNS jsonb
LANGUAGE plpgsql
AS $$
DECLARE
    v_rec        jsonb;
    v_cite       jsonb;
    v_rank       integer;
    v_embedding  vector(1536);
    v_counts     jsonb;
BEGIN
    -- recarga limpia: CASCADE arrastra las tablas hijas
    TRUNCATE rw_user RESTART IDENTITY CASCADE;

    -- ---- usuarios + perfiles (1:1) ----
    FOR v_rec IN SELECT * FROM jsonb_array_elements(p_seed -> 'users')
    LOOP
        INSERT INTO rw_user (id, email, password_hash, is_platform_admin, is_active)
        VALUES (
            (v_rec ->> 'id')::uuid,
            v_rec ->> 'email',
            v_rec ->> 'password_hash',
            coalesce((v_rec ->> 'is_platform_admin')::boolean, false),
            coalesce((v_rec ->> 'is_active')::boolean, true)
        );

        INSERT INTO rw_user_profile (user_id, display_name, job_title, avatar_url, bio)
        VALUES (
            (v_rec ->> 'id')::uuid,
            v_rec -> 'profile' ->> 'display_name',
            v_rec -> 'profile' ->> 'job_title',
            v_rec -> 'profile' ->> 'avatar_url',
            v_rec -> 'profile' ->> 'bio'
        );
    END LOOP;

    -- ---- canales (RLS de INSERT: created_by = actor) ----
    FOR v_rec IN SELECT * FROM jsonb_array_elements(p_seed -> 'channels')
    LOOP
        -- fijamos el actor al creador para cumplir la politica RLS de insercion
        PERFORM rw_set_current_user((v_rec ->> 'created_by')::uuid);

        INSERT INTO rw_channel (id, name, description, is_private, created_by)
        VALUES (
            (v_rec ->> 'id')::uuid,
            v_rec ->> 'name',
            v_rec ->> 'description',
            coalesce((v_rec ->> 'is_private')::boolean, false),
            (v_rec ->> 'created_by')::uuid
        );
    END LOOP;

    -- ---- membresias (tabla puente, sin RLS) ----
    FOR v_rec IN SELECT * FROM jsonb_array_elements(p_seed -> 'memberships')
    LOOP
        INSERT INTO rw_channel_member (channel_id, user_id, role)
        VALUES (
            (v_rec ->> 'channel_id')::uuid,
            (v_rec ->> 'user_id')::uuid,
            coalesce(v_rec ->> 'role', 'member')
        );
    END LOOP;

    -- ---- mensajes (RLS de INSERT: sender_id = actor y actor es miembro) ----
    FOR v_rec IN SELECT * FROM jsonb_array_elements(p_seed -> 'messages')
    LOOP
        PERFORM rw_set_current_user((v_rec ->> 'sender_id')::uuid);

        -- embedding sintetico para poder probar la Consulta 3 sin llamar al proveedor de IA
        IF p_synthetic_embeddings THEN
            SELECT ('[' || string_agg(round(random()::numeric, 6)::text, ',') || ']')::vector(1536)
            INTO v_embedding
            FROM generate_series(1, 1536);
        ELSE
            v_embedding := NULL;
        END IF;

        INSERT INTO rw_message (
            id, channel_id, sender_id, body, status,
            created_at, edited_at, deleted_at, deleted_by, embedding
        )
        VALUES (
            (v_rec ->> 'id')::uuid,
            (v_rec ->> 'channel_id')::uuid,
            (v_rec ->> 'sender_id')::uuid,
            v_rec ->> 'body',
            coalesce(v_rec ->> 'status', 'sent'),
            (v_rec ->> 'created_at')::timestamptz,
            (v_rec ->> 'edited_at')::timestamptz,
            (v_rec ->> 'deleted_at')::timestamptz,
            (v_rec ->> 'deleted_by')::uuid,
            v_embedding
        );
    END LOOP;

    -- ---- acuses de lectura ----
    FOR v_rec IN SELECT * FROM jsonb_array_elements(p_seed -> 'read_receipts')
    LOOP
        INSERT INTO rw_message_read_receipt (message_id, user_id, read_at)
        VALUES (
            (v_rec ->> 'message_id')::uuid,
            (v_rec ->> 'user_id')::uuid,
            (v_rec ->> 'read_at')::timestamptz
        );
    END LOOP;

    -- ---- consultas al copiloto + citas ----
    FOR v_rec IN SELECT * FROM jsonb_array_elements(p_seed -> 'copilot_queries')
    LOOP
        INSERT INTO rw_copilot_query (
            id, user_id, question, answer, model,
            prompt_tokens, completion_tokens, status, created_at
        )
        VALUES (
            (v_rec ->> 'id')::uuid,
            (v_rec ->> 'user_id')::uuid,
            v_rec ->> 'question',
            v_rec ->> 'answer',
            v_rec ->> 'model',
            coalesce((v_rec ->> 'prompt_tokens')::integer, 0),
            coalesce((v_rec ->> 'completion_tokens')::integer, 0),
            coalesce(v_rec ->> 'status', 'answered'),
            (v_rec ->> 'created_at')::timestamptz
        );

        v_rank := 0;
        FOR v_cite IN SELECT * FROM jsonb_array_elements(coalesce(v_rec -> 'citations', '[]'::jsonb))
        LOOP
            v_rank := v_rank + 1;
            INSERT INTO rw_copilot_citation (query_id, message_id, rank)
            VALUES ((v_rec ->> 'id')::uuid, (v_cite #>> '{}')::uuid, v_rank);
        END LOOP;
    END LOOP;

    -- limpiamos el actor de la transaccion
    PERFORM set_config('app.current_user_id', '', true);

    SELECT jsonb_build_object(
        'users',           (SELECT count(*) FROM rw_user),
        'channels',        (SELECT count(*) FROM rw_channel),
        'memberships',     (SELECT count(*) FROM rw_channel_member),
        'messages',        (SELECT count(*) FROM rw_message),
        'read_receipts',   (SELECT count(*) FROM rw_message_read_receipt),
        'copilot_queries', (SELECT count(*) FROM rw_copilot_query),
        'citations',       (SELECT count(*) FROM rw_copilot_citation)
    ) INTO v_counts;

    RETURN v_counts;
END;
$$;


-- ============================================================
-- Ejecucion: todo dentro de una transaccion
-- ============================================================
BEGIN;
SELECT rw_load_seed(:'seed_content'::jsonb, true) AS loaded_rows;
COMMIT;
