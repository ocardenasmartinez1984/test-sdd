package com.saga.orchestrator.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DespachoCreateCommand {

    private String sagaId;

    private String orderId;

    private String productId;

    private Integer quantity;

    private String customerId;
}
