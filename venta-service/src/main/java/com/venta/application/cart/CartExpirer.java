package com.venta.application.cart;

import com.venta.domain.event.StockReserveEvent;
import com.venta.domain.model.CartItem;
import com.venta.domain.port.StockEventPublisher;
import com.venta.domain.repository.CartRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Periodically releases stock held by abandoned carts.
 *
 * <p>Cart items are created with a 10-minute {@code expiresAt} but nothing acted
 * on it, so an abandoned cart kept its stock reserved forever — inflating
 * {@code reservedQuantity} on the product (e.g. a cart with 30 units left the
 * product showing 30 reserved indefinitely). This sweeper finds RESERVED cart
 * items whose {@code expiresAt} has passed, emits a compensate event to release
 * the reservation in the stock service, and deletes the item.
 */
@Slf4j
@Component
public class CartExpirer {

    private final CartRepository cartRepository;
    private final StockEventPublisher stockEventPublisher;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public CartExpirer(CartRepository cartRepository, StockEventPublisher stockEventPublisher) {
        this.cartRepository = cartRepository;
        this.stockEventPublisher = stockEventPublisher;
    }

    @Scheduled(fixedDelayString = "${cart.expirer.interval-ms:60000}")
    public void expireAbandonedCarts() {
        if (!running.compareAndSet(false, true)) {
            log.trace("Cart expirer tick skipped: previous sweep still running");
            return;
        }
        expire()
                .doFinally(signal -> running.set(false))
                .subscribe(null, error -> log.error("Cart expirer sweep failed", error));
    }

    /** Reactive sweep, extracted for deterministic unit testing. */
    Mono<Void> expire() {
        return cartRepository
                .findByStatusAndExpiresAtBefore(CartItem.STATUS_RESERVED, LocalDateTime.now())
                .flatMap(this::releaseAndDelete)
                .then();
    }

    private Mono<Void> releaseAndDelete(CartItem item) {
        StockReserveEvent compensateEvent = StockReserveEvent.builder()
                .orderId(item.getId())
                .productId(item.getProductId())
                .quantity(item.getQuantity())
                .build();
        stockEventPublisher.compensateStock(compensateEvent);
        log.info("Cart expirer: released reservation for abandoned cart item {} (product {}, qty {})",
                item.getId(), item.getProductId(), item.getQuantity());
        return cartRepository.delete(item);
    }
}
