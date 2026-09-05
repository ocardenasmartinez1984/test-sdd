package com.venta.domain.repository;

import com.venta.domain.model.CartItem;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Repository
public interface CartRepository extends ReactiveMongoRepository<CartItem, String> {

    Flux<CartItem> findBySessionId(String sessionId);

    Flux<CartItem> findBySessionIdAndStatus(String sessionId, String status);

    Mono<CartItem> findBySessionIdAndProductId(String sessionId, String productId);

    Flux<CartItem> findByStatusAndExpiresAtBefore(String status, LocalDateTime threshold);
}
