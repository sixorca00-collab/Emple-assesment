-- V4__triggers.sql
-- Triggers de consistencia:
--  1) mantienen search_tsv sincronizado ante cambios de mensajes e invalidan el embedding
--  2) impiden el borrado fisico de mensajes
--  3) refrescan updated_at

SET TIME ZONE 'UTC';


-- ============================================================
-- Trigger: sincroniza el vector de busqueda del mensaje
-- ============================================================
CREATE OR REPLACE FUNCTION rw_message_search_sync()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    -- recalculamos solo si es alta o si cambio el texto
    IF TG_OP = 'INSERT' OR NEW.body IS DISTINCT FROM OLD.body THEN
        -- configuracion 'spanish' para stemming/stopwords del corpus interno
        NEW.search_tsv := to_tsvector('spanish', coalesce(NEW.body, ''));

        -- si el texto cambio, el embedding viejo deja de ser valido: lo invalidamos para que el backend lo recalcule
        IF TG_OP = 'UPDATE' THEN
            NEW.embedding := NULL;
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

-- BEFORE => escribimos search_tsv en la misma fila sin un UPDATE extra
CREATE TRIGGER trg_rw_message_search_sync
    BEFORE INSERT OR UPDATE OF body ON rw_message
    FOR EACH ROW
    EXECUTE FUNCTION rw_message_search_sync();


-- ============================================================
-- Trigger: prohibe el borrado fisico de mensajes (solo soft delete)
-- ============================================================
CREATE OR REPLACE FUNCTION rw_message_block_hard_delete()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'hard delete of rw_message is not allowed; use rw_soft_delete_message()' USING errcode = '0A000';
    RETURN NULL;
END;
$$;

CREATE TRIGGER trg_rw_message_block_hard_delete
    BEFORE DELETE ON rw_message
    FOR EACH ROW
    EXECUTE FUNCTION rw_message_block_hard_delete();


-- ============================================================
-- Trigger: refresca updated_at en tablas que lo tienen
-- ============================================================
CREATE OR REPLACE FUNCTION rw_touch_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_rw_user_touch
    BEFORE UPDATE ON rw_user
    FOR EACH ROW
    EXECUTE FUNCTION rw_touch_updated_at();

CREATE TRIGGER trg_rw_user_profile_touch
    BEFORE UPDATE ON rw_user_profile
    FOR EACH ROW
    EXECUTE FUNCTION rw_touch_updated_at();

CREATE TRIGGER trg_rw_channel_touch
    BEFORE UPDATE ON rw_channel
    FOR EACH ROW
    EXECUTE FUNCTION rw_touch_updated_at();
