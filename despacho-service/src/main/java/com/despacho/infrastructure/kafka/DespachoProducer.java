package com.despacho.infrastructure.kafka;

import com.despacho.domain.event.DespachoResponseEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DespachoProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String TOPIC = "despacho-response";

    public void sendDespachoResponse(DespachoResponseEvent event) {
        log.info("Enviando respuesta de despacho al topic {}: {}", TOPIC, event);
        kafkaTemplate.send(TOPIC, event.getOrderId(), event);
    }
}
