package com.riwi.messaging.infrastructure.rls;

import com.riwi.messaging.domain.model.TokenClaims;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

// fija el actor RLS (app.current_user_id) al inicio de cada caso de uso transaccional autenticado
@Aspect
@Component
@Order(50)
public class TransactionActorAspect {

    private static final Logger log = LoggerFactory.getLogger(TransactionActorAspect.class);

    private final JdbcTemplate jdbcTemplate;

    public TransactionActorAspect(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // aplica a los metodos publicos de los casos de uso (clases *UseCase) de la capa de aplicacion
    @Before("execution(public * com.riwi.messaging.application..*UseCase.*(..))")
    public void bindCurrentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof TokenClaims claims)) {
            return;
        }

        // sin transaccion activa el SET LOCAL no tendria efecto util
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            log.warn("no active transaction while binding RLS actor {}", claims.userId());
            return;
        }

        UUID actorId = claims.userId();
        // fijamos el actor para RLS dentro de la transaccion actual (SET LOCAL via funcion de BD)
        jdbcTemplate.query("SELECT rw_set_current_user(?)", resultSet -> null, actorId);
    }
}
