package com.venta.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.venta.application.saga.SagaOrchestrator;
import com.venta.domain.event.DespachoResponseEvent;
import com.venta.domain.event.StockReserveResponseEvent;
import com.venta.domain.model.CartItem;
import com.venta.domain.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

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
        try {
            StockReserveResponseEvent event = objectMapper.convertValue(message, StockReserveResponseEvent.class);

            // Check if this response is for a cart item
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
                    .switchIfEmpty(
                            // Not a cart item, handle as order
                            sagaOrchestrator.handleStockResponse(event)
                    )
                    .subscribe(
                            unused -> {},
                            error -> log.error("Error processing stock-reserve-response: {}", error.getMessage(), error)
                    );
        } catch (Exception e) {
            log.error("Error processing stock-reserve-response: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "despacho-response", groupId = "venta-service-group")
    public void consumeDespachoResponse(Map<String, Object> message) {
        log.info("Received despacho-response: {}", message);
        try {
            DespachoResponseEvent event = objectMapper.convertValue(message, DespachoResponseEvent.class);
            sagaOrchestrator.handleDespachoResponse(event)
                    .subscribe(
                            unused -> {},
                            error -> log.error("Error processing despacho-response: {}", error.getMessage(), error)
                    );
        } catch (Exception e) {
            log.error("Error processing despacho-response: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "despacho-delivered", groupId = "venta-service-group")
    public void consumeDespachoDelivered(Map<String, Object> message) {
        log.info("Received despacho-delivered: {}", message);
        try {
            String orderId = (String) message.get("orderId");
            sagaOrchestrator.handleDespachoDelivered(orderId)
                    .subscribe(
                            unused -> {},
                            error -> log.error("Error processing despacho-delivered: {}", error.getMessage(), error)
                    );
        } catch (Exception e) {
            log.error("Error processing despacho-delivered: {}", e.getMessage(), e);
        }
    }
}
