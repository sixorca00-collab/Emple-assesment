package com.riwi.messaging.domain.port;

// puerto de hashing de contrasenas; el adaptador usa BCrypt
public interface PasswordHasher {

    boolean matches(String rawPassword, String passwordHash);

    String hash(String rawPassword);
}
