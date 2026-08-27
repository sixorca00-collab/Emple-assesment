package com.riwi.messaging.domain.port;

// puerto de lectura de conversaciones del actor; se apoya en la vista rw_user_conversation (RLS)
public interface ConversationRepository {

    // cuenta las conversaciones visibles para el actor fijado en la transaccion actual
    long countForCurrentActor();
}
