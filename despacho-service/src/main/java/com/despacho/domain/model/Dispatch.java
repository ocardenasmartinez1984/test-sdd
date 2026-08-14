package com.despacho.domain.model;

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
