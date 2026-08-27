package com.riwi.messaging.domain.port;

// puerto de hashing del refresh token antes de persistirlo (SHA-256)
public interface TokenHasher {

    String hash(String rawToken);
}
