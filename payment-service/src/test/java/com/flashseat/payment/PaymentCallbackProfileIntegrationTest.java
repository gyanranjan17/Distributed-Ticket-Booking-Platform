package com.flashseat.payment;

import com.flashseat.payment.client.BookingClient;
import com.flashseat.payment.controller.PaymentCallbackController;
import com.flashseat.payment.gateway.PaymentGateway;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("prod")
class PaymentCallbackProfileIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext applicationContext;

    @MockitoBean
    private BookingClient bookingClient;

    @MockitoBean
    private PaymentGateway paymentGateway;

    @Test
    void shouldNotRegisterCallbackController() {

        assertTrue(
                applicationContext
                        .getBeansOfType(
                                PaymentCallbackController.class
                        )
                        .isEmpty()
        );
    }

    @Test
    void shouldNotExposeCallbackEndpoint()
            throws Exception {

        mockMvc.perform(
                        post("/api/payments/callback")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                    "paymentId": 101,
                                    "transactionId": "TXN-001",
                                    "status": "SUCCESS"
                                }
                                """)
                )
                .andExpect(status().isNotFound());
    }
}
