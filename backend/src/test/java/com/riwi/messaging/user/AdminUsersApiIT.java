package com.riwi.messaging.user;

import com.riwi.messaging.interfaces.rest.dto.ErrorResponse;
import com.riwi.messaging.interfaces.rest.dto.LoginRequest;
import com.riwi.messaging.interfaces.rest.dto.PageResponse;
import com.riwi.messaging.interfaces.rest.dto.RegisterRequest;
import com.riwi.messaging.interfaces.rest.dto.TokenResponse;
import com.riwi.messaging.interfaces.rest.dto.UpdateUserRequest;
import com.riwi.messaging.interfaces.rest.dto.UserSummaryResponse;
import com.riwi.messaging.support.AbstractRlsIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// gestion de usuarios end-to-end: consulta con keyset, permisos de edicion y soft delete via los SP
class AdminUsersApiIT extends AbstractRlsIT {

    // usuario admin del seed (is_platform_admin = true)
    private static final String ADMIN_EMAIL = "juan.olarte@riwi.io";
    // usuario no admin del seed
    private static final String ANDRES_EMAIL = "andres.gomez@riwi.io";
    // usuario del seed que no toca ningun otro test; lo usamos para el soft delete
    private static final UUID MARIANA = UUID.fromString("11111111-1111-1111-1111-000000000008");
    private static final String MARIANA_EMAIL = "mariana.lopez@riwi.io";

    private static final String SEED_PASSWORD = "Password123!";

    @Autowired
    private TestRestTemplate rest;

    @Test
    void adminSeesInactiveUsersOnlyWithFlagAndNonAdminNeverDoes() {
        String admin = token(ADMIN_EMAIL);
        String tag = shortId();
        UUID probeId = register("InactiveProbe-" + tag);

        // el admin desactiva al usuario recien creado
        ResponseEntity<UserSummaryResponse> deactivated = patch(admin, probeId, new UpdateUserRequest(null, null, null, null, false));
        assertThat(deactivated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(deactivated.getBody()).isNotNull();
        assertThat(deactivated.getBody().isActive()).isFalse();

        // con includeInactive=true el admin si lo ve
        assertThat(idsOf(search(admin, tag, true))).contains(probeId);
        // sin el flag no aparece
        assertThat(idsOf(search(admin, tag, false))).doesNotContain(probeId);
        // un no admin no lo ve aunque pida includeInactive=true (el SP ignora el flag)
        assertThat(idsOf(search(token(ANDRES_EMAIL), tag, true))).doesNotContain(probeId);
    }

    @Test
    void nonAdminCannotEditAThirdPartyButCanEditOwnProfile() {
        String tag = shortId();
        UUID userId = register("SelfEditor-" + tag);
        String self = token("selfeditor-" + tag + "@riwi.io");

        // un tercero no admin no puede editar a otro usuario
        ResponseEntity<ErrorResponse> forbidden = rest.exchange(
                "/users/" + userId, HttpMethod.PATCH,
                new HttpEntity<>(new UpdateUserRequest("Hijacked Name", null, null, null, null), authHeaders(token(ANDRES_EMAIL))),
                ErrorResponse.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // el propio usuario si puede editar su perfil
        String newName = "Renamed " + shortId();
        ResponseEntity<UserSummaryResponse> ok = patch(self, userId, new UpdateUserRequest(newName, null, null, null, null));
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ok.getBody()).isNotNull();
        assertThat(ok.getBody().displayName()).isEqualTo(newName);
    }

    @Test
    void nonAdminCannotChangeOwnActiveFlag() {
        String tag = shortId();
        UUID userId = register("FlagProbe-" + tag);
        String self = token("flagprobe-" + tag + "@riwi.io");

        // is_active es exclusivo de administradores, incluso sobre el propio perfil
        ResponseEntity<ErrorResponse> forbidden = rest.exchange(
                "/users/" + userId, HttpMethod.PATCH,
                new HttpEntity<>(new UpdateUserRequest(null, null, null, null, true), authHeaders(self)),
                ErrorResponse.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminSoftDeletesSeedUserRevokingTokensButKeepingMessages() {
        // Mariana inicia sesion para tener un refresh token vigente
        token(MARIANA_EMAIL);
        // su mensaje del seed antes del borrado
        UUID messageId = runAsBootstrapQuery(
                "SELECT id FROM rw_message WHERE sender_id = '" + MARIANA + "'::uuid LIMIT 1", UUID.class);
        assertThat(messageId).isNotNull();

        ResponseEntity<Void> deleted = rest.exchange(
                "/users/" + MARIANA, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token(ADMIN_EMAIL))), Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // el usuario queda marcado como eliminado y desactivado
        Map<String, Object> row = runAsBootstrapMap(
                "SELECT deleted_at, is_active FROM rw_user WHERE id = '" + MARIANA + "'::uuid");
        assertThat(row.get("deleted_at")).isNotNull();
        assertThat(row.get("is_active")).isEqualTo(false);

        // no le quedan refresh tokens vigentes
        Integer live = runAsBootstrapQuery(
                "SELECT count(*) FROM rw_refresh_token WHERE user_id = '" + MARIANA + "'::uuid AND revoked_at IS NULL",
                Integer.class);
        assertThat(live).isZero();

        // su mensaje se conserva (nunca hay borrado fisico en cascada)
        Integer stillThere = runAsBootstrapQuery(
                "SELECT count(*) FROM rw_message WHERE id = '" + messageId + "'::uuid", Integer.class);
        assertThat(stillThere).isEqualTo(1);
    }

    @Test
    void patchToNonexistentUserReturns404() {
        ResponseEntity<ErrorResponse> response = rest.exchange(
                "/users/" + UUID.randomUUID(), HttpMethod.PATCH,
                new HttpEntity<>(new UpdateUserRequest("Ghost User", null, null, null, null), authHeaders(token(ADMIN_EMAIL))),
                ErrorResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---- helpers ----

    private String token(String email) {
        TokenResponse body = rest.postForEntity(
                "/auth/login", new LoginRequest(email, SEED_PASSWORD), TokenResponse.class).getBody();
        assertThat(body).isNotNull();
        return body.accessToken();
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    // registra un usuario nuevo y devuelve su id resolviendolo con una busqueda del admin
    private UUID register(String displayName) {
        String email = slug(displayName) + "@riwi.io";
        ResponseEntity<TokenResponse> created = rest.postForEntity(
                "/auth/register", new RegisterRequest(displayName, "IT Tester", email, SEED_PASSWORD), TokenResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID found = idsOf(search(token(ADMIN_EMAIL), displayName, false)).stream().findFirst().orElse(null);
        assertThat(found).isNotNull();
        return found;
    }

    private PageResponse<UserSummaryResponse> search(String token, String q, boolean includeInactive) {
        ResponseEntity<PageResponse<UserSummaryResponse>> response = rest.exchange(
                "/users?q=" + q.replace(" ", "%20") + "&includeInactive=" + includeInactive,
                HttpMethod.GET, new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private ResponseEntity<UserSummaryResponse> patch(String token, UUID id, UpdateUserRequest body) {
        return rest.exchange("/users/" + id, HttpMethod.PATCH,
                new HttpEntity<>(body, authHeaders(token)), UserSummaryResponse.class);
    }

    private static java.util.List<UUID> idsOf(PageResponse<UserSummaryResponse> page) {
        return page.items().stream().map(UserSummaryResponse::id).toList();
    }

    private <T> T runAsBootstrapQuery(String sql, Class<T> type) {
        java.util.concurrent.atomic.AtomicReference<T> ref = new java.util.concurrent.atomic.AtomicReference<>();
        runAsBootstrap(conn -> {
            try (var st = conn.createStatement(); var rs = st.executeQuery(sql)) {
                rs.next();
                Object value = rs.getObject(1);
                if (type == Integer.class && value instanceof Number number) {
                    ref.set(type.cast(number.intValue()));
                } else if (type == UUID.class && value != null) {
                    ref.set(type.cast(UUID.fromString(value.toString())));
                } else {
                    ref.set(type.cast(value));
                }
            }
        });
        return ref.get();
    }

    private Map<String, Object> runAsBootstrapMap(String sql) {
        java.util.HashMap<String, Object> out = new java.util.HashMap<>();
        runAsBootstrap(conn -> {
            try (var st = conn.createStatement(); var rs = st.executeQuery(sql)) {
                rs.next();
                var meta = rs.getMetaData();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    out.put(meta.getColumnLabel(i), rs.getObject(i));
                }
            }
        });
        return out;
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static String slug(String displayName) {
        return displayName.toLowerCase().replace(" ", "-");
    }
}
