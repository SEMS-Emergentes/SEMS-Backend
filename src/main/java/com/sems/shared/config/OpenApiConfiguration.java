package com.sems.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Title and description of the API documentation.
 *
 * <p>Without this bean springdoc falls back to the generic "OpenAPI definition"
 * heading, which says nothing about the product. The wording matches the C#
 * service so that both APIs document themselves the same way.
 *
 * <p>English is the default language for messages, user interface and
 * documentation interface across every product of the solution.
 */
@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI semsOpenApi() {
        return new OpenAPI().info(new Info()
                .title("SEMS API")
                .version("v1")
                .description("Smart Energy Management System. "
                        + "Modular monolith: one bounded context per module."));
    }
}
