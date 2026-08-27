package com.riwi.messaging;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// punto de entrada del backend; excluimos el user/clave en memoria porque solo autenticamos con JWT
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class MessagingApplication {

    public static void main(String[] args) {
        SpringApplication.run(MessagingApplication.class, args);
    }
}
