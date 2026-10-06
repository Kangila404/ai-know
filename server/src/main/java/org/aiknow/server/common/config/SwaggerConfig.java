package org.aiknow.server.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class SwaggerConfig {

    private static final String SECURITY_SCHEME_NAME = "sessionAuth";

    @Bean
    public OpenApiCustomizer csrfHeaderCustomizer() {
        return openApi -> openApi.getPaths().values().forEach(path ->
            path.readOperationsMap().forEach((method, operation) -> {
                if (Set.of("GET", "HEAD", "OPTIONS", "TRACE").contains(method.name())) {
                    return;
                }
                boolean hasCsrfHeader = operation.getParameters() != null
                    && operation.getParameters().stream().anyMatch(parameter ->
                        "header".equals(parameter.getIn())
                            && "X-CSRF-TOKEN".equalsIgnoreCase(parameter.getName()));
                if (!hasCsrfHeader) {
                    operation.addParametersItem(new Parameter()
                        .in("header")
                        .name("X-CSRF-TOKEN")
                        .required(true)
                        .description("로그인 후 GET /api/v1/auth/csrf 응답의 token 값을 입력하세요.")
                        .schema(new StringSchema()));
                }
            })
        );
    }

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
