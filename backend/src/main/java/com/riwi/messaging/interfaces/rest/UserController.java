package com.riwi.messaging.interfaces.rest;

import com.riwi.messaging.application.user.DeleteUserUseCase;
import com.riwi.messaging.application.user.SearchUsersQuery;
import com.riwi.messaging.application.user.SearchUsersUseCase;
import com.riwi.messaging.application.user.UpdateUserCommand;
import com.riwi.messaging.application.user.UpdateUserUseCase;
import com.riwi.messaging.domain.model.UserPage;
import com.riwi.messaging.domain.model.UserSummary;
import com.riwi.messaging.interfaces.rest.dto.ErrorResponse;
import com.riwi.messaging.interfaces.rest.dto.PageResponse;
import com.riwi.messaging.interfaces.rest.dto.UpdateUserRequest;
import com.riwi.messaging.interfaces.rest.dto.UserSummaryResponse;
import com.riwi.messaging.interfaces.rest.support.UserCursorCodec;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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

// endpoints de gestion de usuarios: exponen los 3 stored procedures rw_search_users / rw_update_user / rw_delete_user
@Tag(name = "Usuarios", description = "Consulta, edicion y eliminacion de usuarios (SP de la BD)")
@RestController
@RequestMapping("/users")
public class UserController {

    private final SearchUsersUseCase searchUsers;
    private final UpdateUserUseCase updateUser;
    private final DeleteUserUseCase deleteUser;

    public UserController(SearchUsersUseCase searchUsers,
                          UpdateUserUseCase updateUser,
                          DeleteUserUseCase deleteUser) {
        this.searchUsers = searchUsers;
        this.updateUser = updateUser;
        this.deleteUser = deleteUser;
    }

    @Operation(summary = "Consulta de usuarios con keyset pagination; el SP restringe filas y campos del no-admin")
    @ApiResponse(responseCode = "400", description = "Cursor invalido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping
    public PageResponse<UserSummaryResponse> list(@RequestParam(required = false) String q,
                                                  @RequestParam(required = false) String cursor,
                                                  @RequestParam(required = false) Integer size,
                                                  @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        // decodificamos el cursor opaco a (displayName, id) antes de llamar al SP
        SearchUsersQuery query = new SearchUsersQuery(q, UserCursorCodec.decode(cursor), size, includeInactive);
        // llamamos al SP de consulta de usuarios (rw_search_users)
        UserPage page = searchUsers.execute(query);
        List<UserSummaryResponse> items = page.items().stream().map(UserSummaryResponse::from).toList();
        return new PageResponse<>(items, UserCursorCodec.encode(page.nextCursor()));
    }

    @Operation(summary = "Edita un usuario; el SP permite solo al propio usuario o a un admin, is_active solo al admin")
    @ApiResponse(responseCode = "200", description = "Usuario actualizado")
    @ApiResponse(responseCode = "403", description = "El actor no puede editar a ese usuario o ese campo",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Usuario inexistente",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PatchMapping("/{id}")
    public UserSummaryResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        // rw_update_user valida permisos; releemos el usuario para responder el cuerpo
        UserSummary updated = updateUser.execute(new UpdateUserCommand(
                id, request.displayName(), request.jobTitle(),
                request.avatarUrl(), request.bio(), request.isActive()));
        return UserSummaryResponse.from(updated);
    }

    @Operation(summary = "Elimina un usuario (soft delete); revoca sus refresh tokens y conserva mensajes e historial")
    @ApiResponse(responseCode = "204", description = "Usuario marcado como eliminado")
    @ApiResponse(responseCode = "403", description = "El actor no puede eliminar a ese usuario",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Usuario inexistente",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        // soft delete via rw_delete_user; nunca borrado fisico
        deleteUser.execute(id);
    }
}
