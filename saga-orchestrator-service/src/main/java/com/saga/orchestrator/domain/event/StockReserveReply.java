package com.saga.orchestrator.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockReserveReply {

    private String sagaId;

    private String orderId;

    private String productId;

    private Boolean success;

    private String reason;
}
