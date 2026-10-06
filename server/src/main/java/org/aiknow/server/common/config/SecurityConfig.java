package org.aiknow.server.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.aiknow.server.auth.handler.OAuth2LoginSuccessHandler;
import org.aiknow.server.auth.service.SessionAuthenticationService;
import org.aiknow.server.common.exception.CommonErrorCode;
import org.aiknow.server.common.exception.ErrorResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        OAuth2LoginSuccessHandler successHandler,
        SessionAuthenticationService sessionAuthenticationService,
        ObjectMapper objectMapper
    ) throws Exception {

        http
            .cors(Customizer.withDefaults())
            .csrf(Customizer.withDefaults())

            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .sessionFixation(fixation -> fixation.changeSessionId())
            )

            .requestCache(AbstractHttpConfigurer::disable)

            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/health-check",
                    "/actuator/health",
                    "/swagger-ui.html",
                    "/swagger-ui/**",
                    "/v3/api-docs/**",
                    "/login",
                    "/oauth2/authorization/**",
                    "/login/oauth2/code/**",
                    "/api/v1/auth/csrf"
                ).permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/**").authenticated()
                .anyRequest().denyAll()
            )

            .oauth2Login(oauth2 -> oauth2
                .authorizedClientRepository(
                    new HttpSessionOAuth2AuthorizedClientRepository()
                )
                .successHandler(successHandler)
                .failureHandler((request, response, exception) -> {
                    sessionAuthenticationService.logout(request, response);
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                })
            )

            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)

            .logout(AbstractHttpConfigurer::disable)

            .exceptionHandling(exception -> exception
                .authenticationEntryPoint((request, response, ex) ->
                    response.setStatus(HttpStatus.UNAUTHORIZED.value())
                )
                .accessDeniedHandler((request, response, ex) ->
                    writeErrorResponse(response, CommonErrorCode.FORBIDDEN, objectMapper)
                )
            );

        return http.build();
    }

    private void writeErrorResponse(
        jakarta.servlet.http.HttpServletResponse response,
        CommonErrorCode errorCode,
        ObjectMapper objectMapper
    ) throws java.io.IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), ErrorResponse.of(errorCode));
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
        @Value("${app.frontend-origin}") String frontendOrigin
    ) {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(List.of(frontendOrigin));
        configuration.setAllowedMethods(
            List.of("GET", "PUT", "POST", "PATCH", "DELETE", "OPTIONS")
        );
        configuration.setAllowedHeaders(
            List.of("Content-Type", "X-CSRF-TOKEN")
        );
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
            new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
