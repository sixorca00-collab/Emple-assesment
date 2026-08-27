package com.riwi.messaging.domain.model;

import java.util.List;

// pagina de resultados de busqueda: los hits y el cursor para pedir la siguiente (null si no hay mas)
public record SearchResultPage(
        List<SearchHit> items,
        SearchCursor nextCursor
) {
}
