package com.venta.application.query;

import com.venta.domain.model.Order;
import com.venta.domain.model.Order.OrderStatus;
import com.venta.domain.repository.OrderRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
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

    @CircuitBreaker(name = "mongoDB", fallbackMethod = "getVentaFallback")
    public Mono<Order> getVenta(String orderId) {
        return orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new RuntimeException("Order not found: " + orderId)));
    }

    @CircuitBreaker(name = "mongoDB", fallbackMethod = "listarVentasFallback")
    public Flux<Order> listarVentas() {
        return orderRepository.findAll();
    }

    @CircuitBreaker(name = "mongoDB", fallbackMethod = "ventasPorClienteFallback")
    public Flux<Order> ventasPorCliente(String customerId) {
        return orderRepository.findByCustomerId(customerId);
    }

    @CircuitBreaker(name = "mongoDB", fallbackMethod = "ventasPorEstadoFallback")
    public Flux<Order> ventasPorEstado(OrderStatus status) {
        return orderRepository.findByStatus(status);
    }

    // Fallback methods
    private Mono<Order> getVentaFallback(String orderId, Throwable t) {
        log.error("CircuitBreaker OPEN [mongoDB] - getVenta failed for order: {}. Error: {}", orderId, t.getMessage());
        return Mono.error(new RuntimeException("Sales service temporarily unavailable. Please try again later."));
    }

    private Flux<Order> listarVentasFallback(Throwable t) {
        log.error("CircuitBreaker OPEN [mongoDB] - listarVentas failed. Error: {}", t.getMessage());
        return Flux.empty();
    }

    private Flux<Order> ventasPorClienteFallback(String customerId, Throwable t) {
        log.error("CircuitBreaker OPEN [mongoDB] - ventasPorCliente failed. Error: {}", t.getMessage());
        return Flux.empty();
    }

    private Flux<Order> ventasPorEstadoFallback(OrderStatus status, Throwable t) {
        log.error("CircuitBreaker OPEN [mongoDB] - ventasPorEstado failed. Error: {}", t.getMessage());
        return Flux.empty();
    }
}
