package com.riwi.messaging.messaging;

import com.riwi.messaging.interfaces.rest.dto.AddMemberRequest;
import com.riwi.messaging.interfaces.rest.dto.ChannelResponse;
import com.riwi.messaging.interfaces.rest.dto.CreateChannelRequest;
import com.riwi.messaging.interfaces.rest.dto.LoginRequest;
import com.riwi.messaging.interfaces.rest.dto.MessageResponse;
import com.riwi.messaging.interfaces.rest.dto.PostMessageRequest;
import com.riwi.messaging.interfaces.rest.dto.TokenResponse;
import com.riwi.messaging.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// verifica que un miembro conectado recibe el evento de un mensaje nuevo y un no-miembro no
class MessageWebSocketIT extends AbstractPostgresIT {

    private static final UUID VALENTINA = UUID.fromString("11111111-1111-1111-1111-000000000004");

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Test
    void memberReceivesEventAndNonMemberDoesNot() throws Exception {
        String andres = token("andres.gomez@riwi.io");
        String valentina = token("valentina.ruiz@riwi.io");
        String laura = token("laura.cardona@riwi.io");

        UUID channelId = createChannel(andres);
        addMember(andres, channelId, VALENTINA);

        // Valentina es miembro; Laura no
        CollectingHandler memberInbox = new CollectingHandler();
        CollectingHandler outsiderInbox = new CollectingHandler();
        WebSocketSession memberSession = connect(valentina, memberInbox);
        WebSocketSession outsiderSession = connect(laura, outsiderInbox);

        try {
            MessageResponse posted = postMessage(andres, channelId, "hello over websocket");

            // el miembro recibe el frame del mensaje nuevo
            String frame = memberInbox.messages.poll(5, TimeUnit.SECONDS);
            assertThat(frame).isNotNull();
            assertThat(frame).contains("message.created");
            assertThat(frame).contains(posted.id().toString());
            assertThat(frame).contains("hello over websocket");

            // el no-miembro no recibe nada
            assertThat(outsiderInbox.messages.poll(1, TimeUnit.SECONDS)).isNull();
        } finally {
            memberSession.close(CloseStatus.NORMAL);
            outsiderSession.close(CloseStatus.NORMAL);
        }
    }

    private WebSocketSession connect(String accessToken, CollectingHandler handler) throws Exception {
        URI uri = URI.create("ws://localhost:" + port + "/ws/messages?access_token=" + accessToken);
        return new StandardWebSocketClient().execute(handler, null, uri).get(5, TimeUnit.SECONDS);
    }

    private static final class CollectingHandler extends TextWebSocketHandler {
        private final LinkedBlockingQueue<String> messages = new LinkedBlockingQueue<>();

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            messages.add(message.getPayload());
        }
    }

    private String token(String email) {
        TokenResponse body = rest.postForEntity(
                "/auth/login", new LoginRequest(email, "Password123!"), TokenResponse.class).getBody();
        assertThat(body).isNotNull();
        return body.accessToken();
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private UUID createChannel(String token) {
        return rest.exchange("/channels", HttpMethod.POST,
                new HttpEntity<>(new CreateChannelRequest("it-ws-" + UUID.randomUUID(), "ws test", true), authHeaders(token)),
                ChannelResponse.class).getBody().id();
    }

    private void addMember(String token, UUID channelId, UUID userId) {
        rest.exchange("/channels/" + channelId + "/members", HttpMethod.POST,
                new HttpEntity<>(new AddMemberRequest(userId, "member"), authHeaders(token)), Void.class);
    }

    private MessageResponse postMessage(String token, UUID channelId, String body) {
        return rest.exchange("/channels/" + channelId + "/messages", HttpMethod.POST,
                new HttpEntity<>(new PostMessageRequest(body, null), authHeaders(token)), MessageResponse.class).getBody();
    }
}
