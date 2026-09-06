package com.venta.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Evento/comando de la SAGA que solicita al despacho-service crear el envío de
 * una orden tras confirmarse la reserva de stock.
 *
 * <p>El venta-service lo publica en el tópico {@code saga.despacho.create-command}.
 * Es un DTO de mensajería entre servicios.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DespachoRequestEvent {

    private String orderId;

    private String productId;

    private Integer quantity;

    private String customerId;
}
