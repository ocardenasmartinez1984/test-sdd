package com.stock.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Evento de respuesta de la SAGA con el resultado de una reserva de stock.
 *
 * <p>DTO que el stock-service publica en el topic de réplica tras intentar
 * reservar inventario. Lo produce el
 * {@link com.stock.infrastructure.kafka.StockProducer} y lo consume el
 * orquestador de la SAGA (venta-service) para decidir si continúa el flujo o
 * inicia la compensación. Indica el éxito o fracaso mediante {@code success} y,
 * en caso de fallo, el motivo en {@code reason}, correlacionado por
 * {@code sagaId}/{@code orderId}.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockReserveResponseEvent {

    private String sagaId;

    private String orderId;

    private String productId;

    private Boolean success;

    private String reason;
}
