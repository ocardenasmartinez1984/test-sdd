package com.venta.application.command;

import com.venta.domain.model.Order;
import com.venta.domain.model.Order.OrderStatus;
import com.venta.domain.port.StockEventPublisher;
import com.venta.domain.repository.OrderRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class OrderCommandServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private StockEventPublisher stockEventPublisher;

    @InjectMocks
    private OrderCommandService orderCommandService;

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
    @DisplayName("Crear Venta Tests")
    class CrearVentaTests {

        // Verifica el flujo feliz de crearVenta: guarda una orden nueva, comprueba que
        // queda con id "order-1" y estado PENDING, y que se publica el evento de reserva
        // de stock (reserveStock).
        @Test
        @DisplayName("Should create order with PENDING status and send stock reserve event")
        void shouldCreateOrderAndSendStockReserveEvent() {
            Order newOrder = Order.builder()
                    .customerId("customer-1")
                    .productId("product-1")
                    .quantity(5)
                    .totalAmount(new BigDecimal("100.00"))
                    .build();

            when(orderRepository.save(any(Order.class))).thenReturn(Mono.just(testOrder));
            doNothing().when(stockEventPublisher).reserveStock(any());

            StepVerifier.create(orderCommandService.crearVenta(newOrder))
                    .assertNext(order -> {
                        assertThat(order.getId()).isEqualTo("order-1");
                        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
                    })
                    .verifyComplete();

            verify(orderRepository).save(any(Order.class));
            verify(stockEventPublisher).reserveStock(any());
        }
    }

    @Nested
    @DisplayName("Cancelar Venta Tests")
    class CancelarVentaTests {

        // Verifica que cancelar una orden en estado PENDING la deja en CANCELLED sin
        // compensar stock: prepara la orden PENDING, invoca cancelarVenta y comprueba el
        // estado CANCELLED y que NUNCA se llama a compensateStock (aún no había reserva).
        @Test
        @DisplayName("Should cancel order in PENDING status")
        void shouldCancelPendingOrder() {
            testOrder.setStatus(OrderStatus.PENDING);
            Order cancelledOrder = Order.builder()
                    .id("order-1")
                    .customerId("customer-1")
                    .productId("product-1")
                    .quantity(5)
                    .status(OrderStatus.CANCELLED)
                    .build();

            when(orderRepository.findById("order-1")).thenReturn(Mono.just(testOrder));
            when(orderRepository.save(any(Order.class))).thenReturn(Mono.just(cancelledOrder));

            StepVerifier.create(orderCommandService.cancelarVenta("order-1"))
                    .assertNext(order -> assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED))
                    .verifyComplete();

            verify(stockEventPublisher, never()).compensateStock(any());
        }

        // Verifica que cancelar una orden en STOCK_RESERVED la deja en CANCELLED y publica
        // el evento de compensación: prepara la orden STOCK_RESERVED, invoca cancelarVenta
        // y comprueba el estado CANCELLED y que se llama a compensateStock.
        @Test
        @DisplayName("Should cancel order in STOCK_RESERVED and send compensate event")
        void shouldCancelStockReservedOrderAndSendCompensate() {
            testOrder.setStatus(OrderStatus.STOCK_RESERVED);
            Order cancelledOrder = Order.builder()
                    .id("order-1")
                    .customerId("customer-1")
                    .productId("product-1")
                    .quantity(5)
                    .status(OrderStatus.CANCELLED)
                    .build();

            when(orderRepository.findById("order-1")).thenReturn(Mono.just(testOrder));
            when(orderRepository.save(any(Order.class))).thenReturn(Mono.just(cancelledOrder));
            doNothing().when(stockEventPublisher).compensateStock(any());

            StepVerifier.create(orderCommandService.cancelarVenta("order-1"))
                    .assertNext(order -> assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED))
                    .verifyComplete();

            verify(stockEventPublisher).compensateStock(any());
        }

        // Verifica que cancelar una orden inexistente produce error: el repositorio devuelve
        // Mono.empty() en findById, y se comprueba que cancelarVenta emite un RuntimeException
        // cuyo mensaje contiene "Order not found".
        @Test
        @DisplayName("Should throw error when order not found")
        void shouldThrowErrorWhenOrderNotFound() {
            when(orderRepository.findById("nonexistent")).thenReturn(Mono.empty());

            StepVerifier.create(orderCommandService.cancelarVenta("nonexistent"))
                    .expectErrorMatches(e -> e instanceof RuntimeException && e.getMessage().contains("Order not found"))
                    .verify();
        }

        // Verifica que no se puede cancelar una orden ya COMPLETED: prepara la orden en
        // estado COMPLETED e invoca cancelarVenta, comprobando que emite un RuntimeException
        // cuyo mensaje contiene "Cannot cancel order in status".
        @Test
        @DisplayName("Should throw error when cancelling COMPLETED order")
        void shouldThrowErrorWhenCancellingCompletedOrder() {
            testOrder.setStatus(OrderStatus.COMPLETED);

            when(orderRepository.findById("order-1")).thenReturn(Mono.just(testOrder));

            StepVerifier.create(orderCommandService.cancelarVenta("order-1"))
                    .expectErrorMatches(e -> e instanceof RuntimeException &&
                            e.getMessage().contains("Cannot cancel order in status"))
                    .verify();
        }

        // Verifica que no se puede cancelar una orden ya CANCELLED: prepara la orden en
        // estado CANCELLED e invoca cancelarVenta, comprobando que emite un RuntimeException
        // cuyo mensaje contiene "Cannot cancel order in status".
        @Test
        @DisplayName("Should throw error when cancelling CANCELLED order")
        void shouldThrowErrorWhenCancellingCancelledOrder() {
            testOrder.setStatus(OrderStatus.CANCELLED);

            when(orderRepository.findById("order-1")).thenReturn(Mono.just(testOrder));

            StepVerifier.create(orderCommandService.cancelarVenta("order-1"))
                    .expectErrorMatches(e -> e instanceof RuntimeException &&
                            e.getMessage().contains("Cannot cancel order in status"))
                    .verify();
        }

        // Verifica que cancelar una orden en DISPATCHING la deja en CANCELLED y publica el
        // evento de compensación: prepara la orden DISPATCHING, invoca cancelarVenta y
        // comprueba el estado CANCELLED y que se llama a compensateStock.
        @Test
        @DisplayName("Should cancel order in DISPATCHING and send compensate event")
        void shouldCancelDispatchingOrderAndSendCompensate() {
            testOrder.setStatus(OrderStatus.DISPATCHING);
            Order cancelledOrder = Order.builder()
                    .id("order-1")
                    .customerId("customer-1")
                    .productId("product-1")
                    .quantity(5)
                    .status(OrderStatus.CANCELLED)
                    .build();

            when(orderRepository.findById("order-1")).thenReturn(Mono.just(testOrder));
            when(orderRepository.save(any(Order.class))).thenReturn(Mono.just(cancelledOrder));
            doNothing().when(stockEventPublisher).compensateStock(any());

            StepVerifier.create(orderCommandService.cancelarVenta("order-1"))
                    .assertNext(order -> assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED))
                    .verifyComplete();

            verify(stockEventPublisher).compensateStock(any());
        }
    }

    @Nested
    @DisplayName("Actualizar Estado Tests")
    class ActualizarEstadoTests {

        // Verifica que actualizarEstado cambia el estado de la orden: prepara findById y save,
        // invoca actualizarEstado a COMPLETED y comprueba que la orden emitida queda en
        // estado COMPLETED.
        @Test
        @DisplayName("Should update order status")
        void shouldUpdateOrderStatus() {
            Order updatedOrder = Order.builder()
                    .id("order-1")
                    .status(OrderStatus.COMPLETED)
                    .build();

            when(orderRepository.findById("order-1")).thenReturn(Mono.just(testOrder));
            when(orderRepository.save(any(Order.class))).thenReturn(Mono.just(updatedOrder));

            StepVerifier.create(orderCommandService.actualizarEstado("order-1", OrderStatus.COMPLETED))
                    .assertNext(order -> assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("CircuitBreaker Fallback Tests")
    class FallbackTests {

        // Verifica el fallback del circuit breaker de crearVenta: invoca por reflexión
        // crearVentaFallback con una excepción y comprueba que devuelve un Mono.error cuyo
        // mensaje contiene "Sales service temporarily unavailable".
        @Test
        @DisplayName("crearVentaFallback should return Mono.error with unavailable message")
        void crearVentaFallbackShouldReturnError() throws Exception {
            Method fallbackMethod = OrderCommandService.class.getDeclaredMethod(
                    "crearVentaFallback", Order.class, Throwable.class);
            fallbackMethod.setAccessible(true);

            @SuppressWarnings("unchecked")
            Mono<Order> result = (Mono<Order>) fallbackMethod.invoke(
                    orderCommandService, testOrder, new RuntimeException("Connection refused"));

            StepVerifier.create(result)
                    .expectErrorMatches(e -> e instanceof RuntimeException &&
                            e.getMessage().contains("Sales service temporarily unavailable"))
                    .verify();
        }

        // Verifica el fallback del circuit breaker de cancelarVenta: invoca por reflexión
        // cancelarVentaFallback con una excepción y comprueba que devuelve un Mono.error cuyo
        // mensaje contiene "Sales service temporarily unavailable".
        @Test
        @DisplayName("cancelarVentaFallback should return Mono.error with unavailable message")
        void cancelarVentaFallbackShouldReturnError() throws Exception {
            Method fallbackMethod = OrderCommandService.class.getDeclaredMethod(
                    "cancelarVentaFallback", String.class, Throwable.class);
            fallbackMethod.setAccessible(true);

            @SuppressWarnings("unchecked")
            Mono<Order> result = (Mono<Order>) fallbackMethod.invoke(
                    orderCommandService, "order-1", new RuntimeException("Connection refused"));

            StepVerifier.create(result)
                    .expectErrorMatches(e -> e instanceof RuntimeException &&
                            e.getMessage().contains("Sales service temporarily unavailable"))
                    .verify();
        }

        // Verifica el fallback del circuit breaker de actualizarEstado: invoca por reflexión
        // actualizarEstadoFallback con una excepción y comprueba que devuelve un Mono.error
        // cuyo mensaje contiene "Sales service temporarily unavailable".
        @Test
        @DisplayName("actualizarEstadoFallback should return Mono.error with unavailable message")
        void actualizarEstadoFallbackShouldReturnError() throws Exception {
            Method fallbackMethod = OrderCommandService.class.getDeclaredMethod(
                    "actualizarEstadoFallback", String.class, OrderStatus.class, Throwable.class);
            fallbackMethod.setAccessible(true);

            @SuppressWarnings("unchecked")
            Mono<Order> result = (Mono<Order>) fallbackMethod.invoke(
                    orderCommandService, "order-1", OrderStatus.COMPLETED, new RuntimeException("Connection refused"));

            StepVerifier.create(result)
                    .expectErrorMatches(e -> e instanceof RuntimeException &&
                            e.getMessage().contains("Sales service temporarily unavailable"))
                    .verify();
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        // Verifica el manejo de cancelación concurrente: la orden está PENDING en findById
        // pero save falla con "Optimistic locking failure"; se comprueba que cancelarVenta
        // propaga ese RuntimeException.
        @Test
        @DisplayName("Should handle concurrent cancellation - order already cancelled between findById and save")
        void shouldHandleConcurrentCancellation() {
            testOrder.setStatus(OrderStatus.PENDING);

            when(orderRepository.findById("order-1")).thenReturn(Mono.just(testOrder));
            when(orderRepository.save(any(Order.class)))
                    .thenReturn(Mono.error(new RuntimeException("Optimistic locking failure")));

            StepVerifier.create(orderCommandService.cancelarVenta("order-1"))
                    .expectErrorMatches(e -> e instanceof RuntimeException &&
                            e.getMessage().contains("Optimistic locking failure"))
                    .verify();
        }
    }
}
