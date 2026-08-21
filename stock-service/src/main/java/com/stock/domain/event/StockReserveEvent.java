package com.stock.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockReserveEvent {

    private String sagaId;

    private String orderId;

    private String productId;

    private Integer quantity;
}
