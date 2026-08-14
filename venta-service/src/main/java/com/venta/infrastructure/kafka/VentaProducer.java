package com.venta.infrastructure.kafka;

import com.venta.domain.event.DespachoRequestEvent;
import com.venta.domain.event.StockReserveEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class VentaProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String STOCK_RESERVE_TOPIC = "stock-reserve";
    private static final String STOCK_COMPENSATE_TOPIC = "stock-compensate";
    private static final String DESPACHO_REQUEST_TOPIC = "despacho-request";

    public void sendStockReserve(StockReserveEvent event) {
        log.info("Sending stock-reserve event: {}", event);
        kafkaTemplate.send(STOCK_RESERVE_TOPIC, event.getOrderId(), event);
    }

    public void sendStockCompensate(StockReserveEvent event) {
        log.info("Sending stock-compensate event: {}", event);
        kafkaTemplate.send(STOCK_COMPENSATE_TOPIC, event.getOrderId(), event);
    }

    public void sendDespachoRequest(DespachoRequestEvent event) {
        log.info("Sending despacho-request event: {}", event);
        kafkaTemplate.send(DESPACHO_REQUEST_TOPIC, event.getOrderId(), event);
    }
}
