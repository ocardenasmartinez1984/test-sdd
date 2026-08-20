package com.venta.application.command;

import com.venta.domain.event.StockReserveEvent;
import com.venta.domain.model.Order;
import com.venta.domain.model.Order.OrderStatus;
import com.venta.domain.port.StockEventPublisher;
import com.venta.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCommandService {

    private final OrderRepository orderRepository;
    private final StockEventPublisher stockEventPublisher;

    public Mono<Order> crearVenta(Order order) {
        order.setStatus(OrderStatus.PENDING);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        return orderRepository.save(order)
                .doOnSuccess(savedOrder -> {
                    log.info("Order created: {}", savedOrder.getId());

                    StockReserveEvent event = StockReserveEvent.builder()
                            .orderId(savedOrder.getId())
                            .productId(savedOrder.getProductId())
                            .quantity(savedOrder.getQuantity())
                            .build();

                    stockEventPublisher.reserveStock(event);
                    log.info("Stock reserve event sent for order: {}", savedOrder.getId());
                });
    }

    public Mono<Order> cancelarVenta(String orderId) {
        return orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new RuntimeException("Order not found: " + orderId)))
                .flatMap(order -> {
                    if (order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.CANCELLED) {
                        return Mono.error(new RuntimeException("Cannot cancel order in status: " + order.getStatus()));
                    }

                    OrderStatus previousStatus = order.getStatus();
                    order.setStatus(OrderStatus.CANCELLED);
                    order.setUpdatedAt(LocalDateTime.now());

                    return orderRepository.save(order)
                            .doOnSuccess(savedOrder -> {
                                if (previousStatus == OrderStatus.STOCK_RESERVED || previousStatus == OrderStatus.DISPATCHING) {
                                    StockReserveEvent compensateEvent = StockReserveEvent.builder()
                                            .orderId(savedOrder.getId())
                                            .productId(savedOrder.getProductId())
                                            .quantity(savedOrder.getQuantity())
                                            .build();
                                    stockEventPublisher.compensateStock(compensateEvent);
                                    log.info("Stock compensate event sent for order: {}", orderId);
                                }
                                log.info("Order cancelled: {}", orderId);
                            });
                });
    }

    public Mono<Order> actualizarEstado(String orderId, OrderStatus nuevoEstado) {
        return orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new RuntimeException("Order not found: " + orderId)))
                .flatMap(order -> {
                    order.setStatus(nuevoEstado);
                    order.setUpdatedAt(LocalDateTime.now());
                    return orderRepository.save(order);
                })
                .doOnSuccess(saved -> log.info("Order {} status updated to: {}", orderId, nuevoEstado));
    }
}
