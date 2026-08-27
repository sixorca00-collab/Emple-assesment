package com.riwi.messaging.rls;

import com.riwi.messaging.support.AbstractRlsIT;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Test obligatorio 1: un usuario que NO es miembro de un canal privado es rechazado
class NonMemberRejectionIT extends AbstractRlsIT {

    // Diego Torres: contratista externo, NO es miembro de product-planning
    private static final UUID DIEGO = UUID.fromString("11111111-1111-1111-1111-000000000007");
    private static final UUID PRODUCT_PLANNING = UUID.fromString("22222222-2222-2222-2222-000000000003");

    @Test
    void nonMemberCannotReadMessagesOfPrivateChannel() {
        // consulta directa del historial: la RLS de rw_message no devuelve ninguna fila
        List<Map<String, Object>> rows = asActor(DIEGO, jdbc ->
                jdbc.queryForList("SELECT id FROM rw_message WHERE channel_id = ?", PRODUCT_PLANNING));

        assertThat(rows).isEmpty();
    }

    @Test
    void nonMemberCannotReadPrivateChannelHistoryViaKeysetQuery() {
        // la Consulta 1 (historial con keyset) tampoco filtra nada para un no-miembro
        List<Map<String, Object>> rows = asActor(DIEGO, jdbc -> jdbc.queryForList(
                """
                SELECT m.id
                FROM rw_message m
                WHERE m.channel_id = ?
                  AND m.deleted_at IS NULL
                ORDER BY m.created_at DESC, m.id DESC
                LIMIT 50
                """, PRODUCT_PLANNING));

        assertThat(rows).isEmpty();
    }

    @Test
    void nonMemberCannotPostToPrivateChannel() {
        // la funcion transaccional valida la membresia en la BD y lanza excepcion (SQLSTATE 42501)
        assertThatThrownBy(() -> asActor(DIEGO, jdbc ->
                jdbc.query("SELECT rw_post_message(?, ?, NULL::uuid)", resultSet -> null, PRODUCT_PLANNING, "intruso")))
                .isInstanceOf(DataAccessException.class)
                .hasStackTraceContaining("is not a member of channel");
    }

    @Test
    void nonMemberCannotMarkPrivateChannelAsRead() {
        assertThatThrownBy(() -> asActor(DIEGO, jdbc ->
                jdbc.query("SELECT rw_mark_channel_read(?)", resultSet -> null, PRODUCT_PLANNING)))
                .isInstanceOf(DataAccessException.class)
                .hasStackTraceContaining("is not a member of channel");
    }
}
