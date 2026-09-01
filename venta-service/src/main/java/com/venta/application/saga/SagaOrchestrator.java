package com.venta.application.saga;

import com.venta.domain.event.DespachoRequestEvent;
import com.venta.domain.event.DespachoResponseEvent;
import com.venta.domain.event.StockReserveEvent;
import com.venta.domain.event.StockReserveResponseEvent;
import com.venta.domain.exception.OrderNotFoundException;
import com.venta.domain.model.Order;
import com.venta.domain.model.Order.OrderStatus;
import com.venta.domain.port.DespachoEventPublisher;
import com.venta.domain.port.StockEventPublisher;
import com.venta.domain.repository.OrderRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Orchestrates the sales SAGA across stock and dispatch services.
 *
 * <p><b>Failure handling.</b> Each handler is guarded by a circuit breaker on
 * the MongoDB dependency. Previously the fallback methods swallowed the failure
 * by returning {@code Mono.empty()}, which silently dropped the Kafka event and
 * left the order stuck in an intermediate state forever. The fallbacks now
 * <b>propagate</b> the error so the failure surfaces to the Kafka listener,
 * whose {@code DefaultErrorHandler} retries and, on exhaustion, routes the
 * record to the dead-letter topic instead of losing it. Orders that still end
 * up stuck (e.g. because a downstream response never arrives) are swept by
 * {@link com.venta.application.saga.SagaReconciler}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SagaOrchestrator {

    private final OrderRepository orderRepository;
    private final StockEventPublisher stockEventPublisher;
    private final DespachoEventPublisher despachoEventPublisher;

    @CircuitBreaker(name = "mongoDB", fallbackMethod = "handleStockResponseFallback")
    public Mono<Void> handleStockResponse(StockReserveResponseEvent event) {
        return orderRepository.findById(event.getOrderId())
                .switchIfEmpty(Mono.error(new OrderNotFoundException(event.getOrderId())))
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

    @CircuitBreaker(name = "mongoDB", fallbackMethod = "handleDespachoResponseFallback")
    public Mono<Void> handleDespachoResponse(DespachoResponseEvent event) {
        return orderRepository.findById(event.getOrderId())
                .switchIfEmpty(Mono.error(new OrderNotFoundException(event.getOrderId())))
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

    @CircuitBreaker(name = "mongoDB", fallbackMethod = "handleDespachoDeliveredFallback")
    public Mono<Void> handleDespachoDelivered(String orderId) {
        return orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new OrderNotFoundException(orderId)))
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

    // ------------------------------------------------------------------
    // Fallback methods
    //
    // A missing order (OrderNotFoundException) is a terminal, non-retryable
    // condition: retrying will never make the aggregate appear, so we complete
    // empty to let the listener commit the offset and avoid poisoning the DLQ.
    //
    // Any other error (circuit breaker OPEN, DB timeout, write conflict) is
    // transient: we PROPAGATE it so the Kafka listener's DefaultErrorHandler can
    // retry and, on exhaustion, publish to the dead-letter topic. This is what
    // prevents the SAGA event from being lost silently.
    // ------------------------------------------------------------------

    private Mono<Void> handleStockResponseFallback(StockReserveResponseEvent event, Throwable t) {
        return terminalOrPropagate("handleStockResponse", event.getOrderId(), t);
    }

    private Mono<Void> handleDespachoResponseFallback(DespachoResponseEvent event, Throwable t) {
        return terminalOrPropagate("handleDespachoResponse", event.getOrderId(), t);
    }

    private Mono<Void> handleDespachoDeliveredFallback(String orderId, Throwable t) {
        return terminalOrPropagate("handleDespachoDelivered", orderId, t);
    }

    private Mono<Void> terminalOrPropagate(String operation, String orderId, Throwable t) {
        if (t instanceof OrderNotFoundException) {
            log.error("{} - order not found (non-retryable): {}", operation, orderId);
            return Mono.empty();
        }
        log.error("{} - transient failure for order {} ({}). Propagating for retry/DLQ.",
                operation, orderId, t.getMessage());
        return Mono.error(t);
    }
}
