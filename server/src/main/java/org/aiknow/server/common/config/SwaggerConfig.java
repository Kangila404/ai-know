package org.aiknow.server.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class SwaggerConfig {

    private static final String SECURITY_SCHEME_NAME = "sessionAuth";

    @Bean
    public OpenAPI openAPI() {
        SecurityScheme securityScheme = new SecurityScheme()
            .type(SecurityScheme.Type.APIKEY)
            .in(SecurityScheme.In.COOKIE)
            .name("JSESSIONID");

        SecurityRequirement securityRequirement = new SecurityRequirement()
            .addList(SECURITY_SCHEME_NAME);

        return new OpenAPI()
            .info(new Info()
                .title("AI_KNOW API")
                .version("0.0.1")
            )
            .components(new Components()
                .addSecuritySchemes(SECURITY_SCHEME_NAME, securityScheme)
            )
            .addSecurityItem(securityRequirement);
    }
}
