package com.riwi.messaging.domain.port;

import com.riwi.messaging.domain.model.SearchCursor;
import com.riwi.messaging.domain.model.SearchResultPage;

import java.util.UUID;

// puerto de busqueda full-text de mensajes (Consulta 2); el alcance de permisos lo aplica la RLS en SQL
public interface MessageSearchRepository {

    // busca sobre search_tsv por relevancia; channelId opcional restringe a un canal
    SearchResultPage search(String query, UUID channelId, SearchCursor after, int limit);
}
