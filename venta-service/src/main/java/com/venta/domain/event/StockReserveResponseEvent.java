package com.venta.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Evento de respuesta del stock-service al resultado de una reserva de stock.
 *
 * <p>Lo consume el venta-service desde el tópico {@code saga.stock.reserve-reply}.
 * El campo {@code success} indica si la reserva prosperó y {@code reason}
 * describe el motivo del fallo cuando corresponde. Es un DTO de mensajería.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockReserveResponseEvent {

    private String orderId;

    private String productId;

    private Boolean success;

    private String reason;
}
