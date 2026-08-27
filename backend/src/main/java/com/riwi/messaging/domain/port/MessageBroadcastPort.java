package com.riwi.messaging.domain.port;

import com.riwi.messaging.domain.model.MessageBroadcast;

// puerto de salida de tiempo real; el adaptador WebSocket vive en infrastructure
public interface MessageBroadcastPort {

    // emite el evento solo a los miembros del canal que esten conectados
    void broadcast(MessageBroadcast event);
}
