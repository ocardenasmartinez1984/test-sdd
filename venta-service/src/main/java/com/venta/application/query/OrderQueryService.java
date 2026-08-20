package com.venta.application.query;

import com.venta.domain.model.Order;
import com.venta.domain.model.Order.OrderStatus;
import com.venta.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderQueryService {

    private final OrderRepository orderRepository;

    public Mono<Order> getVenta(String orderId) {
        return orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new RuntimeException("Order not found: " + orderId)));
    }

    public Flux<Order> listarVentas() {
        return orderRepository.findAll();
    }

    public Flux<Order> ventasPorCliente(String customerId) {
        return orderRepository.findByCustomerId(customerId);
    }

    public Flux<Order> ventasPorEstado(OrderStatus status) {
        return orderRepository.findByStatus(status);
    }
}
