package com.riwi.messaging.domain.port;

import com.riwi.messaging.domain.model.ChannelView;
import com.riwi.messaging.domain.model.ConversationPage;
import com.riwi.messaging.domain.model.Cursor;
import com.riwi.messaging.domain.model.NewChannel;

import java.util.UUID;

// puerto de persistencia de canales y membresias; el adaptador concreto vive en infrastructure
public interface ChannelRepository {

    // conversaciones del actor desde la vista rw_user_conversation, con keyset pagination
    ConversationPage listConversations(Cursor after, int limit);

    // crea el canal y deja al actor como owner en la misma transaccion; devuelve el canal creado
    ChannelView createWithOwner(NewChannel channel);

    // agrega un miembro; solo si el actor es owner/admin del canal
    void addMember(UUID channelId, UUID userId, String role);
}
