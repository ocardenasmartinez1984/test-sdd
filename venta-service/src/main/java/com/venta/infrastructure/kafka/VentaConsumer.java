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
 * Kafka listeners for SAGA response topics.
 *
 * <p><b>Why we block on the reactive pipeline.</b> The previous version called
 * {@code .subscribe(...)} and logged errors inside the callback. That detached
 * processing from the listener thread, so the container committed the offset
 * immediately and any failure was invisible to the configured
 * {@link org.springframework.kafka.listener.DefaultErrorHandler} — the message
 * was effectively lost. By blocking until the reactive work completes and
 * letting exceptions bubble out of the listener method, we hand control back to
 * the error handler, which retries with back-off and finally routes the record
 * to the {@code <topic>.dlt} dead-letter topic. {@link OrderNotFoundException}
 * is treated as terminal upstream (see {@code SagaOrchestrator}) so it does not
 * poison the DLQ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VentaConsumer {

    private final SagaOrchestrator sagaOrchestrator;
    private final CartRepository cartRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "stock-reserve-response", groupId = "venta-service-group")
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

    @KafkaListener(topics = "despacho-response", groupId = "venta-service-group")
    public void consumeDespachoResponse(Map<String, Object> message) {
        log.info("Received despacho-response: {}", message);
        DespachoResponseEvent event = objectMapper.convertValue(message, DespachoResponseEvent.class);
        sagaOrchestrator.handleDespachoResponse(event).block();
    }

    @KafkaListener(topics = "despacho-delivered", groupId = "venta-service-group")
    public void consumeDespachoDelivered(Map<String, Object> message) {
        log.info("Received despacho-delivered: {}", message);
        String orderId = (String) message.get("orderId");
        sagaOrchestrator.handleDespachoDelivered(orderId).block();
    }
}
