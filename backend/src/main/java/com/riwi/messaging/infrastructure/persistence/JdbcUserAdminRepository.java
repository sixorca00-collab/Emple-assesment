package com.riwi.messaging.infrastructure.persistence;

import com.riwi.messaging.domain.model.UserPage;
import com.riwi.messaging.domain.model.UserSearchCursor;
import com.riwi.messaging.domain.model.UserSummary;
import com.riwi.messaging.domain.port.UserAdminRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// adaptador JdbcTemplate del puerto de gestion de usuarios; invoca los SP con SQL parametrizado
@Repository
public class JdbcUserAdminRepository implements UserAdminRepository {

    // SP (a): keyset por (lower(display_name), user_id); los CAST evitan ambiguedad de tipo con parametros nulos
    private static final String SEARCH_SQL = """
            SELECT user_id, display_name, job_title, avatar_url, is_active, created_at
            FROM rw_search_users(
                CAST(:q AS text),
                CAST(:afterName AS text),
                CAST(:afterId AS uuid),
                CAST(:limit AS integer),
                CAST(:includeInactive AS boolean))
            """;

    // SP (b.1): edicion; los parametros nulos dejan el valor actual dentro del propio SP
    private static final String UPDATE_SQL = """
            CALL rw_update_user(
                CAST(:target AS uuid),
                CAST(:displayName AS text),
                CAST(:jobTitle AS text),
                CAST(:avatarUrl AS text),
                CAST(:bio AS text),
                CAST(:active AS boolean))
            """;

    // SP (b.2): eliminacion (soft delete)
    private static final String DELETE_SQL = "CALL rw_delete_user(CAST(:target AS uuid))";

    // relectura del usuario vigente para el cuerpo del PATCH
    private static final String FIND_BY_ID_SQL = """
            SELECT u.id AS user_id, p.display_name, p.job_title, p.avatar_url, u.is_active, u.created_at
            FROM rw_user u
            JOIN rw_user_profile p ON p.user_id = u.id
            WHERE u.id = :id AND u.deleted_at IS NULL
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcUserAdminRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UserPage search(String query, UserSearchCursor after, int limit, boolean includeInactive) {
        var params = new MapSqlParameterSource()
                .addValue("q", blankToNull(query))
                .addValue("afterName", after == null ? null : after.displayName())
                .addValue("afterId", after == null ? null : after.id())
                // pedimos una fila extra para detectar la pagina siguiente sin usar OFFSET
                .addValue("limit", limit + 1)
                .addValue("includeInactive", includeInactive);

        // llamamos al SP de consulta de usuarios; la RLS no aplica a rw_user pero el SP filtra por rol
        List<UserSummary> rows = DbFunctionErrors.mapping(() -> jdbc.query(SEARCH_SQL, params, userMapper()));

        boolean hasMore = rows.size() > limit;
        List<UserSummary> items = hasMore ? new ArrayList<>(rows.subList(0, limit)) : rows;
        UserSearchCursor next = hasMore
                ? new UserSearchCursor(items.get(items.size() - 1).displayName(), items.get(items.size() - 1).id())
                : null;
        return new UserPage(items, next);
    }

    @Override
    public void update(UUID targetId, String displayName, String jobTitle, String avatarUrl, String bio, Boolean active) {
        var params = new MapSqlParameterSource()
                .addValue("target", targetId)
                .addValue("displayName", displayName)
                .addValue("jobTitle", jobTitle)
                .addValue("avatarUrl", avatarUrl)
                .addValue("bio", bio)
                .addValue("active", active);

        // llamamos al SP de edicion de usuario; traduce 42501 -> 403 y P0002 -> 404
        DbFunctionErrors.mapping(() -> jdbc.update(UPDATE_SQL, params));
    }

    @Override
    public void delete(UUID targetId) {
        var params = new MapSqlParameterSource().addValue("target", targetId);
        // llamamos al SP de eliminacion de usuario (soft delete, nunca borrado fisico)
        DbFunctionErrors.mapping(() -> jdbc.update(DELETE_SQL, params));
    }

    @Override
    public Optional<UserSummary> findById(UUID userId) {
        var params = new MapSqlParameterSource().addValue("id", userId);
        // relectura directa de rw_user + rw_user_profile por id
        return jdbc.query(FIND_BY_ID_SQL, params, userMapper()).stream().findFirst();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private RowMapper<UserSummary> userMapper() {
        return (ResultSet rs, int rowNum) -> new UserSummary(
                rs.getObject("user_id", UUID.class),
                rs.getString("display_name"),
                rs.getString("job_title"),
                rs.getString("avatar_url"),
                rs.getBoolean("is_active"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }
}
