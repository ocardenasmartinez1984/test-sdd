package com.venta.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Aggregate root del dominio de ventas: representa una orden de venta y su
 * estado a lo largo de la SAGA distribuida.
 *
 * <p>Se persiste como documento en la colección {@code orders} de MongoDB. Su
 * campo {@link OrderStatus} refleja en qué punto de la coreografía SAGA
 * (reserva de stock, despacho, compensación) se encuentra la orden. Lombok
 * genera constructores, getters y setters.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "orders")
public class Order {

    @Id
    private String id;

    private String customerId;

    private String productId;

    private Integer quantity;

    private BigDecimal totalAmount;

    private OrderStatus status;

    private String failureReason;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /**
     * Estados por los que transita una orden durante la SAGA de venta.
     */
    public enum OrderStatus {
        /** Orden recién creada; se ha solicitado la reserva de stock. */
        PENDING,
        /** El stock se reservó con éxito; se solicitará el despacho. */
        STOCK_RESERVED,
        /** Falló la reserva de stock; la SAGA termina en fallo. */
        STOCK_FAILED,
        /** Despacho aceptado; el pedido está en proceso de envío. */
        DISPATCHING,
        /** Falló el despacho; se compensa liberando el stock reservado. */
        DISPATCH_FAILED,
        /** Orden entregada y confirmada; la SAGA finaliza con éxito. */
        COMPLETED,
        /** Orden cancelada (por el usuario o por compensación). */
        CANCELLED
    }
}
