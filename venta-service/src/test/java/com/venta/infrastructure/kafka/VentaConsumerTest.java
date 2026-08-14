package com.venta.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.venta.application.VentaApplicationService;
import com.venta.domain.event.DespachoResponseEvent;
import com.venta.domain.event.StockReserveResponseEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VentaConsumerTest {

    @Mock
    private VentaApplicationService ventaApplicationService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private VentaConsumer ventaConsumer;

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
        when(ventaApplicationService.handleStockResponse(event)).thenReturn(Mono.empty());

        ventaConsumer.consumeStockReserveResponse(message);

        verify(objectMapper).convertValue(message, StockReserveResponseEvent.class);
        verify(ventaApplicationService).handleStockResponse(event);
    }

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
        when(ventaApplicationService.handleDespachoResponse(event)).thenReturn(Mono.empty());

        ventaConsumer.consumeDespachoResponse(message);

        verify(objectMapper).convertValue(message, DespachoResponseEvent.class);
        verify(ventaApplicationService).handleDespachoResponse(event);
    }

    @Test
    @DisplayName("Should consume despacho delivered successfully")
    void shouldConsumeDespachoDelivered() {
        Map<String, Object> message = new HashMap<>();
        message.put("orderId", "order-1");

        when(ventaApplicationService.handleDespachoDelivered("order-1")).thenReturn(Mono.empty());

        ventaConsumer.consumeDespachoDelivered(message);

        verify(ventaApplicationService).handleDespachoDelivered("order-1");
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

        verify(ventaApplicationService, never()).handleStockResponse(any());
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

        verify(ventaApplicationService, never()).handleDespachoResponse(any());
    }
}
