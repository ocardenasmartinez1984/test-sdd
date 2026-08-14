package com.venta.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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

    public enum OrderStatus {
        PENDING,
        STOCK_RESERVED,
        STOCK_FAILED,
        DISPATCHING,
        DISPATCH_FAILED,
        COMPLETED,
        CANCELLED
    }
}
