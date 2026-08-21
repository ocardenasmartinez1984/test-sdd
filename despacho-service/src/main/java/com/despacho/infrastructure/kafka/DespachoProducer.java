package com.despacho.infrastructure.kafka;

import com.despacho.domain.event.DespachoResponseEvent;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DespachoProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String TOPIC = "saga.despacho.create-reply";
    private static final String DELIVERED_TOPIC = "saga.despacho.delivered";

    @CircuitBreaker(name = "kafkaProducer", fallbackMethod = "sendDespachoResponseFallback")
    public void sendDespachoResponse(DespachoResponseEvent event) {
        log.info("Enviando respuesta de despacho al topic {}: {}", TOPIC, event);
        kafkaTemplate.send(TOPIC, event.getOrderId(), event);
    }

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
