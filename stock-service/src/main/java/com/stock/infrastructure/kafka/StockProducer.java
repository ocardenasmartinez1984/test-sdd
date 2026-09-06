package com.stock.infrastructure.kafka;

import com.stock.domain.event.StockReserveResponseEvent;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Adaptador de salida Kafka (capa de infraestructura) que publica las respuestas
 * de reserva de stock hacia la SAGA de ventas.
 *
 * <p>Envía los {@link StockReserveResponseEvent} al topic
 * {@code saga.stock.reserve-reply} usando el {@link KafkaTemplate}, con el
 * identificador de pedido como clave de partición. Está protegido por un circuit
 * breaker de Resilience4j (instancia {@code kafkaProducer}) con un fallback que
 * registra el fallo de publicación.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockProducer {

    private static final String TOPIC_STOCK_RESERVE_RESPONSE = "saga.stock.reserve-reply";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Publica la respuesta de una reserva de stock en el topic de réplica.
     *
     * @param event evento con el resultado de la reserva (éxito o motivo del fallo)
     */
    @CircuitBreaker(name = "kafkaProducer", fallbackMethod = "sendReserveResponseFallback")
    public void sendReserveResponse(StockReserveResponseEvent event) {
        log.info("Sending stock-reserve-response for order: {}, success: {}", event.getOrderId(), event.getSuccess());
        kafkaTemplate.send(TOPIC_STOCK_RESERVE_RESPONSE, event.getOrderId(), event);
    }

    private void sendReserveResponseFallback(StockReserveResponseEvent event, Throwable t) {
        log.error("CircuitBreaker OPEN - Failed to send stock-reserve-response for order: {}. Error: {}", event.getOrderId(), t.getMessage());
    }
}
