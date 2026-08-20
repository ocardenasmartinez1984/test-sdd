package com.venta.application.query;

import com.venta.domain.model.Order;
import com.venta.domain.model.Order.OrderStatus;
import com.venta.domain.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.Mockito.when;

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

    @Test
    @DisplayName("Should list all ventas")
    void shouldListAllVentas() {
        when(orderRepository.findAll()).thenReturn(Flux.just(testOrder));

        StepVerifier.create(orderQueryService.listarVentas())
                .assertNext(order -> assertThat(order.getId()).isEqualTo("order-1"))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should find ventas by customer")
    void shouldFindVentasByCustomer() {
        when(orderRepository.findByCustomerId("customer-1")).thenReturn(Flux.just(testOrder));

        StepVerifier.create(orderQueryService.ventasPorCliente("customer-1"))
                .assertNext(order -> assertThat(order.getCustomerId()).isEqualTo("customer-1"))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should find ventas by status")
    void shouldFindVentasByStatus() {
        when(orderRepository.findByStatus(OrderStatus.PENDING)).thenReturn(Flux.just(testOrder));

        StepVerifier.create(orderQueryService.ventasPorEstado(OrderStatus.PENDING))
                .assertNext(order -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING))
                .verifyComplete();
    }
}
