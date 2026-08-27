package com.riwi.messaging.interfaces.rest;

import com.riwi.messaging.application.messaging.DeleteMessageUseCase;
import com.riwi.messaging.application.messaging.EditMessageCommand;
import com.riwi.messaging.application.messaging.EditMessageUseCase;
import com.riwi.messaging.application.messaging.SearchMessagesCommand;
import com.riwi.messaging.application.messaging.SearchMessagesUseCase;
import com.riwi.messaging.domain.model.MessageView;
import com.riwi.messaging.domain.model.SearchCursor;
import com.riwi.messaging.domain.model.SearchResultPage;
import com.riwi.messaging.interfaces.rest.dto.EditMessageRequest;
import com.riwi.messaging.interfaces.rest.dto.MessageResponse;
import com.riwi.messaging.interfaces.rest.dto.PageResponse;
import com.riwi.messaging.interfaces.rest.dto.SearchHitResponse;
import com.riwi.messaging.interfaces.rest.support.SearchCursorCodec;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// endpoints sobre mensajes: busqueda full-text, edicion y soft delete
@RestController
@RequestMapping("/messages")
public class MessageController {

    private final SearchMessagesUseCase searchMessages;
    private final EditMessageUseCase editMessage;
    private final DeleteMessageUseCase deleteMessage;

    public MessageController(SearchMessagesUseCase searchMessages,
                            EditMessageUseCase editMessage,
                            DeleteMessageUseCase deleteMessage) {
        this.searchMessages = searchMessages;
        this.editMessage = editMessage;
        this.deleteMessage = deleteMessage;
    }

    @GetMapping("/search")
    public PageResponse<SearchHitResponse> search(@RequestParam(required = false) String q,
                                                  @RequestParam(required = false) UUID channelId,
                                                  @RequestParam(required = false) String cursor,
                                                  @RequestParam(required = false) Integer size) {
        // decodificamos el cursor opaco (rank, id) del keyset por relevancia
        SearchCursor after = SearchCursorCodec.decode(cursor);
        // busqueda con resaltado (Consulta 2); la RLS limita los resultados a canales del actor
        SearchResultPage page = searchMessages.execute(new SearchMessagesCommand(q, channelId, after, size));
        List<SearchHitResponse> items = page.items().stream().map(SearchHitResponse::from).toList();
        return new PageResponse<>(items, SearchCursorCodec.encode(page.nextCursor()));
    }

    @PatchMapping("/{messageId}")
    public MessageResponse edit(@PathVariable UUID messageId, @Valid @RequestBody EditMessageRequest request) {
        // rw_edit_message solo permite editar al autor
        MessageView edited = editMessage.execute(new EditMessageCommand(messageId, request.body()));
        return MessageResponse.from(edited);
    }

    @DeleteMapping("/{messageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID messageId) {
        // soft delete via rw_soft_delete_message; nunca borrado fisico
        deleteMessage.execute(messageId);
    }
}
