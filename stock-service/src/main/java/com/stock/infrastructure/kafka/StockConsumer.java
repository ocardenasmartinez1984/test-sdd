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

@Slf4j
@Component
@RequiredArgsConstructor
public class StockConsumer {

    private final StockApplicationService stockApplicationService;
    private final StockProducer stockProducer;
    private final ObjectMapper objectMapper;

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
}
