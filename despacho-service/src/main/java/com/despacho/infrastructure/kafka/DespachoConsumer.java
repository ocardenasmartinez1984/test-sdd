package com.despacho.infrastructure.kafka;

import com.despacho.application.DespachoApplicationService;
import com.despacho.domain.event.DespachoRequestEvent;
import com.despacho.domain.event.DespachoResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DespachoConsumer {

    private final DespachoApplicationService despachoApplicationService;
    private final DespachoProducer despachoProducer;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "despacho-request", groupId = "despacho-group")
    public void consumeDespachoRequest(Map<String, Object> message) {
        log.info("Mensaje recibido en despacho-request: {}", message);

        try {
            DespachoRequestEvent request = objectMapper.convertValue(message, DespachoRequestEvent.class);

            despachoApplicationService.crearDespacho(request)
                    .subscribe(
                            dispatch -> {
                                DespachoResponseEvent response = DespachoResponseEvent.builder()
                                        .orderId(dispatch.getOrderId())
                                        .success(true)
                                        .trackingNumber(dispatch.getTrackingNumber())
                                        .reason(null)
                                        .build();

                                despachoProducer.sendDespachoResponse(response);
                                log.info("Despacho procesado exitosamente para orden: {}", dispatch.getOrderId());
                            },
                            error -> {
                                log.error("Error procesando despacho-request: {}", error.getMessage(), error);

                                String orderId = request.getOrderId() != null ? request.getOrderId() : "UNKNOWN";

                                DespachoResponseEvent response = DespachoResponseEvent.builder()
                                        .orderId(orderId)
                                        .success(false)
                                        .trackingNumber(null)
                                        .reason(error.getMessage())
                                        .build();

                                despachoProducer.sendDespachoResponse(response);
                            }
                    );

        } catch (Exception e) {
            log.error("Error procesando despacho-request: {}", e.getMessage(), e);

            String orderId = message.get("orderId") != null ? message.get("orderId").toString() : "UNKNOWN";

            DespachoResponseEvent response = DespachoResponseEvent.builder()
                    .orderId(orderId)
                    .success(false)
                    .trackingNumber(null)
                    .reason(e.getMessage())
                    .build();

            despachoProducer.sendDespachoResponse(response);
        }
    }
}
