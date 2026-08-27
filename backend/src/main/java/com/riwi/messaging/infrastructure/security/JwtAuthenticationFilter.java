package com.riwi.messaging.infrastructure.security;

import com.riwi.messaging.domain.exception.InvalidTokenException;
import com.riwi.messaging.domain.model.TokenClaims;
import com.riwi.messaging.domain.port.AccessTokenPort;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

// extrae el access token del header Authorization y fija el actor en el SecurityContext
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final AccessTokenPort accessTokens;

    public JwtAuthenticationFilter(AccessTokenPort accessTokens) {
        this.accessTokens = accessTokens;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            try {
                // el user_id sale EXCLUSIVAMENTE del claim sub del token verificado
                TokenClaims claims = accessTokens.verify(header.substring(PREFIX.length()));

                List<GrantedAuthority> authorities = new ArrayList<>();
                if (claims.platformAdmin()) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"));
                }

                var authentication = new UsernamePasswordAuthenticationToken(claims, header, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (InvalidTokenException e) {
                // token invalido: seguimos sin autenticar y el entry point respondera 401
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
