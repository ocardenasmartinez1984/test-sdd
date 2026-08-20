package com.stock.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.application.StockApplicationService;
import com.stock.domain.event.StockReserveEvent;
import com.stock.domain.event.StockReserveResponseEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("StockConsumer Unit Tests")
class StockConsumerTest {

    @Mock
    private StockApplicationService stockApplicationService;

    @Mock
    private StockProducer stockProducer;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private StockConsumer stockConsumer;

    @Nested
    @DisplayName("HandleStockReserve Tests")
    class HandleStockReserveTests {

        @Test
        @DisplayName("Should handle stock reserve and send success response")
        void shouldHandleStockReserveSuccess() {
            Map<String, Object> message = new HashMap<>();
            message.put("orderId", "order-1");
            message.put("productId", "product-1");
            message.put("quantity", 5);

            StockReserveEvent event = StockReserveEvent.builder()
                    .orderId("order-1")
                    .productId("product-1")
                    .quantity(5)
                    .build();

            when(objectMapper.convertValue(message, StockReserveEvent.class)).thenReturn(event);
            when(stockApplicationService.reserve("order-1", "product-1", 5)).thenReturn(Mono.just(true));
            doNothing().when(stockProducer).sendReserveResponse(any());

            stockConsumer.handleStockReserve(message);

            verify(objectMapper).convertValue(message, StockReserveEvent.class);
            verify(stockApplicationService).reserve("order-1", "product-1", 5);
        }

        @Test
        @DisplayName("Should handle stock reserve and send failure response")
        void shouldHandleStockReserveFailure() {
            Map<String, Object> message = new HashMap<>();
            message.put("orderId", "order-1");
            message.put("productId", "product-1");
            message.put("quantity", 500);

            StockReserveEvent event = StockReserveEvent.builder()
                    .orderId("order-1")
                    .productId("product-1")
                    .quantity(500)
                    .build();

            when(objectMapper.convertValue(message, StockReserveEvent.class)).thenReturn(event);
            when(stockApplicationService.reserve("order-1", "product-1", 500)).thenReturn(Mono.just(false));
            doNothing().when(stockProducer).sendReserveResponse(any());

            stockConsumer.handleStockReserve(message);

            verify(stockApplicationService).reserve("order-1", "product-1", 500);
        }
    }

    @Nested
    @DisplayName("HandleStockCompensate Tests")
    class HandleStockCompensateTests {

        @Test
        @DisplayName("Should handle stock compensate event")
        void shouldHandleStockCompensate() {
            Map<String, Object> message = new HashMap<>();
            message.put("orderId", "order-1");
            message.put("productId", "product-1");
            message.put("quantity", 5);

            StockReserveEvent event = StockReserveEvent.builder()
                    .orderId("order-1")
                    .productId("product-1")
                    .quantity(5)
                    .build();

            when(objectMapper.convertValue(message, StockReserveEvent.class)).thenReturn(event);
            when(stockApplicationService.release("order-1", "product-1", 5)).thenReturn(Mono.empty());

            stockConsumer.handleStockCompensate(message);

            verify(objectMapper).convertValue(message, StockReserveEvent.class);
            verify(stockApplicationService).release("order-1", "product-1", 5);
        }
    }

    @Nested
    @DisplayName("Edge Cases - Malformed Messages")
    class MalformedMessageTests {

        @Test
        @DisplayName("Should handle malformed message when ObjectMapper throws IllegalArgumentException")
        void shouldHandleMalformedMessage() {
            Map<String, Object> malformedMessage = new HashMap<>();
            malformedMessage.put("invalid_field", "garbage_data");

            when(objectMapper.convertValue(malformedMessage, StockReserveEvent.class))
                    .thenThrow(new IllegalArgumentException("Cannot convert value"));

            assertThrows(IllegalArgumentException.class, () ->
                    stockConsumer.handleStockReserve(malformedMessage));

            verify(stockApplicationService, never()).reserve(anyString(), anyString(), anyInt());
            verify(stockProducer, never()).sendReserveResponse(any());
        }

        @Test
        @DisplayName("Should handle empty message map - NPE from null quantity unboxing")
        void shouldHandleEmptyMessage() {
            Map<String, Object> emptyMessage = Collections.emptyMap();

            StockReserveEvent emptyEvent = StockReserveEvent.builder().build();

            when(objectMapper.convertValue(emptyMessage, StockReserveEvent.class))
                    .thenReturn(emptyEvent);

            // quantity is null in the event, unboxing to int causes NPE
            assertThrows(NullPointerException.class, () ->
                    stockConsumer.handleStockReserve(emptyMessage));
        }

        @Test
        @DisplayName("Should throw NullPointerException when quantity is null due to Integer unboxing")
        void shouldThrowNpeWhenQuantityIsNull() {
            Map<String, Object> messageWithNulls = new HashMap<>();
            messageWithNulls.put("orderId", "order-1");
            messageWithNulls.put("productId", "product-1");
            messageWithNulls.put("quantity", null);

            StockReserveEvent eventWithNulls = StockReserveEvent.builder()
                    .orderId("order-1")
                    .productId("product-1")
                    .quantity(null)
                    .build();

            when(objectMapper.convertValue(messageWithNulls, StockReserveEvent.class)).thenReturn(eventWithNulls);

            assertThrows(NullPointerException.class, () ->
                    stockConsumer.handleStockReserve(messageWithNulls));
        }

        @Test
        @DisplayName("Should handle compensate with malformed message")
        void shouldHandleCompensateMalformedMessage() {
            Map<String, Object> malformedMessage = new HashMap<>();
            malformedMessage.put("bad_key", "bad_value");

            when(objectMapper.convertValue(malformedMessage, StockReserveEvent.class))
                    .thenThrow(new IllegalArgumentException("Cannot convert value"));

            assertThrows(IllegalArgumentException.class, () ->
                    stockConsumer.handleStockCompensate(malformedMessage));

            verify(stockApplicationService, never()).release(anyString(), anyString(), anyInt());
        }
    }
}
