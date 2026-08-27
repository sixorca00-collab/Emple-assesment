package com.riwi.messaging.interfaces.rest;

import com.riwi.messaging.application.messaging.AddChannelMemberUseCase;
import com.riwi.messaging.application.messaging.AddMemberCommand;
import com.riwi.messaging.application.messaging.CreateChannelCommand;
import com.riwi.messaging.application.messaging.CreateChannelUseCase;
import com.riwi.messaging.application.messaging.GetChannelHistoryUseCase;
import com.riwi.messaging.application.messaging.ListConversationsUseCase;
import com.riwi.messaging.application.messaging.MarkChannelReadUseCase;
import com.riwi.messaging.application.messaging.PostMessageCommand;
import com.riwi.messaging.application.messaging.PostMessageUseCase;
import com.riwi.messaging.domain.model.ChannelView;
import com.riwi.messaging.domain.model.ConversationPage;
import com.riwi.messaging.domain.model.MessagePage;
import com.riwi.messaging.domain.model.MessageView;
import com.riwi.messaging.interfaces.rest.dto.ChannelResponse;
import com.riwi.messaging.interfaces.rest.dto.ConversationResponse;
import com.riwi.messaging.interfaces.rest.dto.CreateChannelRequest;
import com.riwi.messaging.interfaces.rest.dto.AddMemberRequest;
import com.riwi.messaging.interfaces.rest.dto.MarkReadResponse;
import com.riwi.messaging.interfaces.rest.dto.MessageResponse;
import com.riwi.messaging.interfaces.rest.dto.PageResponse;
import com.riwi.messaging.interfaces.rest.dto.PostMessageRequest;
import com.riwi.messaging.interfaces.rest.support.CursorCodec;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// endpoints de canales y de mensajes dentro de un canal; el actor se propaga a RLS via TransactionActorAspect
@RestController
@RequestMapping("/channels")
public class ChannelController {

    private final ListConversationsUseCase listConversations;
    private final CreateChannelUseCase createChannel;
    private final AddChannelMemberUseCase addChannelMember;
    private final GetChannelHistoryUseCase getChannelHistory;
    private final PostMessageUseCase postMessage;
    private final MarkChannelReadUseCase markChannelRead;

    public ChannelController(ListConversationsUseCase listConversations,
                            CreateChannelUseCase createChannel,
                            AddChannelMemberUseCase addChannelMember,
                            GetChannelHistoryUseCase getChannelHistory,
                            PostMessageUseCase postMessage,
                            MarkChannelReadUseCase markChannelRead) {
        this.listConversations = listConversations;
        this.createChannel = createChannel;
        this.addChannelMember = addChannelMember;
        this.getChannelHistory = getChannelHistory;
        this.postMessage = postMessage;
        this.markChannelRead = markChannelRead;
    }

    @GetMapping
    public PageResponse<ConversationResponse> list(@RequestParam(required = false) String cursor,
                                                  @RequestParam(required = false) Integer size) {
        // decodificamos el cursor opaco a (timestamp, id) antes de consultar
        ConversationPage page = listConversations.execute(CursorCodec.decode(cursor), size);
        List<ConversationResponse> items = page.items().stream().map(ConversationResponse::from).toList();
        return new PageResponse<>(items, CursorCodec.encode(page.nextCursor()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChannelResponse create(@Valid @RequestBody CreateChannelRequest request) {
        // la BD deja al actor como owner del canal recien creado
        ChannelView created = createChannel.execute(
                new CreateChannelCommand(request.name(), request.description(), request.isPrivate()));
        return ChannelResponse.from(created);
    }

    @PostMapping("/{channelId}/members")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addMember(@PathVariable UUID channelId, @Valid @RequestBody AddMemberRequest request) {
        // solo owner/admin del canal puede agregar miembros (validado en SQL)
        addChannelMember.execute(new AddMemberCommand(channelId, request.userId(), request.role()));
    }

    @GetMapping("/{channelId}/messages")
    public PageResponse<MessageResponse> history(@PathVariable UUID channelId,
                                                 @RequestParam(required = false) String cursor,
                                                 @RequestParam(required = false) Integer size) {
        // historial con keyset pagination (Consulta 1); RLS excluye canales ajenos
        MessagePage page = getChannelHistory.execute(channelId, CursorCodec.decode(cursor), size);
        List<MessageResponse> items = page.items().stream().map(MessageResponse::from).toList();
        return new PageResponse<>(items, CursorCodec.encode(page.nextCursor()));
    }

    @PostMapping("/{channelId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse post(@PathVariable UUID channelId, @Valid @RequestBody PostMessageRequest request) {
        // publica via rw_post_message y emite el evento en tiempo real tras el commit
        MessageView sent = postMessage.execute(
                new PostMessageCommand(channelId, request.body(), request.clientNonce()));
        return MessageResponse.from(sent);
    }

    @PostMapping("/{channelId}/read")
    public MarkReadResponse read(@PathVariable UUID channelId) {
        // marca leidos los mensajes ajenos vivos del canal
        return new MarkReadResponse(markChannelRead.execute(channelId));
    }
}
