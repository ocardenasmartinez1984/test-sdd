package com.despacho.interfaces.rest;

import com.despacho.application.DespachoApplicationService;
import com.despacho.domain.model.Dispatch;
import com.despacho.domain.model.Dispatch.DispatchStatus;
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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DespachoControllerTest {

    @Mock
    private DespachoApplicationService despachoApplicationService;

    @InjectMocks
    private DespachoController despachoController;

    private Dispatch testDispatch;

    @BeforeEach
    void setUp() {
        testDispatch = Dispatch.builder()
                .id("dispatch-1")
                .orderId("order-1")
                .productId("product-1")
                .quantity(5)
                .customerId("customer-1")
                .trackingNumber("TRK-12345678")
                .status(DispatchStatus.PREPARANDO)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Should list all dispatches")
    void shouldListAllDispatches() {
        when(despachoApplicationService.listarTodos()).thenReturn(Flux.just(testDispatch));

        StepVerifier.create(despachoController.listarTodos())
                .assertNext(dispatch -> {
                    assertThat(dispatch.getId()).isEqualTo("dispatch-1");
                    assertThat(dispatch.getOrderId()).isEqualTo("order-1");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should find dispatch by tracking number")
    void shouldFindByTrackingNumber() {
        when(despachoApplicationService.buscarPorTracking("TRK-12345678")).thenReturn(Mono.just(testDispatch));

        StepVerifier.create(despachoController.buscarPorTracking("TRK-12345678"))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody()).isNotNull();
                    assertThat(response.getBody().getTrackingNumber()).isEqualTo("TRK-12345678");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return 404 when tracking not found")
    void shouldReturn404WhenTrackingNotFound() {
        when(despachoApplicationService.buscarPorTracking("TRK-NONEXIST")).thenReturn(Mono.empty());

        StepVerifier.create(despachoController.buscarPorTracking("TRK-NONEXIST"))
                .assertNext(response -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should find dispatch by order id")
    void shouldFindByOrderId() {
        when(despachoApplicationService.buscarPorOrden("order-1")).thenReturn(Mono.just(testDispatch));

        StepVerifier.create(despachoController.buscarPorOrden("order-1"))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody()).isNotNull();
                    assertThat(response.getBody().getOrderId()).isEqualTo("order-1");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return 404 when order not found")
    void shouldReturn404WhenOrderNotFound() {
        when(despachoApplicationService.buscarPorOrden("nonexistent")).thenReturn(Mono.empty());

        StepVerifier.create(despachoController.buscarPorOrden("nonexistent"))
                .assertNext(response -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should list dispatches by status")
    void shouldListByStatus() {
        when(despachoApplicationService.listarPorEstado(DispatchStatus.PREPARANDO)).thenReturn(Flux.just(testDispatch));

        StepVerifier.create(despachoController.listarPorEstado(DispatchStatus.PREPARANDO))
                .assertNext(dispatch -> assertThat(dispatch.getStatus()).isEqualTo(DispatchStatus.PREPARANDO))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should update dispatch status")
    void shouldUpdateStatus() {
        Dispatch updatedDispatch = Dispatch.builder()
                .id("dispatch-1")
                .orderId("order-1")
                .status(DispatchStatus.ENVIADO)
                .build();

        when(despachoApplicationService.actualizarEstado("dispatch-1", DispatchStatus.ENVIADO))
                .thenReturn(Mono.just(updatedDispatch));

        StepVerifier.create(despachoController.actualizarEstado("dispatch-1", DispatchStatus.ENVIADO))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody().getStatus()).isEqualTo(DispatchStatus.ENVIADO);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return 404 when updating non-existent dispatch")
    void shouldReturn404WhenUpdatingNonExistentDispatch() {
        when(despachoApplicationService.actualizarEstado("nonexistent", DispatchStatus.ENVIADO))
                .thenReturn(Mono.empty());

        StepVerifier.create(despachoController.actualizarEstado("nonexistent", DispatchStatus.ENVIADO))
                .assertNext(response -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND))
                .verifyComplete();
    }
}
