package com.riwi.messaging.infrastructure.persistence;

import com.riwi.messaging.domain.model.RefreshToken;
import com.riwi.messaging.domain.port.RefreshTokenRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

// adaptador JdbcTemplate del puerto de refresh tokens; nunca hace DELETE (rol riwi_app sin ese permiso)
@Repository
public class JdbcRefreshTokenRepository implements RefreshTokenRepository {

    private static final String COLUMNS = "id, user_id, token_hash, issued_at, expires_at, revoked_at, replaced_by";

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcRefreshTokenRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(RefreshToken token) {
        // insertamos el registro con el hash del token (nunca el valor en claro)
        String sql = """
                INSERT INTO rw_refresh_token (id, user_id, token_hash, issued_at, expires_at)
                VALUES (:id, :userId, :tokenHash, :issuedAt, :expiresAt)
                """;
        var params = new MapSqlParameterSource()
                .addValue("id", token.id())
                .addValue("userId", token.userId())
                .addValue("tokenHash", token.tokenHash())
                .addValue("issuedAt", atUtc(token.issuedAt()))
                .addValue("expiresAt", atUtc(token.expiresAt()));
        jdbc.update(sql, params);
    }

    @Override
    public Optional<RefreshToken> findByHash(String tokenHash) {
        // buscamos por hash exacto (columna UNIQUE)
        String sql = "SELECT " + COLUMNS + " FROM rw_refresh_token WHERE token_hash = :hash";
        var params = new MapSqlParameterSource("hash", tokenHash);
        return jdbc.query(sql, params, mapper()).stream().findFirst();
    }

    @Override
    public void markRotated(UUID tokenId, UUID replacementId, Instant when) {
        // rotacion: revocamos el token anterior y lo enlazamos con su reemplazo
        String sql = """
                UPDATE rw_refresh_token
                SET revoked_at = :when, replaced_by = :replacement
                WHERE id = :id AND revoked_at IS NULL
                """;
        var params = new MapSqlParameterSource()
                .addValue("id", tokenId)
                .addValue("replacement", replacementId)
                .addValue("when", atUtc(when));
        jdbc.update(sql, params);
    }

    @Override
    public void revoke(UUID tokenId, Instant when) {
        // revocacion simple (logout)
        String sql = "UPDATE rw_refresh_token SET revoked_at = :when WHERE id = :id AND revoked_at IS NULL";
        var params = new MapSqlParameterSource()
                .addValue("id", tokenId)
                .addValue("when", atUtc(when));
        jdbc.update(sql, params);
    }

    @Override
    public int revokeAllActiveForUser(UUID userId, Instant when) {
        // defensa ante reuso: revocamos todos los tokens vigentes del usuario
        String sql = "UPDATE rw_refresh_token SET revoked_at = :when WHERE user_id = :userId AND revoked_at IS NULL";
        var params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("when", atUtc(when));
        return jdbc.update(sql, params);
    }

    private static OffsetDateTime atUtc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private RowMapper<RefreshToken> mapper() {
        return (ResultSet rs, int rowNum) -> {
            OffsetDateTime revokedAt = rs.getObject("revoked_at", OffsetDateTime.class);
            UUID replacedBy = rs.getObject("replaced_by", UUID.class);
            return new RefreshToken(
                    rs.getObject("id", UUID.class),
                    rs.getObject("user_id", UUID.class),
                    rs.getString("token_hash"),
                    rs.getObject("issued_at", OffsetDateTime.class).toInstant(),
                    rs.getObject("expires_at", OffsetDateTime.class).toInstant(),
                    revokedAt == null ? null : revokedAt.toInstant(),
                    replacedBy);
        };
    }
}
