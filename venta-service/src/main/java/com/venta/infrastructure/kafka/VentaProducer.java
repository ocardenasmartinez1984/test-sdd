package com.venta.infrastructure.kafka;

import com.venta.domain.event.DespachoRequestEvent;
import com.venta.domain.event.StockReserveEvent;
import com.venta.domain.port.DespachoEventPublisher;
import com.venta.domain.port.StockEventPublisher;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Adaptador de salida Kafka que implementa los puertos {@link StockEventPublisher}
 * y {@link DespachoEventPublisher}.
 *
 * <p>Traduce las intenciones del dominio (reservar, compensar o confirmar stock,
 * y solicitar despacho) en mensajes publicados en los tópicos SAGA
 * {@code saga.stock.*-command} y {@code saga.despacho.create-command}, usando el
 * id de orden como clave de partición. Cada envío está protegido por un circuit
 * breaker {@code kafkaProducer} cuyo fallback solo registra el fallo.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VentaProducer implements StockEventPublisher, DespachoEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String STOCK_RESERVE_TOPIC = "saga.stock.reserve-command";
    private static final String STOCK_COMPENSATE_TOPIC = "saga.stock.compensate-command";
    private static final String STOCK_CONFIRM_TOPIC = "saga.stock.confirm-command";
    private static final String DESPACHO_REQUEST_TOPIC = "saga.despacho.create-command";

    /**
     * Publica un comando de reserva de stock en el tópico correspondiente.
     *
     * @param event evento con orden, producto y cantidad a reservar
     */
    @Override
    @CircuitBreaker(name = "kafkaProducer", fallbackMethod = "reserveStockFallback")
    public void reserveStock(StockReserveEvent event) {
        log.info("Sending stock-reserve event: {}", event);
        kafkaTemplate.send(STOCK_RESERVE_TOPIC, event.getOrderId(), event);
    }

    /**
     * Publica un comando de compensación (liberación) de stock.
     *
     * @param event evento con orden, producto y cantidad a liberar
     */
    @Override
    @CircuitBreaker(name = "kafkaProducer", fallbackMethod = "compensateStockFallback")
    public void compensateStock(StockReserveEvent event) {
        log.info("Sending stock-compensate event: {}", event);
        kafkaTemplate.send(STOCK_COMPENSATE_TOPIC, event.getOrderId(), event);
    }

    /**
     * Publica un comando de confirmación de stock (descuento definitivo).
     *
     * @param event evento con orden, producto y cantidad a confirmar
     */
    @Override
    @CircuitBreaker(name = "kafkaProducer", fallbackMethod = "confirmStockFallback")
    public void confirmStock(StockReserveEvent event) {
        log.info("Sending stock-confirm event: {}", event);
        kafkaTemplate.send(STOCK_CONFIRM_TOPIC, event.getOrderId(), event);
    }

    /**
     * Publica una solicitud de despacho para una orden.
     *
     * @param event evento con orden, producto, cantidad y cliente a despachar
     */
    @Override
    @CircuitBreaker(name = "kafkaProducer", fallbackMethod = "requestDespachoFallback")
    public void requestDespacho(DespachoRequestEvent event) {
        log.info("Sending despacho-request event: {}", event);
        kafkaTemplate.send(DESPACHO_REQUEST_TOPIC, event.getOrderId(), event);
    }

    // Fallback methods
    private void reserveStockFallback(StockReserveEvent event, Throwable t) {
        log.error("CircuitBreaker OPEN - Failed to send stock-reserve for order: {}. Error: {}", event.getOrderId(), t.getMessage());
    }

    private void compensateStockFallback(StockReserveEvent event, Throwable t) {
        log.error("CircuitBreaker OPEN - Failed to send stock-compensate for order: {}. Error: {}", event.getOrderId(), t.getMessage());
    }

    private void confirmStockFallback(StockReserveEvent event, Throwable t) {
        log.error("CircuitBreaker OPEN - Failed to send stock-confirm for order: {}. Error: {}", event.getOrderId(), t.getMessage());
    }

    private void requestDespachoFallback(DespachoRequestEvent event, Throwable t) {
        log.error("CircuitBreaker OPEN - Failed to send despacho-request for order: {}. Error: {}", event.getOrderId(), t.getMessage());
    }
}
