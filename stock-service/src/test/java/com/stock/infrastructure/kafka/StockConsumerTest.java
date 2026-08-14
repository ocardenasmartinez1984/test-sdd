package com.stock.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.application.StockApplicationService;
import com.stock.domain.event.StockReserveEvent;
import com.stock.domain.event.StockReserveResponseEvent;
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
class StockConsumerTest {

    @Mock
    private StockApplicationService stockApplicationService;

    @Mock
    private StockProducer stockProducer;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private StockConsumer stockConsumer;

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
