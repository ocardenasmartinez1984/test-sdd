package com.venta.application.saga;

import com.venta.domain.event.DespachoResponseEvent;
import com.venta.domain.event.StockReserveResponseEvent;
import com.venta.domain.exception.OrderNotFoundException;
import com.venta.domain.model.Order;
import com.venta.domain.model.Order.OrderStatus;
import com.venta.domain.port.DespachoEventPublisher;
import com.venta.domain.port.StockEventPublisher;
import com.venta.domain.repository.OrderRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class SagaOrchestratorTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private StockEventPublisher stockEventPublisher;

    @Mock
    private DespachoEventPublisher despachoEventPublisher;

    @InjectMocks
    private SagaOrchestrator sagaOrchestrator;

    private Order testOrder;

    @BeforeEach
    void setUp() {
        testOrder = Order.builder()
                .id("order-1")
                .customerId("customer-1")
                .productId("product-1")
                .quantity(5)
                .totalAmount(new BigDecimal("100.00"))
                .status(OrderStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("Handle Stock Response Tests")
    class HandleStockResponseTests {

        // Verifica el flujo feliz de handleStockResponse: ante una respuesta exitosa, la orden
        // pasa a STOCK_RESERVED, se guarda y se publica la solicitud de despacho (requestDespacho).
        @Test
        @DisplayName("Should set STOCK_RESERVED and send despacho request on success")
        void shouldSetStockReservedOnSuccess() {
            StockReserveResponseEvent event = StockReserveResponseEvent.builder()
                    .orderId("order-1")
                    .productId("product-1")
                    .success(true)
                    .build();

            Order reservedOrder = Order.builder()
                    .id("order-1")
                    .customerId("customer-1")
                    .productId("product-1")
                    .quantity(5)
                    .status(OrderStatus.STOCK_RESERVED)
                    .build();

            when(orderRepository.findById("order-1")).thenReturn(Mono.just(testOrder));
            when(orderRepository.save(any(Order.class))).thenReturn(Mono.just(reservedOrder));
            doNothing().when(despachoEventPublisher).requestDespacho(any());

            StepVerifier.create(sagaOrchestrator.handleStockResponse(event))
                    .verifyComplete();

            verify(orderRepository).save(any(Order.class));
            verify(despachoEventPublisher).requestDespacho(any());
        }

        // Verifica que ante una respuesta de stock fallida la orden pasa a STOCK_FAILED con
        // su motivo: prepara el evento success=false y comprueba que NUNCA se solicita despacho.
        @Test
        @DisplayName("Should set STOCK_FAILED on failure")
        void shouldSetStockFailedOnFailure() {
            StockReserveResponseEvent event = StockReserveResponseEvent.builder()
                    .orderId("order-1")
                    .productId("product-1")
                    .success(false)
                    .reason("Insufficient stock")
                    .build();

            Order failedOrder = Order.builder()
                    .id("order-1")
                    .status(OrderStatus.STOCK_FAILED)
                    .failureReason("Insufficient stock")
                    .build();

            when(orderRepository.findById("order-1")).thenReturn(Mono.just(testOrder));
            when(orderRepository.save(any(Order.class))).thenReturn(Mono.just(failedOrder));

            StepVerifier.create(sagaOrchestrator.handleStockResponse(event))
                    .verifyComplete();

            verify(despachoEventPublisher, never()).requestDespacho(any());
        }

        // Verifica que al recibir una respuesta de stock para una orden inexistente se emite
        // error: findById devuelve vacío y se comprueba que handleStockResponse propaga un
        // RuntimeException "Order not found: nonexistent" sin guardar ni pedir despacho.
        @Test
        @DisplayName("Should return error when order not found")
        void shouldReturnErrorWhenOrderNotFound() {
            StockReserveResponseEvent event = StockReserveResponseEvent.builder()
                    .orderId("nonexistent")
                    .productId("product-1")
                    .success(true)
                    .build();

            when(orderRepository.findById("nonexistent")).thenReturn(Mono.empty());

            StepVerifier.create(sagaOrchestrator.handleStockResponse(event))
                    .expectErrorMatches(e -> e instanceof RuntimeException &&
                            e.getMessage().contains("Order not found: nonexistent"))
                    .verify();

            verify(orderRepository, never()).save(any(Order.class));
            verify(despachoEventPublisher, never()).requestDespacho(any());
        }
    }

    @Nested
    @DisplayName("Handle Despacho Response Tests")
    class HandleDespachoResponseTests {

        // Verifica el flujo feliz de handleDespachoResponse: ante una respuesta exitosa la
        // orden pasa a DISPATCHING y se guarda, comprobando que NUNCA se compensa stock.
        @Test
        @DisplayName("Should set DISPATCHING on success")
        void shouldSetDispatchingOnSuccess() {
            DespachoResponseEvent event = DespachoResponseEvent.builder()
                    .orderId("order-1")
                    .success(true)
                    .trackingNumber("TRK-12345678")
                    .build();

            Order dispatchingOrder = Order.builder()
                    .id("order-1")
                    .status(OrderStatus.DISPATCHING)
                    .build();

            when(orderRepository.findById("order-1")).thenReturn(Mono.just(testOrder));
            when(orderRepository.save(any(Order.class))).thenReturn(Mono.just(dispatchingOrder));

            StepVerifier.create(sagaOrchestrator.handleDespachoResponse(event))
                    .verifyComplete();

            verify(orderRepository).save(any(Order.class));
            verify(stockEventPublisher, never()).compensateStock(any());
        }

        // Verifica que ante una respuesta de despacho fallida la orden pasa a DISPATCH_FAILED
        // y se dispara la compensación de stock (compensateStock) para revertir la reserva.
        @Test
        @DisplayName("Should set DISPATCH_FAILED and send stock compensate on failure")
        void shouldSetDispatchFailedAndCompensateOnFailure() {
            DespachoResponseEvent event = DespachoResponseEvent.builder()
                    .orderId("order-1")
                    .success(false)
                    .reason("Cannot dispatch")
                    .build();

            Order failedOrder = Order.builder()
                    .id("order-1")
                    .productId("product-1")
                    .quantity(5)
                    .status(OrderStatus.DISPATCH_FAILED)
                    .failureReason("Cannot dispatch")
                    .build();

            when(orderRepository.findById("order-1")).thenReturn(Mono.just(testOrder));
            when(orderRepository.save(any(Order.class))).thenReturn(Mono.just(failedOrder));
            doNothing().when(stockEventPublisher).compensateStock(any());

            StepVerifier.create(sagaOrchestrator.handleDespachoResponse(event))
                    .verifyComplete();

            verify(stockEventPublisher).compensateStock(any());
        }

        // Verifica que al recibir una respuesta de despacho para una orden inexistente se
        // emite error: findById devuelve vacío y se comprueba que handleDespachoResponse
        // propaga "Order not found: nonexistent" sin guardar ni compensar stock.
        @Test
        @DisplayName("Should return error when order not found")
        void shouldReturnErrorWhenOrderNotFound() {
            DespachoResponseEvent event = DespachoResponseEvent.builder()
                    .orderId("nonexistent")
                    .success(true)
                    .trackingNumber("TRK-99999999")
                    .build();

            when(orderRepository.findById("nonexistent")).thenReturn(Mono.empty());

            StepVerifier.create(sagaOrchestrator.handleDespachoResponse(event))
                    .expectErrorMatches(e -> e instanceof RuntimeException &&
                            e.getMessage().contains("Order not found: nonexistent"))
                    .verify();

            verify(orderRepository, never()).save(any(Order.class));
            verify(stockEventPublisher, never()).compensateStock(any());
        }
    }

    @Nested
    @DisplayName("Handle Despacho Delivered Tests")
    class HandleDespachoDeliveredTests {

        // Verifica que cuando la orden está en DISPATCHING y llega la entrega, pasa a COMPLETED,
        // se guarda y se confirma el stock (confirmStock) para descontar el stock físico.
        @Test
        @DisplayName("Should set COMPLETED when order is DISPATCHING")
        void shouldSetCompletedWhenDispatching() {
            testOrder.setStatus(OrderStatus.DISPATCHING);
            Order completedOrder = Order.builder()
                    .id("order-1")
                    .status(OrderStatus.COMPLETED)
                    .build();

            when(orderRepository.findById("order-1")).thenReturn(Mono.just(testOrder));
            when(orderRepository.save(any(Order.class))).thenReturn(Mono.just(completedOrder));

            StepVerifier.create(sagaOrchestrator.handleDespachoDelivered("order-1"))
                    .verifyComplete();

            verify(orderRepository).save(any(Order.class));
            // On delivery the reservation is confirmed -> physical stock decrement
            verify(stockEventPublisher).confirmStock(any());
        }

        // Verifica que si la orden no está en DISPATCHING (aquí PENDING) la entrega no cambia
        // su estado: invoca handleDespachoDelivered y comprueba que NUNCA se guarda la orden.
        @Test
        @DisplayName("Should not update status when order is not DISPATCHING")
        void shouldNotUpdateWhenNotDispatching() {
            testOrder.setStatus(OrderStatus.PENDING);

            when(orderRepository.findById("order-1")).thenReturn(Mono.just(testOrder));

            StepVerifier.create(sagaOrchestrator.handleDespachoDelivered("order-1"))
                    .verifyComplete();

            verify(orderRepository, never()).save(any(Order.class));
        }

        // Verifica que al notificar entrega de una orden inexistente se emite error:
        // findById devuelve vacío y se comprueba que handleDespachoDelivered propaga
        // "Order not found: nonexistent" sin guardar cambios.
        @Test
        @DisplayName("Should return error when order not found")
        void shouldReturnErrorWhenOrderNotFound() {
            when(orderRepository.findById("nonexistent")).thenReturn(Mono.empty());

            StepVerifier.create(sagaOrchestrator.handleDespachoDelivered("nonexistent"))
                    .expectErrorMatches(e -> e instanceof RuntimeException &&
                            e.getMessage().contains("Order not found: nonexistent"))
                    .verify();

            verify(orderRepository, never()).save(any(Order.class));
        }
    }

    @Nested
    @DisplayName("Concurrent Saga Events Tests")
    class ConcurrentSagaEventsTests {

        // Verifica el manejo concurrente en handleStockResponse: findById devuelve la orden
        // pero save falla con "Concurrent modification"; se comprueba que el error se propaga
        // y que NUNCA se solicita despacho.
        @Test
        @DisplayName("Should handle concurrent stock response - save conflict")
        void shouldHandleConcurrentStockResponseSaveConflict() {
            StockReserveResponseEvent event = StockReserveResponseEvent.builder()
                    .orderId("order-1")
                    .productId("product-1")
                    .success(true)
                    .build();

            when(orderRepository.findById("order-1")).thenReturn(Mono.just(testOrder));
            when(orderRepository.save(any(Order.class)))
                    .thenReturn(Mono.error(new RuntimeException("Concurrent modification")));

            StepVerifier.create(sagaOrchestrator.handleStockResponse(event))
                    .expectErrorMatches(e -> e instanceof RuntimeException &&
                            e.getMessage().contains("Concurrent modification"))
                    .verify();

            verify(despachoEventPublisher, never()).requestDespacho(any());
        }
    }

    @Nested
    @DisplayName("CircuitBreaker Fallback Tests")
    class FallbackTests {

        // Verifica que el fallback de handleStockResponse PROPAGA un error transitorio para
        // permitir retry/DLQ: invoca por reflexión handleStockResponseFallback con un error
        // genérico "DB down" y comprueba que el Mono emite ese RuntimeException.
        @Test
        @DisplayName("handleStockResponseFallback should PROPAGATE a transient error for retry/DLQ")
        void handleStockResponseFallbackShouldPropagateTransient() throws Exception {
            var fallbackMethod = SagaOrchestrator.class.getDeclaredMethod(
                    "handleStockResponseFallback", StockReserveResponseEvent.class, Throwable.class);
            fallbackMethod.setAccessible(true);

            StockReserveResponseEvent event = StockReserveResponseEvent.builder()
                    .orderId("order-1")
                    .build();

            @SuppressWarnings("unchecked")
            Mono<Void> result = (Mono<Void>) fallbackMethod.invoke(
                    sagaOrchestrator, event, new RuntimeException("DB down"));

            StepVerifier.create(result)
                    .expectErrorMatches(e -> e instanceof RuntimeException && e.getMessage().contains("DB down"))
                    .verify();
        }

        // Verifica que el fallback de handleDespachoResponse PROPAGA un error transitorio para
        // permitir retry/DLQ: invoca por reflexión handleDespachoResponseFallback con "DB down"
        // y comprueba que el Mono emite ese RuntimeException.
        @Test
        @DisplayName("handleDespachoResponseFallback should PROPAGATE a transient error for retry/DLQ")
        void handleDespachoResponseFallbackShouldPropagateTransient() throws Exception {
            var fallbackMethod = SagaOrchestrator.class.getDeclaredMethod(
                    "handleDespachoResponseFallback", DespachoResponseEvent.class, Throwable.class);
            fallbackMethod.setAccessible(true);

            DespachoResponseEvent event = DespachoResponseEvent.builder()
                    .orderId("order-1")
                    .build();

            @SuppressWarnings("unchecked")
            Mono<Void> result = (Mono<Void>) fallbackMethod.invoke(
                    sagaOrchestrator, event, new RuntimeException("DB down"));

            StepVerifier.create(result)
                    .expectErrorMatches(e -> e instanceof RuntimeException && e.getMessage().contains("DB down"))
                    .verify();
        }

        // Verifica que el fallback de handleDespachoDelivered PROPAGA un error transitorio
        // para permitir retry/DLQ: invoca por reflexión handleDespachoDeliveredFallback con
        // "DB down" y comprueba que el Mono emite ese RuntimeException.
        @Test
        @DisplayName("handleDespachoDeliveredFallback should PROPAGATE a transient error for retry/DLQ")
        void handleDespachoDeliveredFallbackShouldPropagateTransient() throws Exception {
            var fallbackMethod = SagaOrchestrator.class.getDeclaredMethod(
                    "handleDespachoDeliveredFallback", String.class, Throwable.class);
            fallbackMethod.setAccessible(true);

            @SuppressWarnings("unchecked")
            Mono<Void> result = (Mono<Void>) fallbackMethod.invoke(
                    sagaOrchestrator, "order-1", new RuntimeException("DB down"));

            StepVerifier.create(result)
                    .expectErrorMatches(e -> e instanceof RuntimeException && e.getMessage().contains("DB down"))
                    .verify();
        }

        // Verifica que el fallback COMPLETA VACÍO (sin error) ante una excepción terminal
        // OrderNotFoundException: invoca por reflexión handleStockResponseFallback con dicha
        // excepción y comprueba que el Mono completa sin emitir error (no se reintenta).
        @Test
        @DisplayName("fallback should COMPLETE EMPTY for a terminal OrderNotFoundException")
        void fallbackShouldCompleteEmptyForOrderNotFound() throws Exception {
            var fallbackMethod = SagaOrchestrator.class.getDeclaredMethod(
                    "handleStockResponseFallback", StockReserveResponseEvent.class, Throwable.class);
            fallbackMethod.setAccessible(true);

            StockReserveResponseEvent event = StockReserveResponseEvent.builder()
                    .orderId("order-1")
                    .build();

            @SuppressWarnings("unchecked")
            Mono<Void> result = (Mono<Void>) fallbackMethod.invoke(
                    sagaOrchestrator, event, new OrderNotFoundException("order-1"));

            StepVerifier.create(result).verifyComplete();
        }
    }
}
