package com.venta.interfaces.rest;

import com.venta.application.command.OrderCommandService;
import com.venta.application.query.OrderQueryService;
import com.venta.domain.model.Order;
import com.venta.domain.model.Order.OrderStatus;
import org.junit.jupiter.api.*;
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

@Tag("unit")
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

    @Nested
    @DisplayName("Crear Venta Tests")
    class CrearVentaTests {

        // Verifica que crearVenta responde 201 CREATED con la orden: el servicio devuelve la
        // orden y se comprueba el status CREATED y que el cuerpo tiene el id "order-1".
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

        // Verifica que crearVenta propaga el error del servicio: el servicio devuelve un
        // Mono.error y se comprueba que el controlador propaga el RuntimeException con
        // mensaje "Sales service temporarily unavailable".
        @Test
        @DisplayName("Should propagate error when service fails to create venta")
        void shouldPropagateErrorWhenServiceFails() {
            when(orderCommandService.crearVenta(any(Order.class)))
                    .thenReturn(Mono.error(new RuntimeException("Sales service temporarily unavailable")));

            StepVerifier.create(ventaController.crearVenta(testOrder))
                    .expectErrorMatches(e -> e instanceof RuntimeException &&
                            e.getMessage().contains("Sales service temporarily unavailable"))
                    .verify();
        }
    }

    @Nested
    @DisplayName("Get Venta Tests")
    class GetVentaTests {

        // Verifica que getVenta responde 200 OK con la orden: el servicio devuelve la orden
        // por id y se comprueba el status OK y que el cuerpo tiene el id "order-1".
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

        // Verifica que getVenta propaga el error cuando la orden no existe: el servicio
        // devuelve un Mono.error y se comprueba que el controlador propaga el RuntimeException
        // con mensaje "Order not found".
        @Test
        @DisplayName("Should propagate error when venta not found")
        void shouldPropagateErrorWhenVentaNotFound() {
            when(orderQueryService.getVenta("nonexistent"))
                    .thenReturn(Mono.error(new RuntimeException("Order not found: nonexistent")));

            StepVerifier.create(ventaController.getVenta("nonexistent"))
                    .expectErrorMatches(e -> e instanceof RuntimeException &&
                            e.getMessage().contains("Order not found"))
                    .verify();
        }
    }

    @Nested
    @DisplayName("Listar Ventas Tests")
    class ListarVentasTests {

        // Verifica que listarVentas devuelve el flujo de órdenes: el servicio devuelve una
        // orden y se comprueba que el controlador la emite con el id "order-1".
        @Test
        @DisplayName("Should list all ventas")
        void shouldListVentas() {
            when(orderQueryService.listarVentas()).thenReturn(Flux.just(testOrder));

            StepVerifier.create(ventaController.listarVentas())
                    .assertNext(order -> assertThat(order.getId()).isEqualTo("order-1"))
                    .verifyComplete();
        }

        // Verifica que listarVentas devuelve un flujo vacío cuando no hay ventas: el servicio
        // devuelve Flux vacío y se comprueba que el controlador completa sin emitir elementos.
        @Test
        @DisplayName("Should return empty flux when no ventas exist")
        void shouldReturnEmptyFluxWhenNoVentasExist() {
            when(orderQueryService.listarVentas()).thenReturn(Flux.empty());

            StepVerifier.create(ventaController.listarVentas())
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Ventas Por Cliente Tests")
    class VentasPorClienteTests {

        // Verifica que ventasPorCliente devuelve las ventas del cliente: el servicio devuelve
        // una orden y se comprueba que el controlador la emite con el customerId "customer-1".
        @Test
        @DisplayName("Should list ventas by customer")
        void shouldListVentasByCustomer() {
            when(orderQueryService.ventasPorCliente("customer-1")).thenReturn(Flux.just(testOrder));

            StepVerifier.create(ventaController.ventasPorCliente("customer-1"))
                    .assertNext(order -> assertThat(order.getCustomerId()).isEqualTo("customer-1"))
                    .verifyComplete();
        }

        // Verifica que ventasPorCliente devuelve vacío cuando el cliente no tiene ventas: el
        // servicio devuelve Flux vacío y se comprueba que el controlador completa sin emitir.
        @Test
        @DisplayName("Should return empty flux when customer has no ventas")
        void shouldReturnEmptyFluxWhenCustomerHasNoVentas() {
            when(orderQueryService.ventasPorCliente("unknown")).thenReturn(Flux.empty());

            StepVerifier.create(ventaController.ventasPorCliente("unknown"))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Ventas Por Estado Tests")
    class VentasPorEstadoTests {

        // Verifica que ventasPorEstado devuelve las ventas de un estado: el servicio devuelve
        // una orden PENDING y se comprueba que el controlador la emite con estado PENDING.
        @Test
        @DisplayName("Should list ventas by status")
        void shouldListVentasByStatus() {
            when(orderQueryService.ventasPorEstado(OrderStatus.PENDING)).thenReturn(Flux.just(testOrder));

            StepVerifier.create(ventaController.ventasPorEstado(OrderStatus.PENDING))
                    .assertNext(order -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING))
                    .verifyComplete();
        }

        // Verifica que ventasPorEstado devuelve vacío cuando no hay ventas en ese estado: el
        // servicio devuelve Flux vacío y se comprueba que el controlador completa sin emitir.
        @Test
        @DisplayName("Should return empty flux when no ventas with given status")
        void shouldReturnEmptyFluxWhenNoVentasWithStatus() {
            when(orderQueryService.ventasPorEstado(OrderStatus.COMPLETED)).thenReturn(Flux.empty());

            StepVerifier.create(ventaController.ventasPorEstado(OrderStatus.COMPLETED))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Cancelar Venta Tests")
    class CancelarVentaTests {

        // Verifica que cancelarVenta responde 200 OK con la orden cancelada: el servicio
        // devuelve la orden en CANCELLED y se comprueba el status OK y el estado CANCELLED.
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

        // Verifica que cancelarVenta propaga el error cuando la cancelación falla: el servicio
        // devuelve un Mono.error y se comprueba que el controlador propaga el RuntimeException
        // con mensaje "Cannot cancel order in status".
        @Test
        @DisplayName("Should propagate error when cancellation fails")
        void shouldPropagateErrorWhenCancellationFails() {
            when(orderCommandService.cancelarVenta("order-1"))
                    .thenReturn(Mono.error(new RuntimeException("Cannot cancel order in status: COMPLETED")));

            StepVerifier.create(ventaController.cancelarVenta("order-1"))
                    .expectErrorMatches(e -> e instanceof RuntimeException &&
                            e.getMessage().contains("Cannot cancel order in status"))
                    .verify();
        }
    }

    @Nested
    @DisplayName("Actualizar Estado Tests")
    class ActualizarEstadoTests {

        // Verifica que actualizarEstado responde 200 OK con la orden actualizada: el servicio
        // devuelve la orden en COMPLETED y se comprueba el status OK y el estado COMPLETED.
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

        // Verifica que actualizarEstado propaga el error cuando la actualización falla: el
        // servicio devuelve un Mono.error y se comprueba que el controlador propaga el
        // RuntimeException con mensaje "Order not found".
        @Test
        @DisplayName("Should propagate error when status update fails")
        void shouldPropagateErrorWhenStatusUpdateFails() {
            when(orderCommandService.actualizarEstado("nonexistent", OrderStatus.COMPLETED))
                    .thenReturn(Mono.error(new RuntimeException("Order not found: nonexistent")));

            StepVerifier.create(ventaController.actualizarEstado("nonexistent", OrderStatus.COMPLETED))
                    .expectErrorMatches(e -> e instanceof RuntimeException &&
                            e.getMessage().contains("Order not found"))
                    .verify();
        }
    }
}
