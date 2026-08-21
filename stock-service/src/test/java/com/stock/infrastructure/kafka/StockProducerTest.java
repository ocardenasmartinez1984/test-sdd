package com.stock.infrastructure.kafka;

import com.stock.domain.event.StockReserveResponseEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("StockProducer Unit Tests")
class StockProducerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private StockProducer stockProducer;

    @Nested
    @DisplayName("SendReserveResponse Tests")
    class SendReserveResponseTests {

        @Test
        @DisplayName("Should send reserve response to correct topic with success")
        void shouldSendReserveResponseSuccess() {
            StockReserveResponseEvent event = StockReserveResponseEvent.builder()
                    .orderId("order-1")
                    .productId("product-1")
                    .success(true)
                    .build();

            stockProducer.sendReserveResponse(event);

            verify(kafkaTemplate).send("saga.stock.reserve-reply", "order-1", event);
        }

        @Test
        @DisplayName("Should send reserve response to correct topic with failure")
        void shouldSendReserveResponseFailure() {
            StockReserveResponseEvent event = StockReserveResponseEvent.builder()
                    .orderId("order-1")
                    .productId("product-1")
                    .success(false)
                    .reason("Insufficient stock")
                    .build();

            stockProducer.sendReserveResponse(event);

            verify(kafkaTemplate).send("saga.stock.reserve-reply", "order-1", event);
        }

        @Test
        @DisplayName("Should propagate exception when KafkaTemplate throws")
        void shouldPropagateExceptionWhenKafkaTemplateFails() {
            StockReserveResponseEvent event = StockReserveResponseEvent.builder()
                    .orderId("order-1")
                    .productId("product-1")
                    .success(true)
                    .build();

            when(kafkaTemplate.send(anyString(), anyString(), any())).thenThrow(new RuntimeException("Kafka broker unreachable"));

            org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () ->
                    stockProducer.sendReserveResponse(event));
        }
    }

    @Nested
    @DisplayName("CircuitBreaker Fallback Tests")
    class CircuitBreakerFallbackTests {

        @Test
        @DisplayName("sendReserveResponseFallback should handle failure gracefully without throwing")
        void sendReserveResponseFallbackShouldNotThrow() throws Exception {
            StockReserveResponseEvent event = StockReserveResponseEvent.builder()
                    .orderId("order-1")
                    .productId("product-1")
                    .success(true)
                    .build();

            Method fallback = StockProducer.class.getDeclaredMethod(
                    "sendReserveResponseFallback", StockReserveResponseEvent.class, Throwable.class);
            fallback.setAccessible(true);

            assertThatNoException().isThrownBy(() ->
                    fallback.invoke(stockProducer, event, new RuntimeException("Kafka down")));
        }

        @Test
        @DisplayName("sendReserveResponseFallback should not interact with KafkaTemplate")
        void sendReserveResponseFallbackShouldNotUseKafka() throws Exception {
            StockReserveResponseEvent event = StockReserveResponseEvent.builder()
                    .orderId("order-1")
                    .productId("product-1")
                    .success(false)
                    .reason("Insufficient stock")
                    .build();

            Method fallback = StockProducer.class.getDeclaredMethod(
                    "sendReserveResponseFallback", StockReserveResponseEvent.class, Throwable.class);
            fallback.setAccessible(true);

            fallback.invoke(stockProducer, event, new RuntimeException("Connection timeout"));

            verifyNoInteractions(kafkaTemplate);
        }
    }
}
