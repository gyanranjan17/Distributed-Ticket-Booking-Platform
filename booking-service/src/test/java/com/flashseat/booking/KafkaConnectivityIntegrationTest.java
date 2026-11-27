package com.flashseat.booking;

import org.apache.kafka.clients.admin.AdminClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class KafkaConnectivityIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private KafkaAdmin kafkaAdmin;

    @Test
    void shouldConfigureKafkaBeans() {
        assertNotNull(kafkaTemplate);
        assertNotNull(kafkaAdmin);
    }

    @Test
    void shouldConnectToKafkaAndRetrieveTopics() throws Exception {

        try (AdminClient adminClient = AdminClient.create(
                kafkaAdmin.getConfigurationProperties())) {

            Set<String> topics = adminClient
                    .listTopics()
                    .names()
                    .get(10, TimeUnit.SECONDS);

            assertTrue(topics.contains("payment-events"));
            assertTrue(topics.contains("booking-events"));

            System.out.println("Available Kafka topics: " + topics);
        }
    }
}