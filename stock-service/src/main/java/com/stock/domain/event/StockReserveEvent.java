package com.stock.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Evento/comando de la SAGA de ventas que solicita una operación de stock.
 *
 * <p>DTO que transporta por Kafka la intención de reservar, compensar (liberar)
 * o confirmar el despacho de una cantidad de un producto para un pedido. Lo
 * emite el venta-service (orquestador de la SAGA) y lo consume el
 * {@link com.stock.infrastructure.kafka.StockConsumer}. Incluye el identificador
 * de la SAGA para correlacionar la respuesta.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockReserveEvent {

    private String sagaId;

    private String orderId;

    private String productId;

    private Integer quantity;
}
