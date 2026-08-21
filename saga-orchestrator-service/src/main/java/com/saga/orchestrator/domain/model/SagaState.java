package com.saga.orchestrator.domain.model;

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
@Document(collection = "saga_states")
public class SagaState {

    @Id
    private String id;

    private String orderId;

    private String productId;

    private Integer quantity;

    private String customerId;

    private SagaStep currentStep;

    private SagaStatus status;

    private String failureReason;

    private String trackingNumber;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
