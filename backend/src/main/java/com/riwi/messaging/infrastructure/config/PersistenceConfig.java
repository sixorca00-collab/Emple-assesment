package com.riwi.messaging.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

// order = 0 => el aspecto de transaccion envuelve a los demas: el SET LOCAL del actor corre DENTRO de la tx
@Configuration
@EnableTransactionManagement(order = 0)
public class PersistenceConfig {

    @Bean
    NamedParameterJdbcTemplate namedParameterJdbcTemplate(DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }
}
