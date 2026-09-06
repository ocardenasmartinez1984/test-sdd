package com.stock.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.application.StockApplicationService;
import com.stock.domain.event.StockReserveEvent;
import com.stock.domain.event.StockReserveResponseEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Adaptador de entrada Kafka (capa de infraestructura) que consume los comandos
 * de stock de la SAGA de ventas.
 *
 * <p>Escucha los topics {@code saga.stock.*-command}, deserializa cada mensaje a
 * {@link StockReserveEvent} mediante {@link ObjectMapper} e invoca el
 * {@link StockApplicationService} para reservar, compensar o confirmar stock.
 * En el caso de la reserva, publica el resultado a través del
 * {@link StockProducer} para que el orquestador de la SAGA continúe o compense
 * el flujo.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockConsumer {

    private final StockApplicationService stockApplicationService;
    private final StockProducer stockProducer;
    private final ObjectMapper objectMapper;

    /**
     * Procesa el comando de reserva de stock del topic
     * {@code saga.stock.reserve-command}.
     *
     * <p>Convierte el mensaje a {@link StockReserveEvent}, intenta reservar la
     * cantidad indicada y publica un {@link StockReserveResponseEvent} con el
     * resultado (éxito o motivo del fallo) mediante el {@link StockProducer}. Los
     * errores durante el procesamiento se registran sin propagarse.</p>
     *
     * @param message mensaje Kafka recibido como mapa de atributos del evento
     */
    @KafkaListener(topics = "saga.stock.reserve-command", groupId = "stock-service-group")
    public void handleStockReserve(Map<String, Object> message) {
        log.info("Received saga.stock.reserve-command event: {}", message);

        StockReserveEvent event = objectMapper.convertValue(message, StockReserveEvent.class);

        stockApplicationService.reserve(event.getOrderId(), event.getProductId(), event.getQuantity())
                .subscribe(success -> {
                    StockReserveResponseEvent response = StockReserveResponseEvent.builder()
                            .sagaId(event.getSagaId())
                            .orderId(event.getOrderId())
                            .productId(event.getProductId())
                            .success(success)
                            .reason(success ? null : "Insufficient stock or product not found")
                            .build();

                    stockProducer.sendReserveResponse(response);
                }, error -> log.error("Error processing stock-reserve for order {}: {}", event.getOrderId(), error.getMessage()));
    }

    /**
     * Procesa el comando de compensación del topic
     * {@code saga.stock.compensate-command}.
     *
     * <p>Convierte el mensaje a {@link StockReserveEvent} y libera la reserva de
     * stock asociada al pedido invocando
     * {@link StockApplicationService#release}. No publica respuesta; solo
     * registra el resultado o el posible error.</p>
     *
     * @param message mensaje Kafka recibido como mapa de atributos del evento
     */
    @KafkaListener(topics = "saga.stock.compensate-command", groupId = "stock-service-group")
    public void handleStockCompensate(Map<String, Object> message) {
        log.info("Received saga.stock.compensate-command event: {}", message);

        StockReserveEvent event = objectMapper.convertValue(message, StockReserveEvent.class);

        stockApplicationService.release(event.getOrderId(), event.getProductId(), event.getQuantity())
                .subscribe(
                        unused -> log.info("Stock compensation completed for order: {}", event.getOrderId()),
                        error -> log.error("Error processing stock-compensate for order {}: {}", event.getOrderId(), error.getMessage()),
                        () -> log.info("Stock compensation completed for order: {}", event.getOrderId())
                );
    }

    /**
     * Procesa el comando de confirmación del topic
     * {@code saga.stock.confirm-command}.
     *
     * <p>Convierte el mensaje a {@link StockReserveEvent} y consolida la salida
     * definitiva de stock del pedido invocando
     * {@link StockApplicationService#confirmDispatch}. No publica respuesta; solo
     * registra el resultado o el posible error.</p>
     *
     * @param message mensaje Kafka recibido como mapa de atributos del evento
     */
    @KafkaListener(topics = "saga.stock.confirm-command", groupId = "stock-service-group")
    public void handleStockConfirm(Map<String, Object> message) {
        log.info("Received saga.stock.confirm-command event: {}", message);

        StockReserveEvent event = objectMapper.convertValue(message, StockReserveEvent.class);

        stockApplicationService.confirmDispatch(event.getOrderId(), event.getProductId(), event.getQuantity())
                .subscribe(
                        unused -> log.info("Stock dispatch confirmed for order: {}", event.getOrderId()),
                        error -> log.error("Error processing stock-confirm for order {}: {}", event.getOrderId(), error.getMessage()),
                        () -> log.info("Stock dispatch confirmed for order: {}", event.getOrderId())
                );
    }
}
