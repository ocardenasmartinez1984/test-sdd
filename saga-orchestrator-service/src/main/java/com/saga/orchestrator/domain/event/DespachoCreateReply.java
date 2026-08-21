package com.saga.orchestrator.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DespachoCreateReply {

    private String sagaId;

    private String orderId;

    private Boolean success;

    private String trackingNumber;

    private String reason;
}
