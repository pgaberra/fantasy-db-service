package com.fantasy.db.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    /**
     * Defines a stable OpenAPI document. The server URL is pinned to "/" (rather than
     * springdoc's request-derived default, which leaks the runtime port) so that the
     * generated spec is deterministic — see OpenApiSpecSnapshotTest.
     */
    @Bean
    public OpenAPI dbServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Fantasy DB Service API")
                        .version("v1")
                        .description("User persistence API for the fantasy hockey tool."))
                .servers(List.of(new Server().url("/")));
    }
}
