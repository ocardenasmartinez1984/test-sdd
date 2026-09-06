package com.venta.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Evento/comando de la SAGA que solicita al stock-service reservar (o, según el
 * tópico, compensar/confirmar) unidades de un producto para una orden.
 *
 * <p>Es el payload que el venta-service publica en los tópicos
 * {@code saga.stock.*-command}. Sirve como DTO de mensajería entre servicios.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockReserveEvent {

    private String orderId;

    private String productId;

    private Integer quantity;
}
