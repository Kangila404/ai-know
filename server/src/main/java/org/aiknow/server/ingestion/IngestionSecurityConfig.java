package org.aiknow.server.ingestion;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class IngestionSecurityConfig {
    @Bean @Order(1)
    SecurityFilterChain ingestionFilterChain(HttpSecurity http, @Value("${app.ingestion.token:}") String token) throws Exception {
        if (!token.isEmpty() && (token.length() < 32 || token.chars().anyMatch(Character::isWhitespace)))
            throw new IllegalArgumentException("N8N_INGEST_TOKEN must contain at least 32 non-whitespace characters");
        return http.securityMatcher("/internal/**")
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .securityContext(s -> s.securityContextRepository(new NullSecurityContextRepository()))
            .csrf(AbstractHttpConfigurer::disable)
            .requestCache(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .addFilterBefore(new TokenFilter(token), AnonymousAuthenticationFilter.class)
            .authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.GET, "/internal/v1/images/storage").hasRole("INGEST")
                .requestMatchers(HttpMethod.POST, "/internal/v1/card-news/import", "/internal/v1/images").hasRole("INGEST")
                .requestMatchers(HttpMethod.POST, "/internal/v1/generation/existing", "/internal/v1/generation/claim", "/internal/v1/generation/response").hasRole("INGEST")
                .anyRequest().denyAll())
            .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> res.setStatus(401))
                .accessDeniedHandler((req, res, ex) -> res.setStatus(403)))
            .build();
    }

    // Not a bean: this filter belongs only to the isolated machine-to-machine chain.
    private static final class TokenFilter extends OncePerRequestFilter {
        private final byte[] expected;
        TokenFilter(String token) { expected = token.getBytes(StandardCharsets.UTF_8); }
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
            String header = request.getHeader("Authorization");
            if (expected.length == 0 || header == null || !header.startsWith("Bearer ")
                || !MessageDigest.isEqual(expected, header.substring(7).getBytes(StandardCharsets.UTF_8))) {
                response.setStatus(401);
                return;
            }
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new UsernamePasswordAuthenticationToken("n8n", null,
                List.of(new SimpleGrantedAuthority("ROLE_INGEST"))));
            SecurityContextHolder.setContext(context);
            try { chain.doFilter(request, response); }
            finally { SecurityContextHolder.clearContext(); }
        }
    }
}
