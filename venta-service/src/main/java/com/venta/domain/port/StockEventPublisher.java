package com.venta.domain.port;

import com.venta.domain.event.StockReserveEvent;

/**
 * Puerto de salida (arquitectura hexagonal) para publicar comandos de stock
 * hacia el stock-service dentro de la SAGA.
 *
 * <p>La capa de aplicación depende de esta abstracción; el adaptador Kafka
 * {@code VentaProducer} la implementa. Desacopla el dominio del mecanismo de
 * mensajería concreto.
 */
public interface StockEventPublisher {
    /**
     * Solicita reservar stock para una orden o ítem de carrito.
     *
     * @param event evento con orden, producto y cantidad a reservar
     */
    void reserveStock(StockReserveEvent event);

    /**
     * Solicita compensar (liberar) una reserva de stock previamente hecha.
     *
     * @param event evento con orden, producto y cantidad a liberar
     */
    void compensateStock(StockReserveEvent event);

    /**
     * Confirma una reserva, convirtiéndola en un descuento definitivo de stock.
     *
     * @param event evento con orden, producto y cantidad a confirmar
     */
    void confirmStock(StockReserveEvent event);
}
