package com.saga.orchestrator.infrastructure.kafka;

import com.saga.orchestrator.domain.event.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SagaProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String STOCK_RESERVE_COMMAND_TOPIC = "saga.stock.reserve-command";
    private static final String STOCK_COMPENSATE_COMMAND_TOPIC = "saga.stock.compensate-command";
    private static final String DESPACHO_CREATE_COMMAND_TOPIC = "saga.despacho.create-command";
    private static final String ORDER_STATUS_UPDATE_TOPIC = "saga.order.status-update";

    @CircuitBreaker(name = "kafkaProducer", fallbackMethod = "sendStockReserveCommandFallback")
    public void sendStockReserveCommand(StockReserveCommand command) {
        log.info("Sending stock reserve command for saga: {}, order: {}", command.getSagaId(), command.getOrderId());
        kafkaTemplate.send(STOCK_RESERVE_COMMAND_TOPIC, command.getOrderId(), command);
    }

    @CircuitBreaker(name = "kafkaProducer", fallbackMethod = "sendStockCompensateCommandFallback")
    public void sendStockCompensateCommand(StockCompensateCommand command) {
        log.info("Sending stock compensate command for saga: {}, order: {}", command.getSagaId(), command.getOrderId());
        kafkaTemplate.send(STOCK_COMPENSATE_COMMAND_TOPIC, command.getOrderId(), command);
    }

    @CircuitBreaker(name = "kafkaProducer", fallbackMethod = "sendDespachoCreateCommandFallback")
    public void sendDespachoCreateCommand(DespachoCreateCommand command) {
        log.info("Sending despacho create command for saga: {}, order: {}", command.getSagaId(), command.getOrderId());
        kafkaTemplate.send(DESPACHO_CREATE_COMMAND_TOPIC, command.getOrderId(), command);
    }

    @CircuitBreaker(name = "kafkaProducer", fallbackMethod = "sendOrderStatusUpdateFallback")
    public void sendOrderStatusUpdate(OrderStatusUpdate statusUpdate) {
        log.info("Sending order status update for order: {}, status: {}", statusUpdate.getOrderId(), statusUpdate.getStatus());
        kafkaTemplate.send(ORDER_STATUS_UPDATE_TOPIC, statusUpdate.getOrderId(), statusUpdate);
    }

    // Fallback methods
    private void sendStockReserveCommandFallback(StockReserveCommand command, Throwable t) {
        log.error("CircuitBreaker OPEN - Failed to send stock reserve command for saga: {}. Error: {}", command.getSagaId(), t.getMessage());
    }

    private void sendStockCompensateCommandFallback(StockCompensateCommand command, Throwable t) {
        log.error("CircuitBreaker OPEN - Failed to send stock compensate command for saga: {}. Error: {}", command.getSagaId(), t.getMessage());
    }

    private void sendDespachoCreateCommandFallback(DespachoCreateCommand command, Throwable t) {
        log.error("CircuitBreaker OPEN - Failed to send despacho create command for saga: {}. Error: {}", command.getSagaId(), t.getMessage());
    }

    private void sendOrderStatusUpdateFallback(OrderStatusUpdate statusUpdate, Throwable t) {
        log.error("CircuitBreaker OPEN - Failed to send order status update for order: {}. Error: {}", statusUpdate.getOrderId(), t.getMessage());
    }
}
