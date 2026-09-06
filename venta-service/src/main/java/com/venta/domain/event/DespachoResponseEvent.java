package com.venta.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Evento de respuesta del despacho-service al resultado de una solicitud de
 * despacho.
 *
 * <p>Lo consume el venta-service desde el tópico {@code saga.despacho.create-reply}.
 * {@code success} indica si el despacho se aceptó, {@code trackingNumber} aporta
 * el número de seguimiento y {@code reason} el motivo del fallo. Es un DTO de
 * mensajería.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DespachoResponseEvent {

    private String orderId;

    private Boolean success;

    private String trackingNumber;

    private String reason;
}
