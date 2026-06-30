package com.acme.erp.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;

import com.acme.erp.web.BootstrapController;

@Configuration
public class OpenApiConfig {

    public static final String TENANT_HEADER = "X-Tenant-Id";
    public static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI erpOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("ERP Demo — AR Module API")
                        .description("""
                                Multi-tenant invoicing and accounts receivable prototype.

                                **Getting started in Swagger:**
                                1. Call **GET /api/v1/bootstrap** (no auth) — copy `tenant_id`
                                2. Click **Authorize** → set **X-Tenant-Id** = tenant_id, leave bearer empty for now
                                3. Call **POST /api/v1/auth/login** — fill **X-Tenant-Id** header AND body
                                4. Click **Authorize** again → paste JWT into **bearerAuth**
                                5. If header still fails, use **tenant_id** query param instead
                                """)
                        .version("1.0"))
                .addSecurityItem(new SecurityRequirement()
                        .addList(BEARER_SCHEME)
                        .addList(TENANT_HEADER))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT from POST /api/v1/auth/login"))
                        .addSecuritySchemes(TENANT_HEADER, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name(TENANT_HEADER)
                                .description("Tenant UUID from docker logs")));
    }

    /** Shows X-Tenant-Id as a visible header on every endpoint in Try it out. */
    @Bean
    public OperationCustomizer tenantHeaderParameter() {
        return (operation, handlerMethod) -> {
            if (!requiresTenantHeader(handlerMethod)) {
                return operation;
            }
            boolean alreadyPresent = operation.getParameters() != null && operation.getParameters().stream()
                    .anyMatch(p -> TENANT_HEADER.equals(p.getName()));
            if (!alreadyPresent) {
                operation.addParametersItem(new Parameter()
                        .in("header")
                        .name(TENANT_HEADER)
                        .required(true)
                        .description("Tenant UUID — call GET /api/v1/bootstrap first to get this value")
                        .schema(new StringSchema().example("a172c30e-ec07-405c-862d-d217496666f0")));
            }
            boolean hasQueryFallback = operation.getParameters() != null && operation.getParameters().stream()
                    .anyMatch(p -> "tenant_id".equals(p.getName()));
            if (!hasQueryFallback) {
                operation.addParametersItem(new Parameter()
                        .in("query")
                        .name("tenant_id")
                        .required(false)
                        .description("Alternative to X-Tenant-Id header (use if Swagger drops the header)")
                        .schema(new StringSchema().example("a172c30e-ec07-405c-862d-d217496666f0")));
            }
            return operation;
        };
    }

    private boolean requiresTenantHeader(HandlerMethod handlerMethod) {
        if (handlerMethod.getBeanType().equals(BootstrapController.class)) {
            return false;
        }
        return !handlerMethod.getBeanType().getSimpleName().equals("HealthController");
    }
}
