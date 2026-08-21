package com.saga.orchestrator.domain.model;

public enum SagaStep {
    STARTED,
    STOCK_RESERVING,
    STOCK_RESERVED,
    DESPACHO_CREATING,
    DESPACHO_CREATED,
    COMPLETING,
    COMPLETED,
    COMPENSATING,
    FAILED
}
