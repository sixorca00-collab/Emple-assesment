package com.riwi.messaging.infrastructure.security;

import com.riwi.messaging.domain.port.AccessTokenPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

// cadena de seguridad stateless: /auth/** y /health publicas, el resto autenticado
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            AccessTokenPort accessTokens,
                                            RestAuthenticationEntryPoint entryPoint) throws Exception {

        // instancia local: no es un bean, asi no se registra dos veces en el servlet
        JwtAuthenticationFilter jwtAuthenticationFilter = new JwtAuthenticationFilter(accessTokens);

        http
                // API sin cookies de sesion: no aplica CSRF
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**", "/health").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint))
                // el filtro JWT corre antes del filtro estandar de usuario/clave
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
