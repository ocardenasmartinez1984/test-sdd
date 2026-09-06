package com.venta.domain.port;

import com.venta.domain.event.DespachoRequestEvent;

/**
 * Puerto de salida (arquitectura hexagonal) para publicar solicitudes de
 * despacho hacia el despacho-service dentro de la SAGA.
 *
 * <p>La capa de aplicación depende de esta abstracción; el adaptador Kafka
 * {@code VentaProducer} la implementa.
 */
public interface DespachoEventPublisher {
    /**
     * Solicita al despacho-service crear el envío de una orden.
     *
     * @param event evento con la orden, producto, cantidad y cliente a despachar
     */
    void requestDespacho(DespachoRequestEvent event);
}
