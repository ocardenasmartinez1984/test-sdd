package com.stock.domain.repository;

import com.stock.domain.model.OutboxEvent;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

public interface OutboxRepository extends ReactiveMongoRepository<OutboxEvent, String> {
    Flux<OutboxEvent> findByStatusOrderByCreatedAtAsc(String status);
}
