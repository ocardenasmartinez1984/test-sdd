package com.stock.infrastructure.kafka;

import com.stock.domain.event.StockReserveResponseEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StockProducerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private StockProducer stockProducer;

    @Test
    @DisplayName("Should send reserve response to correct topic with success")
    void shouldSendReserveResponseSuccess() {
        StockReserveResponseEvent event = StockReserveResponseEvent.builder()
                .orderId("order-1")
                .productId("product-1")
                .success(true)
                .build();

        stockProducer.sendReserveResponse(event);

        verify(kafkaTemplate).send("stock-reserve-response", "order-1", event);
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

        verify(kafkaTemplate).send("stock-reserve-response", "order-1", event);
    }
}
