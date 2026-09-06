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

/**
 * Consumidor Kafka del servicio de despacho.
 *
 * <p>Pertenece a la capa de infraestructura y actúa como adaptador de entrada
 * del flujo SAGA: escucha los comandos de creación de despacho, delega la lógica
 * de negocio en {@link DespachoApplicationService} y publica el resultado a
 * través de {@link DespachoProducer}.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DespachoConsumer {

    private final DespachoApplicationService despachoApplicationService;
    private final DespachoProducer despachoProducer;
    private final ObjectMapper objectMapper;

    /**
     * Procesa los comandos recibidos en el tópico
     * {@code saga.despacho.create-command}.
     *
     * <p>Convierte el mensaje (mapa) a {@link DespachoRequestEvent} y solicita la
     * creación del despacho de forma reactiva. Al suscribirse:</p>
     * <ul>
     *   <li>Si tiene éxito, construye un {@link DespachoResponseEvent} con
     *       {@code success = true} y el número de seguimiento, y lo publica.</li>
     *   <li>Si el flujo reactivo emite error, publica una respuesta con
     *       {@code success = false} y el motivo del fallo.</li>
     * </ul>
     * <p>Si falla la conversión inicial u otra excepción síncrona, extrae el
     * {@code orderId}/{@code sagaId} del mensaje crudo (o valores por defecto) y
     * publica igualmente una respuesta de fallo, de modo que la saga siempre
     * reciba una respuesta.</p>
     *
     * @param message mensaje recibido de Kafka como mapa clave-valor
     */
    @KafkaListener(topics = "saga.despacho.create-command", groupId = "despacho-group")
    public void consumeDespachoRequest(Map<String, Object> message) {
        log.info("Mensaje recibido en saga.despacho.create-command: {}", message);

        try {
            DespachoRequestEvent request = objectMapper.convertValue(message, DespachoRequestEvent.class);

            despachoApplicationService.crearDespacho(request)
                    .subscribe(
                            dispatch -> {
                                DespachoResponseEvent response = DespachoResponseEvent.builder()
                                        .sagaId(request.getSagaId())
                                        .orderId(dispatch.getOrderId())
                                        .success(true)
                                        .trackingNumber(dispatch.getTrackingNumber())
                                        .reason(null)
                                        .build();

                                despachoProducer.sendDespachoResponse(response);
                                log.info("Despacho procesado exitosamente para orden: {}", dispatch.getOrderId());
                            },
                            error -> {
                                log.error("Error procesando saga.despacho.create-command: {}", error.getMessage(), error);

                                String orderId = request.getOrderId() != null ? request.getOrderId() : "UNKNOWN";

                                DespachoResponseEvent response = DespachoResponseEvent.builder()
                                        .sagaId(request.getSagaId())
                                        .orderId(orderId)
                                        .success(false)
                                        .trackingNumber(null)
                                        .reason(error.getMessage())
                                        .build();

                                despachoProducer.sendDespachoResponse(response);
                            }
                    );

        } catch (Exception e) {
            log.error("Error procesando saga.despacho.create-command: {}", e.getMessage(), e);

            String orderId = message.get("orderId") != null ? message.get("orderId").toString() : "UNKNOWN";
            String sagaId = message.get("sagaId") != null ? message.get("sagaId").toString() : null;

            DespachoResponseEvent response = DespachoResponseEvent.builder()
                    .sagaId(sagaId)
                    .orderId(orderId)
                    .success(false)
                    .trackingNumber(null)
                    .reason(e.getMessage())
                    .build();

            despachoProducer.sendDespachoResponse(response);
        }
    }
}
