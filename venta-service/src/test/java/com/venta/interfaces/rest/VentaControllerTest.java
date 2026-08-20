package com.venta.interfaces.rest;

import com.venta.application.command.OrderCommandService;
import com.venta.application.query.OrderQueryService;
import com.venta.domain.model.Order;
import com.venta.domain.model.Order.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VentaControllerTest {

    @Mock
    private OrderCommandService orderCommandService;

    @Mock
    private OrderQueryService orderQueryService;

    @InjectMocks
    private VentaController ventaController;

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
    @DisplayName("Should create venta and return CREATED status")
    void shouldCreateVenta() {
        when(orderCommandService.crearVenta(any(Order.class))).thenReturn(Mono.just(testOrder));

        StepVerifier.create(ventaController.crearVenta(testOrder))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                    assertThat(response.getBody()).isNotNull();
                    assertThat(response.getBody().getId()).isEqualTo("order-1");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should get venta by id")
    void shouldGetVenta() {
        when(orderQueryService.getVenta("order-1")).thenReturn(Mono.just(testOrder));

        StepVerifier.create(ventaController.getVenta("order-1"))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody()).isNotNull();
                    assertThat(response.getBody().getId()).isEqualTo("order-1");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should list all ventas")
    void shouldListVentas() {
        when(orderQueryService.listarVentas()).thenReturn(Flux.just(testOrder));

        StepVerifier.create(ventaController.listarVentas())
                .assertNext(order -> assertThat(order.getId()).isEqualTo("order-1"))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should list ventas by customer")
    void shouldListVentasByCustomer() {
        when(orderQueryService.ventasPorCliente("customer-1")).thenReturn(Flux.just(testOrder));

        StepVerifier.create(ventaController.ventasPorCliente("customer-1"))
                .assertNext(order -> assertThat(order.getCustomerId()).isEqualTo("customer-1"))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should list ventas by status")
    void shouldListVentasByStatus() {
        when(orderQueryService.ventasPorEstado(OrderStatus.PENDING)).thenReturn(Flux.just(testOrder));

        StepVerifier.create(ventaController.ventasPorEstado(OrderStatus.PENDING))
                .assertNext(order -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should cancel venta")
    void shouldCancelVenta() {
        Order cancelledOrder = Order.builder()
                .id("order-1")
                .status(OrderStatus.CANCELLED)
                .build();
        when(orderCommandService.cancelarVenta("order-1")).thenReturn(Mono.just(cancelledOrder));

        StepVerifier.create(ventaController.cancelarVenta("order-1"))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody().getStatus()).isEqualTo(OrderStatus.CANCELLED);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should update venta status")
    void shouldUpdateVentaStatus() {
        Order updatedOrder = Order.builder()
                .id("order-1")
                .status(OrderStatus.COMPLETED)
                .build();
        when(orderCommandService.actualizarEstado("order-1", OrderStatus.COMPLETED))
                .thenReturn(Mono.just(updatedOrder));

        StepVerifier.create(ventaController.actualizarEstado("order-1", OrderStatus.COMPLETED))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody().getStatus()).isEqualTo(OrderStatus.COMPLETED);
                })
                .verifyComplete();
    }
}
