package com.venta.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.venta.application.saga.SagaOrchestrator;
import com.venta.domain.event.DespachoResponseEvent;
import com.venta.domain.event.StockReserveResponseEvent;
import com.venta.domain.repository.CartRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class VentaConsumerTest {

    @Mock
    private SagaOrchestrator sagaOrchestrator;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private VentaConsumer ventaConsumer;

    @Nested
    @DisplayName("Consume Stock Reserve Response Tests")
    class ConsumeStockReserveResponseTests {

        @Test
        @DisplayName("Should consume stock reserve response successfully")
        void shouldConsumeStockReserveResponse() {
            Map<String, Object> message = new HashMap<>();
            message.put("orderId", "order-1");
            message.put("productId", "product-1");
            message.put("success", true);

            StockReserveResponseEvent event = StockReserveResponseEvent.builder()
                    .orderId("order-1")
                    .productId("product-1")
                    .success(true)
                    .build();

            when(objectMapper.convertValue(message, StockReserveResponseEvent.class)).thenReturn(event);
            when(cartRepository.findById("order-1")).thenReturn(Mono.empty());
            when(sagaOrchestrator.handleStockResponse(event)).thenReturn(Mono.empty());

            ventaConsumer.consumeStockReserveResponse(message);

            verify(objectMapper).convertValue(message, StockReserveResponseEvent.class);
        }

        @Test
        @DisplayName("Should handle exception in stock reserve response gracefully")
        void shouldHandleExceptionInStockReserveResponse() {
            Map<String, Object> message = new HashMap<>();
            message.put("orderId", "order-1");

            when(objectMapper.convertValue(message, StockReserveResponseEvent.class))
                    .thenThrow(new IllegalArgumentException("Invalid message"));

            // Should not throw exception
            ventaConsumer.consumeStockReserveResponse(message);

            verify(sagaOrchestrator, never()).handleStockResponse(any());
        }

        @Test
        @DisplayName("Should handle empty message map gracefully")
        void shouldHandleEmptyMessageMap() {
            Map<String, Object> emptyMessage = Collections.emptyMap();

            when(objectMapper.convertValue(emptyMessage, StockReserveResponseEvent.class))
                    .thenThrow(new IllegalArgumentException("Cannot convert empty map"));

            // Should not throw exception
            ventaConsumer.consumeStockReserveResponse(emptyMessage);

            verify(sagaOrchestrator, never()).handleStockResponse(any());
        }

        @Test
        @DisplayName("Should handle message with missing required fields gracefully")
        void shouldHandleMessageWithMissingRequiredFields() {
            Map<String, Object> incompleteMessage = new HashMap<>();
            incompleteMessage.put("someKey", "someValue");
            // Missing orderId, productId, success

            StockReserveResponseEvent incompleteEvent = StockReserveResponseEvent.builder()
                    .orderId(null)
                    .productId(null)
                    .success(null)
                    .build();

            when(objectMapper.convertValue(incompleteMessage, StockReserveResponseEvent.class))
                    .thenReturn(incompleteEvent);
            when(cartRepository.findById(anyString())).thenReturn(Mono.empty());
            when(sagaOrchestrator.handleStockResponse(incompleteEvent)).thenReturn(Mono.empty());

            // Should not throw exception
            ventaConsumer.consumeStockReserveResponse(incompleteMessage);

            verify(objectMapper).convertValue(incompleteMessage, StockReserveResponseEvent.class);
        }
    }

    @Nested
    @DisplayName("Consume Despacho Response Tests")
    class ConsumeDespachoResponseTests {

        @Test
        @DisplayName("Should consume despacho response successfully")
        void shouldConsumeDespachoResponse() {
            Map<String, Object> message = new HashMap<>();
            message.put("orderId", "order-1");
            message.put("success", true);
            message.put("trackingNumber", "TRK-12345678");

            DespachoResponseEvent event = DespachoResponseEvent.builder()
                    .orderId("order-1")
                    .success(true)
                    .trackingNumber("TRK-12345678")
                    .build();

            when(objectMapper.convertValue(message, DespachoResponseEvent.class)).thenReturn(event);
            when(sagaOrchestrator.handleDespachoResponse(event)).thenReturn(Mono.empty());

            ventaConsumer.consumeDespachoResponse(message);

            verify(objectMapper).convertValue(message, DespachoResponseEvent.class);
            verify(sagaOrchestrator).handleDespachoResponse(event);
        }

        @Test
        @DisplayName("Should handle exception in despacho response gracefully")
        void shouldHandleExceptionInDespachoResponse() {
            Map<String, Object> message = new HashMap<>();
            message.put("orderId", "order-1");

            when(objectMapper.convertValue(message, DespachoResponseEvent.class))
                    .thenThrow(new IllegalArgumentException("Invalid message"));

            // Should not throw exception
            ventaConsumer.consumeDespachoResponse(message);

            verify(sagaOrchestrator, never()).handleDespachoResponse(any());
        }

        @Test
        @DisplayName("Should handle empty message map for despacho response gracefully")
        void shouldHandleEmptyMessageMapForDespachoResponse() {
            Map<String, Object> emptyMessage = Collections.emptyMap();

            when(objectMapper.convertValue(emptyMessage, DespachoResponseEvent.class))
                    .thenThrow(new IllegalArgumentException("Cannot convert empty map"));

            // Should not throw exception
            ventaConsumer.consumeDespachoResponse(emptyMessage);

            verify(sagaOrchestrator, never()).handleDespachoResponse(any());
        }
    }

    @Nested
    @DisplayName("Consume Despacho Delivered Tests")
    class ConsumeDespachoDeliveredTests {

        @Test
        @DisplayName("Should consume despacho delivered successfully")
        void shouldConsumeDespachoDelivered() {
            Map<String, Object> message = new HashMap<>();
            message.put("orderId", "order-1");

            when(sagaOrchestrator.handleDespachoDelivered("order-1")).thenReturn(Mono.empty());

            ventaConsumer.consumeDespachoDelivered(message);

            verify(sagaOrchestrator).handleDespachoDelivered("order-1");
        }

        @Test
        @DisplayName("Should handle empty message map for despacho delivered gracefully")
        void shouldHandleEmptyMessageMapForDespachoDelivered() {
            Map<String, Object> emptyMessage = Collections.emptyMap();

            // orderId will be null - handleDespachoDelivered will receive null
            when(sagaOrchestrator.handleDespachoDelivered(null)).thenReturn(Mono.empty());

            // Should not throw exception
            ventaConsumer.consumeDespachoDelivered(emptyMessage);

            verify(sagaOrchestrator).handleDespachoDelivered(null);
        }

        @Test
        @DisplayName("Should handle message with missing orderId field")
        void shouldHandleMessageWithMissingOrderId() {
            Map<String, Object> message = new HashMap<>();
            message.put("otherField", "value");

            // message.get("orderId") returns null
            when(sagaOrchestrator.handleDespachoDelivered(null)).thenReturn(Mono.empty());

            // Should not throw exception
            ventaConsumer.consumeDespachoDelivered(message);

            verify(sagaOrchestrator).handleDespachoDelivered(null);
        }
    }
}
