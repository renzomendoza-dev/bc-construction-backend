package com.bcconstructionservices.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Provides a {@link RestClient.Builder} bean explicitly — Spring Boot's own
 * {@code RestClientAutoConfiguration} was not activating in this app, so
 * {@link com.bcconstructionservices.user.service.keycloak.KeycloakAdminClient}
 * (the only current consumer) would otherwise fail to wire.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
