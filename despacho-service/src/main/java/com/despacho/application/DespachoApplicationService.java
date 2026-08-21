package com.despacho.application;

import com.despacho.domain.event.DespachoRequestEvent;
import com.despacho.domain.model.Dispatch;
import com.despacho.domain.model.Dispatch.DispatchStatus;
import com.despacho.domain.repository.DispatchRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DespachoApplicationService {

    private final DispatchRepository dispatchRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @CircuitBreaker(name = "mongoDB", fallbackMethod = "crearDespachoFallback")
    public Mono<Dispatch> crearDespacho(DespachoRequestEvent request) {
        String trackingNumber = "TRK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Dispatch dispatch = Dispatch.builder()
                .orderId(request.getOrderId())
                .productId(request.getProductId())
                .quantity(request.getQuantity())
                .customerId(request.getCustomerId())
                .trackingNumber(trackingNumber)
                .status(DispatchStatus.PREPARANDO)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return dispatchRepository.save(dispatch)
                .doOnSuccess(saved -> log.info("Despacho creado con tracking: {} para orden: {}", trackingNumber, request.getOrderId()));
    }

    @CircuitBreaker(name = "mongoDB", fallbackMethod = "actualizarEstadoFallback")
    public Mono<Dispatch> actualizarEstado(String id, DispatchStatus nuevoEstado) {
        return dispatchRepository.findById(id)
                .flatMap(dispatch -> {
                    dispatch.setStatus(nuevoEstado);
                    dispatch.setUpdatedAt(LocalDateTime.now());
                    return dispatchRepository.save(dispatch);
                })
                .doOnSuccess(updated -> {
                    if (updated != null) {
                        log.info("Despacho {} actualizado a estado: {}", id, nuevoEstado);
                        if (nuevoEstado == DispatchStatus.ENTREGADO) {
                            notifyDelivered(updated.getOrderId());
                        }
                    }
                });
    }

    @CircuitBreaker(name = "kafkaProducer", fallbackMethod = "notifyDeliveredFallback")
    private void notifyDelivered(String orderId) {
        try {
            java.util.Map<String, String> event = java.util.Map.of("orderId", orderId);
            kafkaTemplate.send("saga.despacho.delivered", orderId, event);
            log.info("Notificación de entrega enviada para orden: {}", orderId);
        } catch (Exception e) {
            log.error("Error enviando notificación de entrega: {}", e.getMessage(), e);
        }
    }

    private void notifyDeliveredFallback(String orderId, Throwable t) {
        log.error("CircuitBreaker OPEN [kafkaProducer] - Failed to notify delivery for order: {}. Error: {}", orderId, t.getMessage());
    }

    @CircuitBreaker(name = "mongoDB", fallbackMethod = "buscarPorTrackingFallback")
    public Mono<Dispatch> buscarPorTracking(String trackingNumber) {
        return dispatchRepository.findByTrackingNumber(trackingNumber);
    }

    @CircuitBreaker(name = "mongoDB", fallbackMethod = "buscarPorOrdenFallback")
    public Mono<Dispatch> buscarPorOrden(String orderId) {
        return dispatchRepository.findByOrderId(orderId);
    }

    @CircuitBreaker(name = "mongoDB", fallbackMethod = "listarPorEstadoFallback")
    public Flux<Dispatch> listarPorEstado(DispatchStatus status) {
        return dispatchRepository.findByStatus(status);
    }

    @CircuitBreaker(name = "mongoDB", fallbackMethod = "listarTodosFallback")
    public Flux<Dispatch> listarTodos() {
        return dispatchRepository.findAll();
    }

    // Fallback methods
    private Mono<Dispatch> crearDespachoFallback(DespachoRequestEvent request, Throwable t) {
        log.error("CircuitBreaker OPEN [mongoDB] - crearDespacho failed for order: {}. Error: {}", request.getOrderId(), t.getMessage());
        return Mono.error(new RuntimeException("Dispatch service temporarily unavailable"));
    }

    private Mono<Dispatch> actualizarEstadoFallback(String id, DispatchStatus nuevoEstado, Throwable t) {
        log.error("CircuitBreaker OPEN [mongoDB] - actualizarEstado failed for dispatch: {}. Error: {}", id, t.getMessage());
        return Mono.error(new RuntimeException("Dispatch service temporarily unavailable"));
    }

    private Mono<Dispatch> buscarPorTrackingFallback(String trackingNumber, Throwable t) {
        log.error("CircuitBreaker OPEN [mongoDB] - buscarPorTracking failed. Error: {}", t.getMessage());
        return Mono.empty();
    }

    private Mono<Dispatch> buscarPorOrdenFallback(String orderId, Throwable t) {
        log.error("CircuitBreaker OPEN [mongoDB] - buscarPorOrden failed. Error: {}", t.getMessage());
        return Mono.empty();
    }

    private Flux<Dispatch> listarPorEstadoFallback(DispatchStatus status, Throwable t) {
        log.error("CircuitBreaker OPEN [mongoDB] - listarPorEstado failed. Error: {}", t.getMessage());
        return Flux.empty();
    }

    private Flux<Dispatch> listarTodosFallback(Throwable t) {
        log.error("CircuitBreaker OPEN [mongoDB] - listarTodos failed. Error: {}", t.getMessage());
        return Flux.empty();
    }
}
