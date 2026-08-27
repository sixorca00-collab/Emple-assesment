package com.riwi.messaging.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.function.Function;

// base para los tests de RLS: ejecuta SQL como un actor concreto usando el datasource riwi_app
public abstract class AbstractRlsIT extends AbstractPostgresIT {

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    // abre una transaccion, fija el actor para RLS y ejecuta el trabajo dentro de ella
    protected <T> T asActor(UUID actorId, Function<JdbcTemplate, T> work) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return tx.execute(status -> {
            jdbcTemplate.query("SELECT rw_set_current_user(?)", resultSet -> null, actorId);
            return work.apply(jdbcTemplate);
        });
    }
}
