package com.venta.infrastructure.kafka;

import com.venta.domain.event.DespachoRequestEvent;
import com.venta.domain.event.StockReserveEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class VentaProducerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private VentaProducer ventaProducer;

    @Nested
    @DisplayName("Reserve Stock Tests")
    class ReserveStockTests {

        @Test
        @DisplayName("Should send stock reserve event to correct topic")
        void shouldSendStockReserveEvent() {
            StockReserveEvent event = StockReserveEvent.builder()
                    .orderId("order-1")
                    .productId("product-1")
                    .quantity(5)
                    .build();

            ventaProducer.reserveStock(event);

            verify(kafkaTemplate).send("saga.stock.reserve-command", "order-1", event);
        }

        @Test
        @DisplayName("Should propagate exception when KafkaTemplate.send throws")
        void shouldPropagateExceptionWhenKafkaSendThrows() {
            StockReserveEvent event = StockReserveEvent.builder()
                    .orderId("order-1")
                    .productId("product-1")
                    .quantity(5)
                    .build();

            when(kafkaTemplate.send("saga.stock.reserve-command", "order-1", event))
                    .thenThrow(new RuntimeException("Kafka broker unavailable"));

            org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                    () -> ventaProducer.reserveStock(event));
        }
    }

    @Nested
    @DisplayName("Compensate Stock Tests")
    class CompensateStockTests {

        @Test
        @DisplayName("Should send stock compensate event to correct topic")
        void shouldSendStockCompensateEvent() {
            StockReserveEvent event = StockReserveEvent.builder()
                    .orderId("order-1")
                    .productId("product-1")
                    .quantity(5)
                    .build();

            ventaProducer.compensateStock(event);

            verify(kafkaTemplate).send("saga.stock.compensate-command", "order-1", event);
        }

        @Test
        @DisplayName("Should propagate exception when KafkaTemplate.send throws for compensate")
        void shouldPropagateExceptionWhenKafkaSendThrowsForCompensate() {
            StockReserveEvent event = StockReserveEvent.builder()
                    .orderId("order-1")
                    .productId("product-1")
                    .quantity(5)
                    .build();

            when(kafkaTemplate.send("saga.stock.compensate-command", "order-1", event))
                    .thenThrow(new RuntimeException("Kafka broker unavailable"));

            org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                    () -> ventaProducer.compensateStock(event));
        }
    }

    @Nested
    @DisplayName("Request Despacho Tests")
    class RequestDespachoTests {

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

            verify(kafkaTemplate).send("saga.despacho.create-command", "order-1", event);
        }

        @Test
        @DisplayName("Should propagate exception when KafkaTemplate.send throws for despacho")
        void shouldPropagateExceptionWhenKafkaSendThrowsForDespacho() {
            DespachoRequestEvent event = DespachoRequestEvent.builder()
                    .orderId("order-1")
                    .productId("product-1")
                    .quantity(5)
                    .customerId("customer-1")
                    .build();

            when(kafkaTemplate.send("saga.despacho.create-command", "order-1", event))
                    .thenThrow(new RuntimeException("Kafka broker unavailable"));

            org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                    () -> ventaProducer.requestDespacho(event));
        }
    }

    @Nested
    @DisplayName("CircuitBreaker Fallback Tests")
    class FallbackTests {

        @Test
        @DisplayName("reserveStockFallback should log error and not throw")
        void reserveStockFallbackShouldNotThrow() throws Exception {
            Method fallbackMethod = VentaProducer.class.getDeclaredMethod(
                    "reserveStockFallback", StockReserveEvent.class, Throwable.class);
            fallbackMethod.setAccessible(true);

            StockReserveEvent event = StockReserveEvent.builder()
                    .orderId("order-1")
                    .productId("product-1")
                    .quantity(5)
                    .build();

            assertThatCode(() -> fallbackMethod.invoke(ventaProducer, event,
                    new RuntimeException("Kafka unavailable")))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("compensateStockFallback should log error and not throw")
        void compensateStockFallbackShouldNotThrow() throws Exception {
            Method fallbackMethod = VentaProducer.class.getDeclaredMethod(
                    "compensateStockFallback", StockReserveEvent.class, Throwable.class);
            fallbackMethod.setAccessible(true);

            StockReserveEvent event = StockReserveEvent.builder()
                    .orderId("order-1")
                    .productId("product-1")
                    .quantity(5)
                    .build();

            assertThatCode(() -> fallbackMethod.invoke(ventaProducer, event,
                    new RuntimeException("Kafka unavailable")))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("requestDespachoFallback should log error and not throw")
        void requestDespachoFallbackShouldNotThrow() throws Exception {
            Method fallbackMethod = VentaProducer.class.getDeclaredMethod(
                    "requestDespachoFallback", DespachoRequestEvent.class, Throwable.class);
            fallbackMethod.setAccessible(true);

            DespachoRequestEvent event = DespachoRequestEvent.builder()
                    .orderId("order-1")
                    .productId("product-1")
                    .quantity(5)
                    .customerId("customer-1")
                    .build();

            assertThatCode(() -> fallbackMethod.invoke(ventaProducer, event,
                    new RuntimeException("Kafka unavailable")))
                    .doesNotThrowAnyException();
        }
    }
}
