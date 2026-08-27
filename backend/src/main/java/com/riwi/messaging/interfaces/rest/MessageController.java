package com.riwi.messaging.interfaces.rest;

import com.riwi.messaging.application.messaging.DeleteMessageUseCase;
import com.riwi.messaging.application.messaging.EditMessageCommand;
import com.riwi.messaging.application.messaging.EditMessageUseCase;
import com.riwi.messaging.domain.model.MessageView;
import com.riwi.messaging.interfaces.rest.dto.EditMessageRequest;
import com.riwi.messaging.interfaces.rest.dto.MessageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// endpoints sobre un mensaje concreto: edicion y soft delete
@RestController
@RequestMapping("/messages")
public class MessageController {

    private final EditMessageUseCase editMessage;
    private final DeleteMessageUseCase deleteMessage;

    public MessageController(EditMessageUseCase editMessage, DeleteMessageUseCase deleteMessage) {
        this.editMessage = editMessage;
        this.deleteMessage = deleteMessage;
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
