package com.riwi.messaging.infrastructure.persistence;

import com.riwi.messaging.domain.model.ActorProfile;
import com.riwi.messaging.domain.model.User;
import com.riwi.messaging.domain.port.UserRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

// adaptador JdbcTemplate del puerto de usuarios; todo el SQL va parametrizado
@Repository
public class JdbcUserRepository implements UserRepository {

    private static final String USER_COLUMNS = "id, email, password_hash, is_platform_admin, is_active, deleted_at";

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcUserRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UUID create(String email, String passwordHash, String displayName, String jobTitle) {
        // insertamos las credenciales; is_platform_admin/is_active toman su default (false/true)
        String insertUser = """
                INSERT INTO rw_user (email, password_hash, is_platform_admin, is_active)
                VALUES (:email, :passwordHash, false, true)
                RETURNING id
                """;
        var userParams = new MapSqlParameterSource()
                .addValue("email", email)
                .addValue("passwordHash", passwordHash);
        UUID userId = jdbc.queryForObject(insertUser, userParams, UUID.class);

        // perfil 1:1 en la misma transaccion
        String insertProfile = """
                INSERT INTO rw_user_profile (user_id, display_name, job_title)
                VALUES (:userId, :displayName, :jobTitle)
                """;
        var profileParams = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("displayName", displayName)
                .addValue("jobTitle", jobTitle);
        jdbc.update(insertProfile, profileParams);

        return userId;
    }

    @Override
    public Optional<User> findByEmail(String email) {
        // buscamos al usuario vigente por correo sin distinguir mayusculas
        String sql = "SELECT " + USER_COLUMNS + " FROM rw_user WHERE lower(email) = lower(:email) AND deleted_at IS NULL";
        var params = new MapSqlParameterSource("email", email);
        return jdbc.query(sql, params, userMapper()).stream().findFirst();
    }

    @Override
    public Optional<User> findById(UUID userId) {
        // recuperamos al usuario por id para revalidar su estado en el refresh
        String sql = "SELECT " + USER_COLUMNS + " FROM rw_user WHERE id = :id";
        var params = new MapSqlParameterSource("id", userId);
        return jdbc.query(sql, params, userMapper()).stream().findFirst();
    }

    @Override
    public Optional<ActorProfile> findProfileById(UUID userId) {
        // perfil visible del actor: une credenciales y perfil 1:1
        String sql = """
                SELECT u.id, u.email, p.display_name, p.job_title, u.is_platform_admin
                FROM rw_user u
                JOIN rw_user_profile p ON p.user_id = u.id
                WHERE u.id = :id AND u.deleted_at IS NULL
                """;
        var params = new MapSqlParameterSource("id", userId);
        return jdbc.query(sql, params, profileMapper()).stream().findFirst();
    }

    private RowMapper<User> userMapper() {
        return (ResultSet rs, int rowNum) -> {
            OffsetDateTime deletedAt = rs.getObject("deleted_at", OffsetDateTime.class);
            return new User(
                    rs.getObject("id", UUID.class),
                    rs.getString("email"),
                    rs.getString("password_hash"),
                    rs.getBoolean("is_platform_admin"),
                    rs.getBoolean("is_active"),
                    deletedAt == null ? null : deletedAt.toInstant());
        };
    }

    private RowMapper<ActorProfile> profileMapper() {
        return (ResultSet rs, int rowNum) -> new ActorProfile(
                rs.getObject("id", UUID.class),
                rs.getString("email"),
                rs.getString("display_name"),
                rs.getString("job_title"),
                rs.getBoolean("is_platform_admin"));
    }
}
