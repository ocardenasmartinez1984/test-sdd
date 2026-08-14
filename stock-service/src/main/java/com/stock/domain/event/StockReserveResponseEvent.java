package com.stock.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockReserveResponseEvent {

    private String orderId;

    private String productId;

    private Boolean success;

    private String reason;
}
