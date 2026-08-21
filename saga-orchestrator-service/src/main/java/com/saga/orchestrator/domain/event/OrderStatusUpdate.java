package com.saga.orchestrator.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusUpdate {

    private String orderId;

    private String status;

    private String failureReason;

    private String trackingNumber;
}
