package com.venta.application;

import com.venta.domain.event.DespachoResponseEvent;
import com.venta.domain.event.StockReserveResponseEvent;
import com.venta.domain.model.Order;
import com.venta.domain.model.Order.OrderStatus;
import com.venta.domain.repository.OrderRepository;
import com.venta.infrastructure.kafka.VentaProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VentaApplicationServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private VentaProducer ventaProducer;

    @InjectMocks
    private VentaApplicationService ventaApplicationService;

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
            doNothing().when(ventaProducer).sendStockReserve(any());

            StepVerifier.create(ventaApplicationService.crearVenta(newOrder))
                    .assertNext(order -> {
                        assertThat(order.getId()).isEqualTo("order-1");
                        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
                    })
                    .verifyComplete();

            verify(orderRepository).save(any(Order.class));
            verify(ventaProducer).sendStockReserve(any());
        }
    }

    @Nested
    @DisplayName("Cancelar Venta Tests")
    class CancelarVentaTests {

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

            StepVerifier.create(ventaApplicationService.cancelarVenta("order-1"))
                    .assertNext(order -> assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED))
                    .verifyComplete();

            verify(ventaProducer, never()).sendStockCompensate(any());
        }

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
            doNothing().when(ventaProducer).sendStockCompensate(any());

            StepVerifier.create(ventaApplicationService.cancelarVenta("order-1"))
                    .assertNext(order -> assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED))
                    .verifyComplete();

            verify(ventaProducer).sendStockCompensate(any());
        }

        @Test
        @DisplayName("Should throw error when order not found")
        void shouldThrowErrorWhenOrderNotFound() {
            when(orderRepository.findById("nonexistent")).thenReturn(Mono.empty());

            StepVerifier.create(ventaApplicationService.cancelarVenta("nonexistent"))
                    .expectErrorMatches(e -> e instanceof RuntimeException && e.getMessage().contains("Order not found"))
                    .verify();
        }

        @Test
        @DisplayName("Should throw error when cancelling COMPLETED order")
        void shouldThrowErrorWhenCancellingCompletedOrder() {
            testOrder.setStatus(OrderStatus.COMPLETED);

            when(orderRepository.findById("order-1")).thenReturn(Mono.just(testOrder));

            StepVerifier.create(ventaApplicationService.cancelarVenta("order-1"))
                    .expectErrorMatches(e -> e instanceof RuntimeException &&
                            e.getMessage().contains("Cannot cancel order in status"))
                    .verify();
        }

        @Test
        @DisplayName("Should throw error when cancelling CANCELLED order")
        void shouldThrowErrorWhenCancellingCancelledOrder() {
            testOrder.setStatus(OrderStatus.CANCELLED);

            when(orderRepository.findById("order-1")).thenReturn(Mono.just(testOrder));

            StepVerifier.create(ventaApplicationService.cancelarVenta("order-1"))
                    .expectErrorMatches(e -> e instanceof RuntimeException &&
                            e.getMessage().contains("Cannot cancel order in status"))
                    .verify();
        }
    }

    @Nested
    @DisplayName("Handle Stock Response Tests")
    class HandleStockResponseTests {

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
            doNothing().when(ventaProducer).sendDespachoRequest(any());

            StepVerifier.create(ventaApplicationService.handleStockResponse(event))
                    .verifyComplete();

            verify(orderRepository).save(any(Order.class));
            verify(ventaProducer).sendDespachoRequest(any());
        }

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

            StepVerifier.create(ventaApplicationService.handleStockResponse(event))
                    .verifyComplete();

            verify(ventaProducer, never()).sendDespachoRequest(any());
        }
    }

    @Nested
    @DisplayName("Handle Despacho Response Tests")
    class HandleDespachoResponseTests {

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

            StepVerifier.create(ventaApplicationService.handleDespachoResponse(event))
                    .verifyComplete();

            verify(orderRepository).save(any(Order.class));
            verify(ventaProducer, never()).sendStockCompensate(any());
        }

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
            doNothing().when(ventaProducer).sendStockCompensate(any());

            StepVerifier.create(ventaApplicationService.handleDespachoResponse(event))
                    .verifyComplete();

            verify(ventaProducer).sendStockCompensate(any());
        }
    }

    @Nested
    @DisplayName("Handle Despacho Delivered Tests")
    class HandleDespachoDeliveredTests {

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

            StepVerifier.create(ventaApplicationService.handleDespachoDelivered("order-1"))
                    .verifyComplete();

            verify(orderRepository).save(any(Order.class));
        }

        @Test
        @DisplayName("Should not update status when order is not DISPATCHING")
        void shouldNotUpdateWhenNotDispatching() {
            testOrder.setStatus(OrderStatus.PENDING);

            when(orderRepository.findById("order-1")).thenReturn(Mono.just(testOrder));

            StepVerifier.create(ventaApplicationService.handleDespachoDelivered("order-1"))
                    .verifyComplete();

            verify(orderRepository, never()).save(any(Order.class));
        }
    }

    @Nested
    @DisplayName("Query Tests")
    class QueryTests {

        @Test
        @DisplayName("Should get venta by id")
        void shouldGetVentaById() {
            when(orderRepository.findById("order-1")).thenReturn(Mono.just(testOrder));

            StepVerifier.create(ventaApplicationService.getVenta("order-1"))
                    .assertNext(order -> {
                        assertThat(order.getId()).isEqualTo("order-1");
                        assertThat(order.getCustomerId()).isEqualTo("customer-1");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should throw error when venta not found")
        void shouldThrowErrorWhenVentaNotFound() {
            when(orderRepository.findById("nonexistent")).thenReturn(Mono.empty());

            StepVerifier.create(ventaApplicationService.getVenta("nonexistent"))
                    .expectErrorMatches(e -> e instanceof RuntimeException && e.getMessage().contains("Order not found"))
                    .verify();
        }

        @Test
        @DisplayName("Should list all ventas")
        void shouldListAllVentas() {
            when(orderRepository.findAll()).thenReturn(Flux.just(testOrder));

            StepVerifier.create(ventaApplicationService.listarVentas())
                    .assertNext(order -> assertThat(order.getId()).isEqualTo("order-1"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should find ventas by customer")
        void shouldFindVentasByCustomer() {
            when(orderRepository.findByCustomerId("customer-1")).thenReturn(Flux.just(testOrder));

            StepVerifier.create(ventaApplicationService.ventasPorCliente("customer-1"))
                    .assertNext(order -> assertThat(order.getCustomerId()).isEqualTo("customer-1"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should find ventas by status")
        void shouldFindVentasByStatus() {
            when(orderRepository.findByStatus(OrderStatus.PENDING)).thenReturn(Flux.just(testOrder));

            StepVerifier.create(ventaApplicationService.ventasPorEstado(OrderStatus.PENDING))
                    .assertNext(order -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should update order status")
        void shouldUpdateOrderStatus() {
            Order updatedOrder = Order.builder()
                    .id("order-1")
                    .status(OrderStatus.COMPLETED)
                    .build();

            when(orderRepository.findById("order-1")).thenReturn(Mono.just(testOrder));
            when(orderRepository.save(any(Order.class))).thenReturn(Mono.just(updatedOrder));

            StepVerifier.create(ventaApplicationService.actualizarEstado("order-1", OrderStatus.COMPLETED))
                    .assertNext(order -> assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED))
                    .verifyComplete();
        }
    }
}
