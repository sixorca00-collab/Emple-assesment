package com.riwi.messaging.infrastructure.persistence;

import com.riwi.messaging.domain.model.SearchCursor;
import com.riwi.messaging.domain.model.SearchHit;
import com.riwi.messaging.domain.model.SearchResultPage;
import com.riwi.messaging.domain.port.MessageSearchRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// adaptador JdbcTemplate de la Consulta 2: busqueda full-text con ts_headline y keyset por relevancia
@Repository
public class JdbcMessageSearchRepository implements MessageSearchRepository {

    // opciones de ts_headline: resalta el termino con <mark>...</mark>, igual que db/queries/02
    private static final String HEADLINE_OPTIONS =
            "StartSel=<mark>, StopSel=</mark>, MaxFragments=2, MinWords=3, MaxWords=18, FragmentDelimiter= ... ";

    // websearch_to_tsquery convierte el texto libre en tsquery sin construir SQL; keyset sobre (rank DESC, id DESC)
    private static final String SEARCH_SQL = """
            WITH q AS (
                SELECT websearch_to_tsquery(CAST(:lang AS regconfig), :q) AS tsq
            )
            SELECT
                m.id,
                m.channel_id,
                c.name AS channel_name,
                m.sender_id,
                p.display_name AS sender_name,
                ts_headline(CAST(:lang AS regconfig), m.body, q.tsq, :headlineOptions) AS snippet,
                ts_rank(m.search_tsv, q.tsq) AS rank,
                m.created_at
            FROM rw_message m
            JOIN rw_channel c ON c.id = m.channel_id
            JOIN rw_user_profile p ON p.user_id = m.sender_id
            CROSS JOIN q
            WHERE m.deleted_at IS NULL
              AND m.search_tsv @@ q.tsq
              AND (CAST(:channelId AS uuid) IS NULL OR m.channel_id = CAST(:channelId AS uuid))
              AND (
                    CAST(:afterRank AS real) IS NULL
                    OR (ts_rank(m.search_tsv, q.tsq), m.id) < (CAST(:afterRank AS real), CAST(:afterId AS uuid))
                  )
            ORDER BY rank DESC, m.id DESC
            LIMIT :limit
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final String textConfig;

    public JdbcMessageSearchRepository(NamedParameterJdbcTemplate jdbc,
                                       @Value("${riwi.search.text-config:spanish}") String textConfig) {
        this.jdbc = jdbc;
        this.textConfig = textConfig;
    }

    @Override
    public SearchResultPage search(String query, UUID channelId, SearchCursor after, int limit) {
        var params = new MapSqlParameterSource()
                .addValue("lang", textConfig)
                .addValue("q", query)
                .addValue("headlineOptions", HEADLINE_OPTIONS)
                .addValue("channelId", channelId)
                .addValue("afterRank", after == null ? null : after.rank())
                .addValue("afterId", after == null ? null : after.id())
                // una fila extra para saber si hay pagina siguiente sin usar OFFSET
                .addValue("limit", limit + 1);

        // la RLS p_rw_message_select filtra primero por membresia: la busqueda no ve canales ajenos
        List<SearchHit> rows = jdbc.query(SEARCH_SQL, params, hitMapper());

        boolean hasMore = rows.size() > limit;
        List<SearchHit> items = hasMore ? new ArrayList<>(rows.subList(0, limit)) : rows;
        // armamos el cursor con el (rank, id) del ultimo hit devuelto
        SearchCursor next = hasMore
                ? new SearchCursor(items.get(items.size() - 1).rank(), items.get(items.size() - 1).id())
                : null;
        return new SearchResultPage(items, next);
    }

    private RowMapper<SearchHit> hitMapper() {
        return (ResultSet rs, int rowNum) -> new SearchHit(
                rs.getObject("id", UUID.class),
                rs.getObject("channel_id", UUID.class),
                rs.getString("channel_name"),
                rs.getObject("sender_id", UUID.class),
                rs.getString("sender_name"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getString("snippet"),
                rs.getDouble("rank"));
    }
}
