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

        // Verifica que reserveStock envía el evento al topic correcto: invoca reserveStock y
        // comprueba que KafkaTemplate.send se llama con "saga.stock.reserve-command", la clave
        // "order-1" y el evento.
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

        // Verifica que reserveStock propaga la excepción cuando KafkaTemplate.send lanza:
        // configura send para lanzar "Kafka broker unavailable" y comprueba que reserveStock
        // relanza el RuntimeException.
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

        // Verifica que compensateStock envía el evento al topic correcto: invoca compensateStock
        // y comprueba que KafkaTemplate.send se llama con "saga.stock.compensate-command", la
        // clave "order-1" y el evento.
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

        // Verifica que compensateStock propaga la excepción cuando KafkaTemplate.send lanza:
        // configura send para lanzar "Kafka broker unavailable" y comprueba que compensateStock
        // relanza el RuntimeException.
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

        // Verifica que requestDespacho envía el evento al topic correcto: invoca requestDespacho
        // y comprueba que KafkaTemplate.send se llama con "saga.despacho.create-command", la
        // clave "order-1" y el evento.
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

        // Verifica que requestDespacho propaga la excepción cuando KafkaTemplate.send lanza:
        // configura send para lanzar "Kafka broker unavailable" y comprueba que requestDespacho
        // relanza el RuntimeException.
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

        // Verifica el fallback del circuit breaker para reserveStock: invoca por reflexión
        // reserveStockFallback con una excepción y comprueba que registra el error sin lanzar
        // (no propaga ninguna excepción).
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

        // Verifica el fallback del circuit breaker para compensateStock: invoca por reflexión
        // compensateStockFallback con una excepción y comprueba que registra el error sin
        // lanzar (no propaga ninguna excepción).
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

        // Verifica el fallback del circuit breaker para requestDespacho: invoca por reflexión
        // requestDespachoFallback con una excepción y comprueba que registra el error sin
        // lanzar (no propaga ninguna excepción).
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
