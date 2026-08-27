package com.riwi.messaging.domain.port;

import com.riwi.messaging.domain.model.Cursor;
import com.riwi.messaging.domain.model.MessagePage;
import com.riwi.messaging.domain.model.MessageView;

import java.util.UUID;

// puerto de persistencia de mensajes; se apoya en las funciones transaccionales de la BD
public interface MessageRepository {

    // historial del canal, del mas nuevo al mas viejo, con keyset pagination (Consulta 1)
    MessagePage history(UUID channelId, Cursor before, int limit);

    // publica via rw_post_message; idempotente por clientNonce
    MessageView post(UUID channelId, String body, UUID clientNonce);

    // edita via rw_edit_message; solo el autor
    MessageView edit(UUID messageId, String newBody);

    // soft delete via rw_soft_delete_message; autor o admin del canal
    void softDelete(UUID messageId);

    // marca leidos los mensajes ajenos vivos del canal via rw_mark_channel_read; devuelve cuantos
    int markChannelRead(UUID channelId);
}
