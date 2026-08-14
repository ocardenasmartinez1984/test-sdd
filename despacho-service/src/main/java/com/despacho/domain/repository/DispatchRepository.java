package com.despacho.domain.repository;

import com.despacho.domain.model.Dispatch;
import com.despacho.domain.model.Dispatch.DispatchStatus;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface DispatchRepository extends ReactiveMongoRepository<Dispatch, String> {

    Mono<Dispatch> findByOrderId(String orderId);

    Mono<Dispatch> findByTrackingNumber(String trackingNumber);

    Flux<Dispatch> findByStatus(DispatchStatus status);
}
