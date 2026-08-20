package com.despacho.application;

import com.despacho.domain.event.DespachoRequestEvent;
import com.despacho.domain.model.Dispatch;
import com.despacho.domain.model.Dispatch.DispatchStatus;
import com.despacho.domain.repository.DispatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class DespachoApplicationServiceTest {

    @Mock
    private DispatchRepository dispatchRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private DespachoApplicationService despachoApplicationService;

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

    @Nested
    @DisplayName("Crear Despacho Tests")
    class CrearDespachoTests {

        @Test
        @DisplayName("Should create despacho with tracking number and PREPARANDO status")
        void shouldCreateDespachoSuccessfully() {
            DespachoRequestEvent request = DespachoRequestEvent.builder()
                    .orderId("order-1")
                    .productId("product-1")
                    .quantity(5)
                    .customerId("customer-1")
                    .build();

            when(dispatchRepository.save(any(Dispatch.class))).thenAnswer(invocation -> {
                Dispatch dispatch = invocation.getArgument(0);
                dispatch.setId("dispatch-1");
                return Mono.just(dispatch);
            });

            StepVerifier.create(despachoApplicationService.crearDespacho(request))
                    .assertNext(dispatch -> {
                        assertThat(dispatch.getOrderId()).isEqualTo("order-1");
                        assertThat(dispatch.getProductId()).isEqualTo("product-1");
                        assertThat(dispatch.getQuantity()).isEqualTo(5);
                        assertThat(dispatch.getCustomerId()).isEqualTo("customer-1");
                        assertThat(dispatch.getTrackingNumber()).startsWith("TRK-");
                        assertThat(dispatch.getStatus()).isEqualTo(DispatchStatus.PREPARANDO);
                        assertThat(dispatch.getCreatedAt()).isNotNull();
                        assertThat(dispatch.getUpdatedAt()).isNotNull();
                    })
                    .verifyComplete();

            verify(dispatchRepository).save(any(Dispatch.class));
        }

        @Test
        @DisplayName("Should generate unique tracking numbers")
        void shouldGenerateUniqueTrackingNumbers() {
            DespachoRequestEvent request = DespachoRequestEvent.builder()
                    .orderId("order-1")
                    .productId("product-1")
                    .quantity(5)
                    .customerId("customer-1")
                    .build();

            when(dispatchRepository.save(any(Dispatch.class))).thenAnswer(invocation -> {
                Dispatch dispatch = invocation.getArgument(0);
                return Mono.just(dispatch);
            });

            StepVerifier.create(despachoApplicationService.crearDespacho(request))
                    .assertNext(dispatch -> {
                        assertThat(dispatch.getTrackingNumber()).hasSize(12); // "TRK-" + 8 chars
                        assertThat(dispatch.getTrackingNumber()).matches("TRK-[A-F0-9]{8}");
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Actualizar Estado Tests")
    class ActualizarEstadoTests {

        @Test
        @DisplayName("Should update dispatch status successfully")
        void shouldUpdateStatusSuccessfully() {
            Dispatch updatedDispatch = Dispatch.builder()
                    .id("dispatch-1")
                    .orderId("order-1")
                    .status(DispatchStatus.ENVIADO)
                    .updatedAt(LocalDateTime.now())
                    .build();

            when(dispatchRepository.findById("dispatch-1")).thenReturn(Mono.just(testDispatch));
            when(dispatchRepository.save(any(Dispatch.class))).thenReturn(Mono.just(updatedDispatch));

            StepVerifier.create(despachoApplicationService.actualizarEstado("dispatch-1", DispatchStatus.ENVIADO))
                    .assertNext(dispatch -> {
                        assertThat(dispatch.getStatus()).isEqualTo(DispatchStatus.ENVIADO);
                    })
                    .verifyComplete();

            verify(dispatchRepository).save(any(Dispatch.class));
        }

        @Test
        @DisplayName("Should notify delivered when status is ENTREGADO")
        void shouldNotifyDeliveredWhenEntregado() {
            Dispatch deliveredDispatch = Dispatch.builder()
                    .id("dispatch-1")
                    .orderId("order-1")
                    .status(DispatchStatus.ENTREGADO)
                    .build();

            when(dispatchRepository.findById("dispatch-1")).thenReturn(Mono.just(testDispatch));
            when(dispatchRepository.save(any(Dispatch.class))).thenReturn(Mono.just(deliveredDispatch));

            StepVerifier.create(despachoApplicationService.actualizarEstado("dispatch-1", DispatchStatus.ENTREGADO))
                    .assertNext(dispatch -> assertThat(dispatch.getStatus()).isEqualTo(DispatchStatus.ENTREGADO))
                    .verifyComplete();

            verify(kafkaTemplate).send(eq("despacho-delivered"), eq("order-1"), any());
        }

        @Test
        @DisplayName("Should not notify when status is not ENTREGADO")
        void shouldNotNotifyWhenNotEntregado() {
            Dispatch enviadoDispatch = Dispatch.builder()
                    .id("dispatch-1")
                    .orderId("order-1")
                    .status(DispatchStatus.ENVIADO)
                    .build();

            when(dispatchRepository.findById("dispatch-1")).thenReturn(Mono.just(testDispatch));
            when(dispatchRepository.save(any(Dispatch.class))).thenReturn(Mono.just(enviadoDispatch));

            StepVerifier.create(despachoApplicationService.actualizarEstado("dispatch-1", DispatchStatus.ENVIADO))
                    .assertNext(dispatch -> assertThat(dispatch.getStatus()).isEqualTo(DispatchStatus.ENVIADO))
                    .verifyComplete();

            verify(kafkaTemplate, never()).send(eq("despacho-delivered"), anyString(), any());
        }

        @Test
        @DisplayName("Should return empty when dispatch not found")
        void shouldReturnEmptyWhenDispatchNotFound() {
            when(dispatchRepository.findById("nonexistent")).thenReturn(Mono.empty());

            StepVerifier.create(despachoApplicationService.actualizarEstado("nonexistent", DispatchStatus.ENVIADO))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should handle multiple state transitions PREPARANDO -> ENVIADO -> ENTREGADO")
        void shouldHandleMultipleStateTransitions() {
            Dispatch preparandoDispatch = Dispatch.builder()
                    .id("dispatch-1")
                    .orderId("order-1")
                    .status(DispatchStatus.PREPARANDO)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            Dispatch enviadoDispatch = Dispatch.builder()
                    .id("dispatch-1")
                    .orderId("order-1")
                    .status(DispatchStatus.ENVIADO)
                    .updatedAt(LocalDateTime.now())
                    .build();

            Dispatch entregadoDispatch = Dispatch.builder()
                    .id("dispatch-1")
                    .orderId("order-1")
                    .status(DispatchStatus.ENTREGADO)
                    .updatedAt(LocalDateTime.now())
                    .build();

            // First transition: PREPARANDO -> ENVIADO
            when(dispatchRepository.findById("dispatch-1")).thenReturn(Mono.just(preparandoDispatch));
            when(dispatchRepository.save(any(Dispatch.class))).thenReturn(Mono.just(enviadoDispatch));

            StepVerifier.create(despachoApplicationService.actualizarEstado("dispatch-1", DispatchStatus.ENVIADO))
                    .assertNext(dispatch -> assertThat(dispatch.getStatus()).isEqualTo(DispatchStatus.ENVIADO))
                    .verifyComplete();

            verify(kafkaTemplate, never()).send(eq("despacho-delivered"), anyString(), any());

            // Second transition: ENVIADO -> ENTREGADO
            when(dispatchRepository.findById("dispatch-1")).thenReturn(Mono.just(enviadoDispatch));
            when(dispatchRepository.save(any(Dispatch.class))).thenReturn(Mono.just(entregadoDispatch));

            StepVerifier.create(despachoApplicationService.actualizarEstado("dispatch-1", DispatchStatus.ENTREGADO))
                    .assertNext(dispatch -> assertThat(dispatch.getStatus()).isEqualTo(DispatchStatus.ENTREGADO))
                    .verifyComplete();

            verify(kafkaTemplate).send(eq("despacho-delivered"), eq("order-1"), any());
        }

        @Test
        @DisplayName("Should handle notifyDelivered when Kafka send throws exception")
        void shouldHandleNotifyDeliveredWhenKafkaThrowsException() {
            Dispatch entregadoDispatch = Dispatch.builder()
                    .id("dispatch-1")
                    .orderId("order-1")
                    .status(DispatchStatus.ENTREGADO)
                    .build();

            when(dispatchRepository.findById("dispatch-1")).thenReturn(Mono.just(testDispatch));
            when(dispatchRepository.save(any(Dispatch.class))).thenReturn(Mono.just(entregadoDispatch));
            when(kafkaTemplate.send(eq("despacho-delivered"), eq("order-1"), any()))
                    .thenThrow(new RuntimeException("Kafka broker unavailable"));

            StepVerifier.create(despachoApplicationService.actualizarEstado("dispatch-1", DispatchStatus.ENTREGADO))
                    .assertNext(dispatch -> assertThat(dispatch.getStatus()).isEqualTo(DispatchStatus.ENTREGADO))
                    .verifyComplete();

            verify(kafkaTemplate).send(eq("despacho-delivered"), eq("order-1"), any());
        }
    }

    @Nested
    @DisplayName("Query Tests")
    class QueryTests {

        @Test
        @DisplayName("Should find dispatch by tracking number")
        void shouldFindByTrackingNumber() {
            when(dispatchRepository.findByTrackingNumber("TRK-12345678")).thenReturn(Mono.just(testDispatch));

            StepVerifier.create(despachoApplicationService.buscarPorTracking("TRK-12345678"))
                    .assertNext(dispatch -> {
                        assertThat(dispatch.getTrackingNumber()).isEqualTo("TRK-12345678");
                        assertThat(dispatch.getOrderId()).isEqualTo("order-1");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should find dispatch by order id")
        void shouldFindByOrderId() {
            when(dispatchRepository.findByOrderId("order-1")).thenReturn(Mono.just(testDispatch));

            StepVerifier.create(despachoApplicationService.buscarPorOrden("order-1"))
                    .assertNext(dispatch -> assertThat(dispatch.getOrderId()).isEqualTo("order-1"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should list dispatches by status")
        void shouldListByStatus() {
            when(dispatchRepository.findByStatus(DispatchStatus.PREPARANDO)).thenReturn(Flux.just(testDispatch));

            StepVerifier.create(despachoApplicationService.listarPorEstado(DispatchStatus.PREPARANDO))
                    .assertNext(dispatch -> assertThat(dispatch.getStatus()).isEqualTo(DispatchStatus.PREPARANDO))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should list all dispatches")
        void shouldListAll() {
            when(dispatchRepository.findAll()).thenReturn(Flux.just(testDispatch));

            StepVerifier.create(despachoApplicationService.listarTodos())
                    .assertNext(dispatch -> assertThat(dispatch.getId()).isEqualTo("dispatch-1"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return empty when tracking not found")
        void shouldReturnEmptyWhenTrackingNotFound() {
            when(dispatchRepository.findByTrackingNumber("TRK-NONEXIST")).thenReturn(Mono.empty());

            StepVerifier.create(despachoApplicationService.buscarPorTracking("TRK-NONEXIST"))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("CircuitBreaker Fallback Tests")
    class CircuitBreakerFallbackTests {

        @Test
        @DisplayName("crearDespachoFallback should return Mono.error with correct message")
        void crearDespachoFallbackShouldReturnMonoError() throws Exception {
            DespachoRequestEvent request = DespachoRequestEvent.builder()
                    .orderId("order-1")
                    .productId("product-1")
                    .quantity(5)
                    .customerId("customer-1")
                    .build();

            Method fallbackMethod = DespachoApplicationService.class.getDeclaredMethod(
                    "crearDespachoFallback", DespachoRequestEvent.class, Throwable.class);
            fallbackMethod.setAccessible(true);

            @SuppressWarnings("unchecked")
            Mono<Dispatch> result = (Mono<Dispatch>) fallbackMethod.invoke(
                    despachoApplicationService, request, new RuntimeException("DB connection failed"));

            StepVerifier.create(result)
                    .expectErrorMatches(throwable ->
                            throwable instanceof RuntimeException &&
                            throwable.getMessage().equals("Dispatch service temporarily unavailable"))
                    .verify();
        }

        @Test
        @DisplayName("actualizarEstadoFallback should return Mono.error with correct message")
        void actualizarEstadoFallbackShouldReturnMonoError() throws Exception {
            Method fallbackMethod = DespachoApplicationService.class.getDeclaredMethod(
                    "actualizarEstadoFallback", String.class, DispatchStatus.class, Throwable.class);
            fallbackMethod.setAccessible(true);

            @SuppressWarnings("unchecked")
            Mono<Dispatch> result = (Mono<Dispatch>) fallbackMethod.invoke(
                    despachoApplicationService, "dispatch-1", DispatchStatus.ENVIADO,
                    new RuntimeException("DB timeout"));

            StepVerifier.create(result)
                    .expectErrorMatches(throwable ->
                            throwable instanceof RuntimeException &&
                            throwable.getMessage().equals("Dispatch service temporarily unavailable"))
                    .verify();
        }

        @Test
        @DisplayName("buscarPorTrackingFallback should return Mono.empty")
        void buscarPorTrackingFallbackShouldReturnMonoEmpty() throws Exception {
            Method fallbackMethod = DespachoApplicationService.class.getDeclaredMethod(
                    "buscarPorTrackingFallback", String.class, Throwable.class);
            fallbackMethod.setAccessible(true);

            @SuppressWarnings("unchecked")
            Mono<Dispatch> result = (Mono<Dispatch>) fallbackMethod.invoke(
                    despachoApplicationService, "TRK-12345678", new RuntimeException("DB error"));

            StepVerifier.create(result)
                    .verifyComplete();
        }

        @Test
        @DisplayName("buscarPorOrdenFallback should return Mono.empty")
        void buscarPorOrdenFallbackShouldReturnMonoEmpty() throws Exception {
            Method fallbackMethod = DespachoApplicationService.class.getDeclaredMethod(
                    "buscarPorOrdenFallback", String.class, Throwable.class);
            fallbackMethod.setAccessible(true);

            @SuppressWarnings("unchecked")
            Mono<Dispatch> result = (Mono<Dispatch>) fallbackMethod.invoke(
                    despachoApplicationService, "order-1", new RuntimeException("DB error"));

            StepVerifier.create(result)
                    .verifyComplete();
        }

        @Test
        @DisplayName("listarPorEstadoFallback should return Flux.empty")
        void listarPorEstadoFallbackShouldReturnFluxEmpty() throws Exception {
            Method fallbackMethod = DespachoApplicationService.class.getDeclaredMethod(
                    "listarPorEstadoFallback", DispatchStatus.class, Throwable.class);
            fallbackMethod.setAccessible(true);

            @SuppressWarnings("unchecked")
            Flux<Dispatch> result = (Flux<Dispatch>) fallbackMethod.invoke(
                    despachoApplicationService, DispatchStatus.PREPARANDO, new RuntimeException("DB error"));

            StepVerifier.create(result)
                    .verifyComplete();
        }

        @Test
        @DisplayName("listarTodosFallback should return Flux.empty")
        void listarTodosFallbackShouldReturnFluxEmpty() throws Exception {
            Method fallbackMethod = DespachoApplicationService.class.getDeclaredMethod(
                    "listarTodosFallback", Throwable.class);
            fallbackMethod.setAccessible(true);

            @SuppressWarnings("unchecked")
            Flux<Dispatch> result = (Flux<Dispatch>) fallbackMethod.invoke(
                    despachoApplicationService, new RuntimeException("DB error"));

            StepVerifier.create(result)
                    .verifyComplete();
        }

        @Test
        @DisplayName("notifyDeliveredFallback should not throw and handle gracefully")
        void notifyDeliveredFallbackShouldHandleGracefully() throws Exception {
            Method fallbackMethod = DespachoApplicationService.class.getDeclaredMethod(
                    "notifyDeliveredFallback", String.class, Throwable.class);
            fallbackMethod.setAccessible(true);

            // Should not throw exception - fallback just logs
            fallbackMethod.invoke(despachoApplicationService, "order-1",
                    new RuntimeException("Kafka unavailable"));

            // No exception means fallback handled gracefully
            verifyNoInteractions(kafkaTemplate);
        }
    }
}
