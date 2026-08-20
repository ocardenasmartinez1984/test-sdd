package com.venta.application.saga;

import com.venta.domain.event.DespachoRequestEvent;
import com.venta.domain.event.DespachoResponseEvent;
import com.venta.domain.event.StockReserveEvent;
import com.venta.domain.event.StockReserveResponseEvent;
import com.venta.domain.model.Order;
import com.venta.domain.model.Order.OrderStatus;
import com.venta.domain.port.DespachoEventPublisher;
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
public class SagaOrchestrator {

    private final OrderRepository orderRepository;
    private final StockEventPublisher stockEventPublisher;
    private final DespachoEventPublisher despachoEventPublisher;

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

                                    despachoEventPublisher.requestDespacho(despachoEvent);
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
                                    stockEventPublisher.compensateStock(compensateEvent);
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
}
