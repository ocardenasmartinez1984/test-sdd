package com.venta.application.query;

import com.venta.domain.model.Order;
import com.venta.domain.model.Order.OrderStatus;
import com.venta.domain.repository.OrderRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class OrderQueryServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderQueryService orderQueryService;

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
    @DisplayName("Get Venta Tests")
    class GetVentaTests {

        @Test
        @DisplayName("Should get venta by id")
        void shouldGetVentaById() {
            when(orderRepository.findById("order-1")).thenReturn(Mono.just(testOrder));

            StepVerifier.create(orderQueryService.getVenta("order-1"))
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

            StepVerifier.create(orderQueryService.getVenta("nonexistent"))
                    .expectErrorMatches(e -> e instanceof RuntimeException && e.getMessage().contains("Order not found"))
                    .verify();
        }
    }

    @Nested
    @DisplayName("Listar Ventas Tests")
    class ListarVentasTests {

        @Test
        @DisplayName("Should list all ventas")
        void shouldListAllVentas() {
            when(orderRepository.findAll()).thenReturn(Flux.just(testOrder));

            StepVerifier.create(orderQueryService.listarVentas())
                    .assertNext(order -> assertThat(order.getId()).isEqualTo("order-1"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return empty flux when no orders exist")
        void shouldReturnEmptyWhenNoOrdersExist() {
            when(orderRepository.findAll()).thenReturn(Flux.empty());

            StepVerifier.create(orderQueryService.listarVentas())
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Ventas Por Cliente Tests")
    class VentasPorClienteTests {

        @Test
        @DisplayName("Should find ventas by customer")
        void shouldFindVentasByCustomer() {
            when(orderRepository.findByCustomerId("customer-1")).thenReturn(Flux.just(testOrder));

            StepVerifier.create(orderQueryService.ventasPorCliente("customer-1"))
                    .assertNext(order -> assertThat(order.getCustomerId()).isEqualTo("customer-1"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return empty flux when customer has no orders")
        void shouldReturnEmptyWhenCustomerHasNoOrders() {
            when(orderRepository.findByCustomerId("unknown-customer")).thenReturn(Flux.empty());

            StepVerifier.create(orderQueryService.ventasPorCliente("unknown-customer"))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Ventas Por Estado Tests")
    class VentasPorEstadoTests {

        @Test
        @DisplayName("Should find ventas by status")
        void shouldFindVentasByStatus() {
            when(orderRepository.findByStatus(OrderStatus.PENDING)).thenReturn(Flux.just(testOrder));

            StepVerifier.create(orderQueryService.ventasPorEstado(OrderStatus.PENDING))
                    .assertNext(order -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return empty flux when no orders with given status")
        void shouldReturnEmptyWhenNoOrdersWithStatus() {
            when(orderRepository.findByStatus(OrderStatus.COMPLETED)).thenReturn(Flux.empty());

            StepVerifier.create(orderQueryService.ventasPorEstado(OrderStatus.COMPLETED))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("CircuitBreaker Fallback Tests")
    class FallbackTests {

        @Test
        @DisplayName("getVentaFallback should return Mono.error with unavailable message")
        void getVentaFallbackShouldReturnError() throws Exception {
            Method fallbackMethod = OrderQueryService.class.getDeclaredMethod(
                    "getVentaFallback", String.class, Throwable.class);
            fallbackMethod.setAccessible(true);

            @SuppressWarnings("unchecked")
            Mono<Order> result = (Mono<Order>) fallbackMethod.invoke(
                    orderQueryService, "order-1", new RuntimeException("Connection refused"));

            StepVerifier.create(result)
                    .expectErrorMatches(e -> e instanceof RuntimeException &&
                            e.getMessage().contains("Sales service temporarily unavailable"))
                    .verify();
        }

        @Test
        @DisplayName("listarVentasFallback should return empty Flux")
        void listarVentasFallbackShouldReturnEmpty() throws Exception {
            Method fallbackMethod = OrderQueryService.class.getDeclaredMethod(
                    "listarVentasFallback", Throwable.class);
            fallbackMethod.setAccessible(true);

            @SuppressWarnings("unchecked")
            Flux<Order> result = (Flux<Order>) fallbackMethod.invoke(
                    orderQueryService, new RuntimeException("Connection refused"));

            StepVerifier.create(result)
                    .verifyComplete();
        }

        @Test
        @DisplayName("ventasPorClienteFallback should return empty Flux")
        void ventasPorClienteFallbackShouldReturnEmpty() throws Exception {
            Method fallbackMethod = OrderQueryService.class.getDeclaredMethod(
                    "ventasPorClienteFallback", String.class, Throwable.class);
            fallbackMethod.setAccessible(true);

            @SuppressWarnings("unchecked")
            Flux<Order> result = (Flux<Order>) fallbackMethod.invoke(
                    orderQueryService, "customer-1", new RuntimeException("Connection refused"));

            StepVerifier.create(result)
                    .verifyComplete();
        }

        @Test
        @DisplayName("ventasPorEstadoFallback should return empty Flux")
        void ventasPorEstadoFallbackShouldReturnEmpty() throws Exception {
            Method fallbackMethod = OrderQueryService.class.getDeclaredMethod(
                    "ventasPorEstadoFallback", OrderStatus.class, Throwable.class);
            fallbackMethod.setAccessible(true);

            @SuppressWarnings("unchecked")
            Flux<Order> result = (Flux<Order>) fallbackMethod.invoke(
                    orderQueryService, OrderStatus.PENDING, new RuntimeException("Connection refused"));

            StepVerifier.create(result)
                    .verifyComplete();
        }
    }
}
