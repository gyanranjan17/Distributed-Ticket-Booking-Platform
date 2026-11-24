package com.flashseat.payment;

import com.flashseat.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.flashseat.payment.client.BookingClient;
import com.flashseat.payment.dto.BookingResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PaymentControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentRepository paymentRepository;

    @MockitoBean
    private BookingClient bookingClient;

    @BeforeEach
    void mockBookingService() {
        paymentRepository.deleteAllInBatch();
        when(bookingClient.getBooking(anyLong()))
                .thenAnswer(invocation -> {

                    Long bookingId = invocation.getArgument(0);

                    return new BookingResponse(
                            bookingId,
                            10L,
                            1L,
                            "PENDING",
                            new BigDecimal("500.00"),
                            LocalDateTime.now(),
                            LocalDateTime.now().plusMinutes(10)
                    );
                });
    }

    @Test
    void validPaymentRequestShouldReturn201() throws Exception {

        String key = UUID.randomUUID().toString();

        String requestBody = """
                {
                    "bookingId": 101,
                    "amount": 500.00,
                    "idempotencyKey": "%s"
                }
                """.formatted(key);

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingId").value(101))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.paymentId").exists());
    }

    @Test
    void missingBookingIdShouldReturn400() throws Exception {

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "amount": 500.00,
                                    "idempotencyKey": "test-key"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.bookingId").exists());
    }

    @Test
    void negativeAmountShouldReturn400() throws Exception {

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "bookingId": 101,
                                    "amount": -100,
                                    "idempotencyKey": "test-key"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.amount").exists());
    }

    @Test
    void blankIdempotencyKeyShouldReturn400() throws Exception {

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "bookingId": 101,
                                    "amount": 500.00,
                                    "idempotencyKey": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.idempotencyKey").exists());
    }

    @Test
    void conflictingIdempotencyKeyShouldReturn409() throws Exception {

        String key = UUID.randomUUID().toString();

        String firstRequest = """
                {
                    "bookingId": 101,
                    "amount": 500.00,
                    "idempotencyKey": "%s"
                }
                """.formatted(key);

        String conflictingRequest = """
                {
                    "bookingId": 101,
                    "amount": 700.00,
                    "idempotencyKey": "%s"
                }
                """.formatted(key);

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstRequest))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conflictingRequest))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());
    }
}
