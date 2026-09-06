package com.venta.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.venta.application.saga.SagaOrchestrator;
import com.venta.domain.event.DespachoResponseEvent;
import com.venta.domain.event.StockReserveResponseEvent;
import com.venta.domain.exception.OrderNotFoundException;
import com.venta.domain.model.CartItem;
import com.venta.domain.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Listeners de Kafka para los tópicos de respuesta de la SAGA.
 *
 * <p><b>Por qué bloqueamos sobre la tubería reactiva.</b> La versión anterior
 * llamaba a {@code .subscribe(...)} y registraba los errores dentro del callback.
 * Eso desligaba el procesamiento del hilo del listener, por lo que el contenedor
 * confirmaba el offset de inmediato y cualquier fallo era invisible para el
 * {@link org.springframework.kafka.listener.DefaultErrorHandler} configurado —el
 * mensaje se perdía de hecho—. Al bloquear hasta que el trabajo reactivo termina
 * y dejar que las excepciones salgan del método listener, devolvemos el control
 * al manejador de errores, que reintenta con back-off y finalmente enruta el
 * registro al tópico dead-letter {@code <topic>.dlt}. {@link OrderNotFoundException}
 * se trata como terminal aguas arriba (véase {@code SagaOrchestrator}) para que no
 * envenene la DLQ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VentaConsumer {

    private final SagaOrchestrator sagaOrchestrator;
    private final CartRepository cartRepository;
    private final ObjectMapper objectMapper;

    /**
     * Consume la respuesta de reserva de stock y la enruta al destino correcto.
     *
     * <p>Deserializa el mensaje a {@link StockReserveResponseEvent}. Si el id
     * corresponde a un ítem de carrito, actualiza su estado a RESERVED o
     * RESERVE_FAILED localmente; en caso contrario delega en
     * {@link SagaOrchestrator#handleStockResponse}. Bloquea sobre la tubería
     * reactiva para que los fallos lleguen al manejador de errores de Kafka.
     *
     * @param message mensaje Kafka con los campos de la respuesta de reserva
     */
    @KafkaListener(topics = "saga.stock.reserve-reply", groupId = "venta-service-group")
    public void consumeStockReserveResponse(Map<String, Object> message) {
        log.info("Received stock-reserve-response: {}", message);
        StockReserveResponseEvent event = objectMapper.convertValue(message, StockReserveResponseEvent.class);

        // A cart-item reservation is handled locally; otherwise it's an order SAGA step.
        cartRepository.findById(event.getOrderId())
                .flatMap(cartItem -> {
                    if (Boolean.TRUE.equals(event.getSuccess())) {
                        cartItem.setStatus(CartItem.STATUS_RESERVED);
                        log.info("Cart item {} stock reserved successfully", cartItem.getId());
                    } else {
                        cartItem.setStatus(CartItem.STATUS_RESERVE_FAILED);
                        log.info("Cart item {} stock reservation failed: {}", cartItem.getId(), event.getReason());
                    }
                    return cartRepository.save(cartItem).then();
                })
                .switchIfEmpty(Mono.defer(() -> sagaOrchestrator.handleStockResponse(event)))
                .block();
    }

    /**
     * Consume la respuesta de despacho y delega en el orquestador SAGA.
     *
     * <p>Deserializa el mensaje a {@link DespachoResponseEvent} y llama a
     * {@link SagaOrchestrator#handleDespachoResponse}, bloqueando para propagar
     * los fallos al manejador de errores de Kafka.
     *
     * @param message mensaje Kafka con los campos de la respuesta de despacho
     */
    @KafkaListener(topics = "saga.despacho.create-reply", groupId = "venta-service-group")
    public void consumeDespachoResponse(Map<String, Object> message) {
        log.info("Received despacho-response: {}", message);
        DespachoResponseEvent event = objectMapper.convertValue(message, DespachoResponseEvent.class);
        sagaOrchestrator.handleDespachoResponse(event).block();
    }

    /**
     * Consume la notificación de entrega y cierra la SAGA de la orden.
     *
     * <p>Extrae el {@code orderId} del mensaje y llama a
     * {@link SagaOrchestrator#handleDespachoDelivered}, bloqueando para propagar
     * los fallos al manejador de errores de Kafka.
     *
     * @param message mensaje Kafka que contiene el {@code orderId} entregado
     */
    @KafkaListener(topics = "saga.despacho.delivered", groupId = "venta-service-group")
    public void consumeDespachoDelivered(Map<String, Object> message) {
        log.info("Received despacho-delivered: {}", message);
        String orderId = (String) message.get("orderId");
        sagaOrchestrator.handleDespachoDelivered(orderId).block();
    }
}
