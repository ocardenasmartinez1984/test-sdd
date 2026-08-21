package com.saga.orchestrator.domain.repository;

import com.saga.orchestrator.domain.model.SagaState;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface SagaStateRepository extends ReactiveMongoRepository<SagaState, String> {

    Mono<SagaState> findByOrderId(String orderId);
}
