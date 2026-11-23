package com.flashseat.payment.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient bookingRestClient(
            @Value("${booking.service.base-url}") String baseUrl) {

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(
                        new org.springframework.http.client
                                .SimpleClientHttpRequestFactory() {{
                            setConnectTimeout(Duration.ofSeconds(2));
                            setReadTimeout(Duration.ofSeconds(3));
                        }}
                )
                .build();
    }
}