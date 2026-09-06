package com.despacho.infrastructure.kafka;

import com.despacho.domain.event.DespachoResponseEvent;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Productor Kafka del servicio de despacho.
 *
 * <p>Pertenece a la capa de infraestructura y encapsula la publicación de
 * eventos hacia el flujo SAGA: la respuesta de creación de despacho y la
 * notificación de entrega. Cada envío está protegido con Resilience4j
 * {@code @CircuitBreaker} y cuenta con un fallback que registra el fallo sin
 * propagar la excepción.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DespachoProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String TOPIC = "saga.despacho.create-reply";
    private static final String DELIVERED_TOPIC = "saga.despacho.delivered";

    /**
     * Publica la respuesta de despacho en el tópico
     * {@code saga.despacho.create-reply}.
     *
     * <p>Usa el {@code orderId} como clave del mensaje. Protegido por el circuit
     * breaker {@code kafkaProducer}; ante fallo se delega en
     * {@code sendDespachoResponseFallback}.</p>
     *
     * @param event evento con el resultado del despacho a comunicar a la saga
     */
    @CircuitBreaker(name = "kafkaProducer", fallbackMethod = "sendDespachoResponseFallback")
    public void sendDespachoResponse(DespachoResponseEvent event) {
        log.info("Enviando respuesta de despacho al topic {}: {}", TOPIC, event);
        kafkaTemplate.send(TOPIC, event.getOrderId(), event);
    }

    /**
     * Publica el evento de entrega de una orden en el tópico
     * {@code saga.despacho.delivered}.
     *
     * <p>Construye un mensaje con el {@code orderId}, que también se usa como
     * clave. Protegido por el circuit breaker {@code kafkaProducer}; ante fallo
     * se delega en {@code sendDeliveredEventFallback}.</p>
     *
     * @param orderId identificador de la orden entregada
     */
    @CircuitBreaker(name = "kafkaProducer", fallbackMethod = "sendDeliveredEventFallback")
    public void sendDeliveredEvent(String orderId) {
        java.util.Map<String, String> event = java.util.Map.of("orderId", orderId);
        log.info("Enviando evento de entrega al topic {}: {}", DELIVERED_TOPIC, event);
        kafkaTemplate.send(DELIVERED_TOPIC, orderId, event);
    }

    private void sendDespachoResponseFallback(DespachoResponseEvent event, Throwable t) {
        log.error("CircuitBreaker OPEN - Failed to send despacho-response for order: {}. Error: {}", event.getOrderId(), t.getMessage());
    }

    private void sendDeliveredEventFallback(String orderId, Throwable t) {
        log.error("CircuitBreaker OPEN - Failed to send delivered event for order: {}. Error: {}", orderId, t.getMessage());
    }
}
