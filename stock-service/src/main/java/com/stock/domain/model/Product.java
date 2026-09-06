package com.stock.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.HashMap;
import java.util.Map;

/**
 * Entidad de dominio (agregado) que representa un producto del inventario.
 *
 * <p>Es el aggregate root de la capa de dominio del stock-service y se persiste
 * como documento en la colección {@code products} de MongoDB. Contiene el stock
 * físico ({@code quantity}), el stock comprometido ({@code reservedQuantity}) y
 * un desglose de las reservas por pedido en {@code reservedByOrder}, lo que
 * permite calcular el stock disponible y aplicar reservas idempotentes durante
 * la SAGA de ventas.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "products")
public class Product {

    @Id
    private String id;

    private String sku;

    private String name;

    private Integer quantity;

    private Integer reservedQuantity;

    private Double price;

    /**
     * Quantity currently reserved per order id (orderId or cart item id).
     *
     * <p>This makes stock reservation both <b>idempotent</b> and <b>updatable</b>:
     * <ul>
     *   <li>A redelivered Kafka message or a re-emission from the SAGA reconciler
     *       carrying the same quantity results in a zero delta — the reserved
     *       total does not change.</li>
     *   <li>When an order/cart updates its quantity (e.g. adding more units of the
     *       same product to the cart), only the <i>delta</i> against the previously
     *       reserved amount is applied.</li>
     * </ul>
     */
    @Builder.Default
    private Map<String, Integer> reservedByOrder = new HashMap<>();
}
