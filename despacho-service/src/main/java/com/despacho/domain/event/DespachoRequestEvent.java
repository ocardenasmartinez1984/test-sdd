package com.despacho.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Evento/DTO de entrada del flujo SAGA que solicita la creación de un despacho.
 *
 * <p>Lo publica el servicio de ventas en el tópico de comando de despacho y lo
 * consume este servicio para generar el envío. Transporta el identificador de la
 * saga (para correlacionar la respuesta), la orden, el producto, la cantidad y
 * el cliente asociados a la solicitud de despacho.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DespachoRequestEvent {

    private String sagaId;

    private String orderId;

    private String productId;

    private Integer quantity;

    private String customerId;
}
