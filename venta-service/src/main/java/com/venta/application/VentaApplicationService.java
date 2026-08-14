package com.venta.application;

import com.venta.domain.event.DespachoRequestEvent;
import com.venta.domain.event.DespachoResponseEvent;
import com.venta.domain.event.StockReserveEvent;
import com.venta.domain.event.StockReserveResponseEvent;
import com.venta.domain.model.Order;
import com.venta.domain.model.Order.OrderStatus;
import com.venta.domain.repository.OrderRepository;
import com.venta.infrastructure.kafka.VentaProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class VentaApplicationService {

    private final OrderRepository orderRepository;
    private final VentaProducer ventaProducer;

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

                    ventaProducer.sendStockReserve(event);
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
                                    ventaProducer.sendStockCompensate(compensateEvent);
                                    log.info("Stock compensate event sent for order: {}", orderId);
                                }
                                log.info("Order cancelled: {}", orderId);
                            });
                });
    }

    public Mono<Void> handleStockResponse(StockReserveResponseEvent event) {
        return orderRepository.findById(event.getOrderId())
                .switchIfEmpty(Mono.error(new RuntimeException("Order not found: " + event.getOrderId())))
                .flatMap(order -> {
                    if (Boolean.TRUE.equals(event.getSuccess())) {
                        order.setStatus(OrderStatus.STOCK_RESERVED);
                        order.setUpdatedAt(LocalDateTime.now());

                        return orderRepository.save(order)
                                .doOnSuccess(savedOrder -> {
                                    DespachoRequestEvent despachoEvent = DespachoRequestEvent.builder()
                                            .orderId(savedOrder.getId())
                                            .productId(savedOrder.getProductId())
                                            .quantity(savedOrder.getQuantity())
                                            .customerId(savedOrder.getCustomerId())
                                            .build();

                                    ventaProducer.sendDespachoRequest(despachoEvent);
                                    log.info("Despacho request sent for order: {}", savedOrder.getId());
                                });
                    } else {
                        order.setStatus(OrderStatus.STOCK_FAILED);
                        order.setFailureReason(event.getReason());
                        order.setUpdatedAt(LocalDateTime.now());

                        return orderRepository.save(order)
                                .doOnSuccess(savedOrder ->
                                        log.info("Stock reservation failed for order: {}", savedOrder.getId()));
                    }
                })
                .then();
    }

    public Mono<Void> handleDespachoResponse(DespachoResponseEvent event) {
        return orderRepository.findById(event.getOrderId())
                .switchIfEmpty(Mono.error(new RuntimeException("Order not found: " + event.getOrderId())))
                .flatMap(order -> {
                    if (Boolean.TRUE.equals(event.getSuccess())) {
                        order.setStatus(OrderStatus.DISPATCHING);
                        order.setUpdatedAt(LocalDateTime.now());

                        return orderRepository.save(order)
                                .doOnSuccess(savedOrder ->
                                        log.info("Order dispatching: {} (tracking: {})", savedOrder.getId(), event.getTrackingNumber()));
                    } else {
                        order.setStatus(OrderStatus.DISPATCH_FAILED);
                        order.setFailureReason(event.getReason());
                        order.setUpdatedAt(LocalDateTime.now());

                        return orderRepository.save(order)
                                .doOnSuccess(savedOrder -> {
                                    StockReserveEvent compensateEvent = StockReserveEvent.builder()
                                            .orderId(savedOrder.getId())
                                            .productId(savedOrder.getProductId())
                                            .quantity(savedOrder.getQuantity())
                                            .build();
                                    ventaProducer.sendStockCompensate(compensateEvent);
                                    log.info("Dispatch failed for order: {}. Stock compensate sent.", savedOrder.getId());
                                });
                    }
                })
                .then();
    }

    public Mono<Void> handleDespachoDelivered(String orderId) {
        return orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new RuntimeException("Order not found: " + orderId)))
                .flatMap(order -> {
                    if (order.getStatus() == OrderStatus.DISPATCHING) {
                        order.setStatus(OrderStatus.COMPLETED);
                        order.setUpdatedAt(LocalDateTime.now());

                        return orderRepository.save(order)
                                .doOnSuccess(savedOrder ->
                                        log.info("Order completed (delivered): {}", savedOrder.getId()));
                    }
                    return Mono.just(order);
                })
                .then();
    }

    public Mono<Order> getVenta(String orderId) {
        return orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new RuntimeException("Order not found: " + orderId)));
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
