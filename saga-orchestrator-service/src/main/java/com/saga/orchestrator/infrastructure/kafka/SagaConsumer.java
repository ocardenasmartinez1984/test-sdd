package com.saga.orchestrator.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saga.orchestrator.application.SagaOrchestratorService;
import com.saga.orchestrator.domain.event.DespachoCreateReply;
import com.saga.orchestrator.domain.event.OrderCreatedEvent;
import com.saga.orchestrator.domain.event.StockReserveReply;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SagaConsumer {

    private final SagaOrchestratorService sagaOrchestratorService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "saga.order.created", groupId = "saga-orchestrator-group")
    public void consumeOrderCreated(Map<String, Object> message) {
        log.info("Received saga.order.created: {}", message);
        try {
            OrderCreatedEvent event = objectMapper.convertValue(message, OrderCreatedEvent.class);
            sagaOrchestratorService.startSaga(event)
                    .subscribe(
                            unused -> {},
                            error -> log.error("Error processing saga.order.created: {}", error.getMessage(), error)
                    );
        } catch (Exception e) {
            log.error("Error processing saga.order.created: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "saga.stock.reserve-reply", groupId = "saga-orchestrator-group")
    public void consumeStockReserveReply(Map<String, Object> message) {
        log.info("Received saga.stock.reserve-reply: {}", message);
        try {
            StockReserveReply reply = objectMapper.convertValue(message, StockReserveReply.class);
            sagaOrchestratorService.handleStockReply(reply)
                    .subscribe(
                            unused -> {},
                            error -> log.error("Error processing saga.stock.reserve-reply: {}", error.getMessage(), error)
                    );
        } catch (Exception e) {
            log.error("Error processing saga.stock.reserve-reply: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "saga.despacho.create-reply", groupId = "saga-orchestrator-group")
    public void consumeDespachoCreateReply(Map<String, Object> message) {
        log.info("Received saga.despacho.create-reply: {}", message);
        try {
            DespachoCreateReply reply = objectMapper.convertValue(message, DespachoCreateReply.class);
            sagaOrchestratorService.handleDespachoReply(reply)
                    .subscribe(
                            unused -> {},
                            error -> log.error("Error processing saga.despacho.create-reply: {}", error.getMessage(), error)
                    );
        } catch (Exception e) {
            log.error("Error processing saga.despacho.create-reply: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "saga.despacho.delivered", groupId = "saga-orchestrator-group")
    public void consumeDespachoDelivered(Map<String, Object> message) {
        log.info("Received saga.despacho.delivered: {}", message);
        try {
            String orderId = (String) message.get("orderId");
            sagaOrchestratorService.handleDespachoDelivered(orderId)
                    .subscribe(
                            unused -> {},
                            error -> log.error("Error processing saga.despacho.delivered: {}", error.getMessage(), error)
                    );
        } catch (Exception e) {
            log.error("Error processing saga.despacho.delivered: {}", e.getMessage(), e);
        }
    }
}
