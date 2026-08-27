package com.riwi.messaging.application.auth;

import com.riwi.messaging.domain.exception.AuthenticationFailedException;
import com.riwi.messaging.domain.model.ActorProfile;
import com.riwi.messaging.domain.model.AuthTokens;
import com.riwi.messaging.domain.model.TokenClaims;
import com.riwi.messaging.domain.model.User;
import com.riwi.messaging.domain.port.PasswordHasher;
import com.riwi.messaging.domain.port.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// caso de uso: autentica con email + contrasena y emite el primer par de tokens
@Service
public class LoginUseCase {

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final TokenIssuer tokenIssuer;

    public LoginUseCase(UserRepository users, PasswordHasher passwordHasher, TokenIssuer tokenIssuer) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.tokenIssuer = tokenIssuer;
    }

    @Transactional
    public AuthTokens execute(LoginCommand command) {
        // buscamos por correo; cualquier fallo produce el mismo error generico
        User user = users.findByEmail(command.email())
                .filter(User::canAuthenticate)
                .orElseThrow(AuthenticationFailedException::new);

        // verificamos la contrasena contra el hash bcrypt
        if (!passwordHasher.matches(command.rawPassword(), user.passwordHash())) {
            throw new AuthenticationFailedException();
        }

        // el perfil alimenta los claims name/job_title que consume el copiloto
        ActorProfile profile = users.findProfileById(user.id())
                .orElseThrow(AuthenticationFailedException::new);

        TokenClaims claims = new TokenClaims(
                user.id(),
                user.platformAdmin(),
                profile.displayName(),
                profile.jobTitle());

        // emitimos el par y devolvemos el refresh en claro una unica vez
        return tokenIssuer.issueFor(claims).tokens();
    }
}
