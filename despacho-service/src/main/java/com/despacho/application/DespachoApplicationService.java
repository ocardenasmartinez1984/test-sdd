package com.despacho.application;

import com.despacho.domain.event.DespachoRequestEvent;
import com.despacho.domain.model.Dispatch;
import com.despacho.domain.model.Dispatch.DispatchStatus;
import com.despacho.domain.repository.DispatchRepository;
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

    private void notifyDelivered(String orderId) {
        try {
            java.util.Map<String, String> event = java.util.Map.of("orderId", orderId);
            kafkaTemplate.send("despacho-delivered", orderId, event);
            log.info("Notificación de entrega enviada para orden: {}", orderId);
        } catch (Exception e) {
            log.error("Error enviando notificación de entrega: {}", e.getMessage(), e);
        }
    }

    public Mono<Dispatch> buscarPorTracking(String trackingNumber) {
        return dispatchRepository.findByTrackingNumber(trackingNumber);
    }

    public Mono<Dispatch> buscarPorOrden(String orderId) {
        return dispatchRepository.findByOrderId(orderId);
    }

    public Flux<Dispatch> listarPorEstado(DispatchStatus status) {
        return dispatchRepository.findByStatus(status);
    }

    public Flux<Dispatch> listarTodos() {
        return dispatchRepository.findAll();
    }
}
