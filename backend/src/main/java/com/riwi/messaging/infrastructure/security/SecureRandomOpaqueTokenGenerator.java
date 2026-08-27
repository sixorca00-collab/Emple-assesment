package com.riwi.messaging.infrastructure.security;

import com.riwi.messaging.domain.port.OpaqueTokenGenerator;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

// genera 256 bits aleatorios y los codifica en base64url para el valor del refresh token
@Component
public class SecureRandomOpaqueTokenGenerator implements OpaqueTokenGenerator {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom random = new SecureRandom();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

    @Override
    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return encoder.encodeToString(bytes);
    }
}
