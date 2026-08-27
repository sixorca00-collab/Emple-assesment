package com.riwi.messaging.rls;

import com.riwi.messaging.support.AbstractRlsIT;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Test obligatorio 2: al listar/buscar mensajes, no se filtra NINGUN mensaje de canales privados ajenos
class PrivateChannelLeakIT extends AbstractRlsIT {

    // Sebastian Marin: miembro de general, engineering y random; NO de los canales privados de abajo
    private static final UUID SEBASTIAN = UUID.fromString("11111111-1111-1111-1111-000000000005");
    private static final UUID ENGINEERING = UUID.fromString("22222222-2222-2222-2222-000000000002");
    private static final UUID PRODUCT_PLANNING = UUID.fromString("22222222-2222-2222-2222-000000000003");
    private static final UUID HR_CONFIDENTIAL = UUID.fromString("22222222-2222-2222-2222-000000000004");
    private static final UUID CONTRACTOR_ONBOARDING = UUID.fromString("22222222-2222-2222-2222-000000000006");

    private static final Set<UUID> FOREIGN_PRIVATE_CHANNELS =
            Set.of(PRODUCT_PLANNING, HR_CONFIDENTIAL, CONTRACTOR_ONBOARDING);

    @Test
    void listingMessagesNeverIncludesForeignPrivateChannels() {
        List<UUID> visibleChannelIds = asActor(SEBASTIAN, jdbc ->
                jdbc.queryForList("SELECT DISTINCT channel_id FROM rw_message", UUID.class));

        assertThat(visibleChannelIds).isNotEmpty();
        assertThat(visibleChannelIds).doesNotContainAnyElementsOf(FOREIGN_PRIVATE_CHANNELS);
    }

    @Test
    void fullTextSearchDoesNotLeakForeignPrivateMessages() {
        // "presupuesto" solo aparece en product-planning (privado, ajeno a Sebastian)
        List<Map<String, Object>> results = asActor(SEBASTIAN, jdbc -> jdbc.queryForList(
                """
                SELECT m.id, m.channel_id
                FROM rw_message m
                WHERE m.deleted_at IS NULL
                  AND m.search_tsv @@ websearch_to_tsquery('spanish', ?)
                """, "presupuesto"));

        assertThat(results).isEmpty();
    }

    @Test
    void fullTextSearchStillReturnsMessagesFromChannelsTheActorBelongsTo() {
        // "septiembre" aparece en engineering (visible) y en canales privados ajenos (no visibles)
        List<UUID> channelIds = asActor(SEBASTIAN, jdbc -> jdbc.queryForList(
                """
                SELECT m.channel_id
                FROM rw_message m
                WHERE m.deleted_at IS NULL
                  AND m.search_tsv @@ websearch_to_tsquery('spanish', ?)
                """, UUID.class, "septiembre"));

        assertThat(channelIds).isNotEmpty();
        assertThat(channelIds).containsOnly(ENGINEERING);
    }
}
