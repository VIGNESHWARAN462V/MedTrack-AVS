package com.medtrack.avs.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Spring Configuration for Cloud AI integration (Phase 20.1).
 * Configures a dedicated RestClient with strict connect and read timeouts.
 */
@Configuration
public class AiConfiguration {

    @Bean(name = "aiRestClient")
    public RestClient aiRestClient(AiProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(Math.max(1000, properties.getConnectTimeoutMs())));
        requestFactory.setReadTimeout(Duration.ofMillis(Math.max(1000, properties.getReadTimeoutMs())));

        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }
}
