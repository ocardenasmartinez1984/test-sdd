package com.venta.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Ítem de carrito de compra: producto que un usuario ha añadido a su sesión y
 * cuyo stock queda reservado temporalmente.
 *
 * <p>Se persiste en la colección {@code cart_items} de MongoDB. Cada ítem tiene
 * un {@code expiresAt} tras el cual el {@code CartExpirer} libera la reserva de
 * stock asociada. El campo {@code status} usa las constantes definidas en esta
 * clase ({@code RESERVED}, {@code RESERVE_FAILED}, {@code RELEASED}). Lombok
 * genera constructores, getters y setters.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "cart_items")
public class CartItem {

    @Id
    private String id;

    private String sessionId;

    private String productId;

    private Integer quantity;

    private Double unitPrice;

    private String status;

    private String reservationId;

    private LocalDateTime createdAt;

    private LocalDateTime expiresAt;

    public static final String STATUS_RESERVED = "RESERVED";
    public static final String STATUS_RESERVE_FAILED = "RESERVE_FAILED";
    public static final String STATUS_RELEASED = "RELEASED";
}
