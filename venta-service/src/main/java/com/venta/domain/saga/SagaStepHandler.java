package com.venta.domain.saga;

import reactor.core.publisher.Mono;

public interface SagaStepHandler {
    boolean canHandle(String eventType);
    Mono<Void> handle(Object event);
}
