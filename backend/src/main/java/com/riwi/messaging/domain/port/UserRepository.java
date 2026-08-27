package com.riwi.messaging.domain.port;

import com.riwi.messaging.domain.model.ActorProfile;
import com.riwi.messaging.domain.model.User;

import java.util.Optional;
import java.util.UUID;

// puerto de persistencia de usuarios; el adaptador concreto vive en infrastructure
public interface UserRepository {

    Optional<User> findByEmail(String email);

    Optional<User> findById(UUID userId);

    Optional<ActorProfile> findProfileById(UUID userId);
}
