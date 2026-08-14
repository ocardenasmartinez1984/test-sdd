package com.venta.domain.repository;

import com.venta.domain.model.Order;
import com.venta.domain.model.Order.OrderStatus;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface OrderRepository extends ReactiveMongoRepository<Order, String> {

    Flux<Order> findByCustomerId(String customerId);

    Flux<Order> findByStatus(OrderStatus status);
}
