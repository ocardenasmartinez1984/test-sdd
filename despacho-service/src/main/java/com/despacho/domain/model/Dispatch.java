package com.despacho.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Entidad de dominio que representa un despacho (envío) de una orden de venta.
 *
 * <p>Es la raíz de agregado de la capa de dominio del servicio de despacho y se
 * persiste en la colección {@code dispatches} de MongoDB. Guarda la asociación
 * con la orden y el producto, el número de seguimiento (tracking) generado y el
 * estado del envío a lo largo de su ciclo de vida, junto con las marcas de
 * tiempo de creación y última actualización.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "dispatches")
public class Dispatch {

    @Id
    private String id;

    private String orderId;

    private String productId;

    private Integer quantity;

    private String customerId;

    private String trackingNumber;

    private DispatchStatus status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public enum DispatchStatus {
        PREPARANDO,
        ENVIADO,
        EN_CAMINO,
        ENTREGADO,
        FALLIDO,
        CANCELADO
    }
}
