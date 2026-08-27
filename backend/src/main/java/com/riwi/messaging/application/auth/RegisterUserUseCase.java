package com.riwi.messaging.application.auth;

import com.riwi.messaging.domain.exception.EmailAlreadyRegisteredException;
import com.riwi.messaging.domain.model.AuthTokens;
import com.riwi.messaging.domain.model.TokenClaims;
import com.riwi.messaging.domain.port.PasswordHasher;
import com.riwi.messaging.domain.port.UserRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// caso de uso: crea la cuenta y hace auto-login emitiendo el primer par de tokens
@Service
public class RegisterUserUseCase {

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final TokenIssuer tokenIssuer;

    public RegisterUserUseCase(UserRepository users, PasswordHasher passwordHasher, TokenIssuer tokenIssuer) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.tokenIssuer = tokenIssuer;
    }

    @Transactional
    public AuthTokens execute(RegisterCommand command) {
        // guardamos el correo tal cual (solo sin espacios); login ya compara con lower() y el indice unico es sobre lower(email)
        String email = command.email().trim();
        String displayName = command.name().trim();
        String jobTitle = command.jobTitle().trim();

        // nunca guardamos la contrasena en claro: hash bcrypt
        String passwordHash = passwordHasher.hash(command.rawPassword());

        // insertamos rw_user + rw_user_profile en la misma transaccion
        UUID userId;
        try {
            userId = users.create(email, passwordHash, displayName, jobTitle);
        } catch (DuplicateKeyException ex) {
            // choque contra el indice unico parcial del correo -> 409
            throw new EmailAlreadyRegisteredException();
        }

        // el usuario nace sin privilegios de plataforma
        TokenClaims claims = new TokenClaims(userId, false, displayName, jobTitle);

        // mismo camino de emision que LoginUseCase: TokenIssuer firma el access y persiste el hash del refresh
        return tokenIssuer.issueFor(claims).tokens();
    }
}
