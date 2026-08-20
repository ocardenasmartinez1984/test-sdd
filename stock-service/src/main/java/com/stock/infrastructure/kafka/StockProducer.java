package com.stock.infrastructure.kafka;

import com.stock.domain.event.StockReserveResponseEvent;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockProducer {

    private static final String TOPIC_STOCK_RESERVE_RESPONSE = "stock-reserve-response";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @CircuitBreaker(name = "kafkaProducer", fallbackMethod = "sendReserveResponseFallback")
    public void sendReserveResponse(StockReserveResponseEvent event) {
        log.info("Sending stock-reserve-response for order: {}, success: {}", event.getOrderId(), event.getSuccess());
        kafkaTemplate.send(TOPIC_STOCK_RESERVE_RESPONSE, event.getOrderId(), event);
    }

    private void sendReserveResponseFallback(StockReserveResponseEvent event, Throwable t) {
        log.error("CircuitBreaker OPEN - Failed to send stock-reserve-response for order: {}. Error: {}", event.getOrderId(), t.getMessage());
    }
}
