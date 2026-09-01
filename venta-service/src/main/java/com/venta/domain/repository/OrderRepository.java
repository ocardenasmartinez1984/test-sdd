package com.venta.domain.repository;

import com.venta.domain.model.Order;
import com.venta.domain.model.Order.OrderStatus;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.Collection;

@Repository
public interface OrderRepository extends ReactiveMongoRepository<Order, String> {

    Flux<Order> findByCustomerId(String customerId);

    Flux<Order> findByStatus(OrderStatus status);

    /**
     * Orders sitting in one of the given (typically intermediate) statuses and
     * not updated since {@code threshold}. Used by the SAGA reconciler to detect
     * transactions that got stuck because a downstream response was lost.
     */
    Flux<Order> findByStatusInAndUpdatedAtBefore(Collection<OrderStatus> statuses, LocalDateTime threshold);
}
