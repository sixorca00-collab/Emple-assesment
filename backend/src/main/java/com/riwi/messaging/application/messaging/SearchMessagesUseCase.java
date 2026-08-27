package com.riwi.messaging.application.messaging;

import com.riwi.messaging.domain.exception.InvalidInputException;
import com.riwi.messaging.domain.model.SearchResultPage;
import com.riwi.messaging.domain.port.MessageSearchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// caso de uso: busqueda de mensajes con resaltado del termino (Consulta 2)
@Service
public class SearchMessagesUseCase {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final MessageSearchRepository search;

    public SearchMessagesUseCase(MessageSearchRepository search) {
        this.search = search;
    }

    @Transactional(readOnly = true)
    public SearchResultPage execute(SearchMessagesCommand command) {
        // validamos el termino: vacio o solo espacios no es una busqueda valida
        String query = command.q() == null ? "" : command.q().strip();
        if (query.isEmpty()) {
            throw new InvalidInputException("q must not be blank");
        }

        // acotamos el tamano de pagina
        int limit = clampPageSize(command.size());

        // la RLS de rw_message limita los resultados a canales donde el actor es miembro
        return search.search(query, command.channelId(), command.cursor(), limit);
    }

    private int clampPageSize(Integer requested) {
        if (requested == null) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.max(1, Math.min(requested, MAX_PAGE_SIZE));
    }
}
