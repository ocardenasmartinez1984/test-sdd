package com.venta.application.saga;

import com.venta.domain.event.DespachoRequestEvent;
import com.venta.domain.event.StockReserveEvent;
import com.venta.domain.model.Order;
import com.venta.domain.model.Order.OrderStatus;
import com.venta.domain.port.DespachoEventPublisher;
import com.venta.domain.port.StockEventPublisher;
import com.venta.domain.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Periodically sweeps orders that got stuck in an intermediate SAGA state and
 * re-drives them.
 *
 * <p>Even with retries and a dead-letter topic on the response consumers, an
 * order can still stall — for example if a downstream service crashed before
 * emitting its response, or a response landed in the DLQ. Such an order sits in
 * {@link OrderStatus#PENDING} (awaiting stock) or {@link OrderStatus#STOCK_RESERVED}
 * (awaiting dispatch) indefinitely. This reconciler finds orders that have not
 * changed for longer than {@code saga.reconciler.stuck-after} and re-emits the
 * command that should move them forward. Re-emission is safe because the
 * downstream handlers are idempotent on order id / status.
 */
@Slf4j
@Component
public class SagaReconciler {

    private final OrderRepository orderRepository;
    private final StockEventPublisher stockEventPublisher;
    private final DespachoEventPublisher despachoEventPublisher;
    private final Duration stuckAfter;

    private final AtomicBoolean running = new AtomicBoolean(false);

    public SagaReconciler(OrderRepository orderRepository,
                          StockEventPublisher stockEventPublisher,
                          DespachoEventPublisher despachoEventPublisher,
                          @Value("${saga.reconciler.stuck-after:PT2M}") Duration stuckAfter) {
        this.orderRepository = orderRepository;
        this.stockEventPublisher = stockEventPublisher;
        this.despachoEventPublisher = despachoEventPublisher;
        this.stuckAfter = stuckAfter;
    }

    @Scheduled(fixedDelayString = "${saga.reconciler.interval-ms:60000}")
    public void reconcileStuckOrders() {
        if (!running.compareAndSet(false, true)) {
            log.trace("Reconciler tick skipped: previous sweep still running");
            return;
        }

        reconcile()
                .doFinally(signal -> running.set(false))
                .subscribe(
                        null,
                        error -> log.error("SAGA reconciler sweep failed", error));
    }

    /** Reactive sweep, extracted for deterministic unit testing. */
    Mono<Void> reconcile() {
        LocalDateTime threshold = LocalDateTime.now().minus(stuckAfter);
        return orderRepository
                .findByStatusInAndUpdatedAtBefore(
                        List.of(OrderStatus.PENDING, OrderStatus.STOCK_RESERVED), threshold)
                .doOnNext(this::redrive)
                .then();
    }

    private void redrive(Order order) {
        switch (order.getStatus()) {
            case PENDING -> {
                log.warn("Reconciler: order {} stuck in PENDING, re-emitting stock reserve", order.getId());
                stockEventPublisher.reserveStock(StockReserveEvent.builder()
                        .orderId(order.getId())
                        .productId(order.getProductId())
                        .quantity(order.getQuantity())
                        .build());
            }
            case STOCK_RESERVED -> {
                log.warn("Reconciler: order {} stuck in STOCK_RESERVED, re-emitting despacho request", order.getId());
                despachoEventPublisher.requestDespacho(DespachoRequestEvent.builder()
                        .orderId(order.getId())
                        .productId(order.getProductId())
                        .quantity(order.getQuantity())
                        .customerId(order.getCustomerId())
                        .build());
            }
            default -> log.debug("Reconciler: no action for order {} in status {}", order.getId(), order.getStatus());
        }
    }
}
