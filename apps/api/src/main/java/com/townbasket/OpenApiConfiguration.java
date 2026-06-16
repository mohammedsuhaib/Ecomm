package com.townbasket;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Minimal springdoc-openapi configuration. The generated {@code /v3/api-docs}
 * and Swagger UI ({@code /swagger-ui.html}) are the single source of truth for
 * the generated TypeScript client (ARCHITECTURE §6).
 */
@Configuration
class OpenApiConfiguration {

    @Bean
    OpenAPI townBasketOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Town Basket API")
                .description("Quick-commerce storefront API (catalog, serviceability, cart, "
                        + "orders, payments, inventory, notifications).")
                .version("v1"));
    }
}
