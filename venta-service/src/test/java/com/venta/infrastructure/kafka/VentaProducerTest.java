package com.venta.infrastructure.kafka;

import com.venta.domain.event.DespachoRequestEvent;
import com.venta.domain.event.StockReserveEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class VentaProducerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private VentaProducer ventaProducer;

    @Test
    @DisplayName("Should send stock reserve event to correct topic")
    void shouldSendStockReserveEvent() {
        StockReserveEvent event = StockReserveEvent.builder()
                .orderId("order-1")
                .productId("product-1")
                .quantity(5)
                .build();

        ventaProducer.reserveStock(event);

        verify(kafkaTemplate).send("stock-reserve", "order-1", event);
    }

    @Test
    @DisplayName("Should send stock compensate event to correct topic")
    void shouldSendStockCompensateEvent() {
        StockReserveEvent event = StockReserveEvent.builder()
                .orderId("order-1")
                .productId("product-1")
                .quantity(5)
                .build();

        ventaProducer.compensateStock(event);

        verify(kafkaTemplate).send("stock-compensate", "order-1", event);
    }

    @Test
    @DisplayName("Should send despacho request event to correct topic")
    void shouldSendDespachoRequestEvent() {
        DespachoRequestEvent event = DespachoRequestEvent.builder()
                .orderId("order-1")
                .productId("product-1")
                .quantity(5)
                .customerId("customer-1")
                .build();

        ventaProducer.requestDespacho(event);

        verify(kafkaTemplate).send("despacho-request", "order-1", event);
    }
}
