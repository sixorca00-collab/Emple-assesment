package com.riwi.messaging.infrastructure.security;

import com.riwi.messaging.domain.port.AccessTokenPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

// cadena de seguridad stateless: /auth/** y /health publicas, el resto autenticado
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // origenes del frontend permitidos: lista separada por comas desde CORS_ALLOWED_ORIGINS
    @Value("${CORS_ALLOWED_ORIGINS:http://localhost:4200,http://localhost:14200}")
    private String allowedOrigins;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            AccessTokenPort accessTokens,
                                            RestAuthenticationEntryPoint entryPoint) throws Exception {

        // instancia local: no es un bean, asi no se registra dos veces en el servlet
        JwtAuthenticationFilter jwtAuthenticationFilter = new JwtAuthenticationFilter(accessTokens);

        http
                // habilitamos CORS: Spring Security 6 deja pasar el preflight OPTIONS sin autenticacion
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // API sin cookies de sesion: no aplica CSRF
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // /ws/** se autentica en el handshake (JwtHandshakeInterceptor), no con el filtro Bearer
                        .requestMatchers("/auth/**", "/health", "/ws/**").permitAll()
                        // documentacion de la API: Swagger UI y contrato OpenAPI publicos
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**",
                                "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint))
                // el filtro JWT corre antes del filtro estandar de usuario/clave
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // configuracion CORS para las llamadas cross-origin del frontend servido por nginx
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // patrones (no origenes exactos) para tolerar puertos/hosts de dev con credenciales
        config.setAllowedOriginPatterns(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-Id"));
        config.setExposedHeaders(List.of("X-Correlation-Id"));
        // el front manda Authorization; no usa cookies pero lo permitimos por si acaso
        config.setAllowCredentials(true);
        // cacheamos el preflight ~1h
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
