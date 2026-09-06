package com.despacho.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Evento/DTO de salida del flujo SAGA que comunica el resultado de un despacho.
 *
 * <p>Lo emite este servicio hacia el tópico de respuesta de despacho para que el
 * orquestador de ventas continúe o compense la saga. Transporta el identificador
 * de la saga, la orden, un indicador de éxito/fallo, el número de seguimiento
 * generado cuando la operación tiene éxito y el motivo del fallo en caso
 * contrario.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DespachoResponseEvent {

    private String sagaId;

    private String orderId;

    private Boolean success;

    private String trackingNumber;

    private String reason;
}
