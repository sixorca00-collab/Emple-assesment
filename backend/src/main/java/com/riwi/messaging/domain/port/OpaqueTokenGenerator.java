package com.riwi.messaging.domain.port;

// puerto que genera el valor opaco aleatorio del refresh token
public interface OpaqueTokenGenerator {

    String generate();
}
