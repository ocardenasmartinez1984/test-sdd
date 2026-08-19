package com.venta.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

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
