package com.venta.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.venta.application.VentaApplicationService;
import com.venta.domain.event.DespachoResponseEvent;
import com.venta.domain.event.StockReserveResponseEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class VentaConsumer {

    private final VentaApplicationService ventaApplicationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "stock-reserve-response", groupId = "venta-service-group")
    public void consumeStockReserveResponse(Map<String, Object> message) {
        log.info("Received stock-reserve-response: {}", message);
        try {
            StockReserveResponseEvent event = objectMapper.convertValue(message, StockReserveResponseEvent.class);
            ventaApplicationService.handleStockResponse(event)
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
            ventaApplicationService.handleDespachoResponse(event)
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
            ventaApplicationService.handleDespachoDelivered(orderId)
                    .subscribe(
                            unused -> {},
                            error -> log.error("Error processing despacho-delivered: {}", error.getMessage(), error)
                    );
        } catch (Exception e) {
            log.error("Error processing despacho-delivered: {}", e.getMessage(), e);
        }
    }
}
