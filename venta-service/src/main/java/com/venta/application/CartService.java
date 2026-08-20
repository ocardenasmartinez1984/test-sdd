package com.venta.application;

import com.venta.domain.event.StockReserveEvent;
import com.venta.domain.model.CartItem;
import com.venta.domain.port.StockEventPublisher;
import com.venta.domain.repository.CartRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final StockEventPublisher stockEventPublisher;

    @CircuitBreaker(name = "mongoDB", fallbackMethod = "addToCartFallback")
    public Mono<CartItem> addToCart(String sessionId, String productId, int quantity, double unitPrice) {
        return cartRepository.findBySessionIdAndProductId(sessionId, productId)
                .flatMap(existingItem -> {
                    existingItem.setQuantity(existingItem.getQuantity() + quantity);
                    existingItem.setUnitPrice(unitPrice);
                    existingItem.setExpiresAt(LocalDateTime.now().plusMinutes(10));
                    return cartRepository.save(existingItem);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    CartItem cartItem = CartItem.builder()
                            .sessionId(sessionId)
                            .productId(productId)
                            .quantity(quantity)
                            .unitPrice(unitPrice)
                            .status(CartItem.STATUS_RESERVED)
                            .createdAt(LocalDateTime.now())
                            .expiresAt(LocalDateTime.now().plusMinutes(10))
                            .build();
                    return cartRepository.save(cartItem);
                }))
                .doOnSuccess(savedItem -> {
                    StockReserveEvent event = StockReserveEvent.builder()
                            .orderId(savedItem.getId())
                            .productId(savedItem.getProductId())
                            .quantity(savedItem.getQuantity())
                            .build();
                    stockEventPublisher.reserveStock(event);
                    log.info("Stock reserve event sent for cart item: {}", savedItem.getId());
                });
    }

    @CircuitBreaker(name = "mongoDB", fallbackMethod = "removeFromCartFallback")
    public Mono<Void> removeFromCart(String sessionId, String productId) {
        return cartRepository.findBySessionIdAndProductId(sessionId, productId)
                .flatMap(cartItem -> {
                    cartItem.setStatus(CartItem.STATUS_RELEASED);

                    StockReserveEvent compensateEvent = StockReserveEvent.builder()
                            .orderId(cartItem.getId())
                            .productId(cartItem.getProductId())
                            .quantity(cartItem.getQuantity())
                            .build();
                    stockEventPublisher.compensateStock(compensateEvent);
                    log.info("Stock compensate event sent for cart item: {}", cartItem.getId());

                    return cartRepository.delete(cartItem);
                });
    }

    @CircuitBreaker(name = "mongoDB", fallbackMethod = "getCartFallback")
    public Flux<CartItem> getCart(String sessionId) {
        return cartRepository.findBySessionIdAndStatus(sessionId, CartItem.STATUS_RESERVED);
    }

    @CircuitBreaker(name = "mongoDB", fallbackMethod = "clearCartFallback")
    public Mono<Void> clearCart(String sessionId) {
        return cartRepository.findBySessionId(sessionId)
                .filter(item -> CartItem.STATUS_RESERVED.equals(item.getStatus()))
                .doOnNext(item -> {
                    StockReserveEvent compensateEvent = StockReserveEvent.builder()
                            .orderId(item.getId())
                            .productId(item.getProductId())
                            .quantity(item.getQuantity())
                            .build();
                    stockEventPublisher.compensateStock(compensateEvent);
                    log.info("Stock compensate event sent for cart item: {}", item.getId());
                })
                .then(cartRepository.deleteAll(cartRepository.findBySessionId(sessionId)))
                .then();
    }

    // Fallback methods
    private Mono<CartItem> addToCartFallback(String sessionId, String productId, int quantity, double unitPrice, Throwable t) {
        log.error("CircuitBreaker OPEN [mongoDB] - addToCart failed. Error: {}", t.getMessage());
        return Mono.error(new RuntimeException("Cart service temporarily unavailable. Please try again later."));
    }

    private Mono<Void> removeFromCartFallback(String sessionId, String productId, Throwable t) {
        log.error("CircuitBreaker OPEN [mongoDB] - removeFromCart failed. Error: {}", t.getMessage());
        return Mono.error(new RuntimeException("Cart service temporarily unavailable. Please try again later."));
    }

    private Flux<CartItem> getCartFallback(String sessionId, Throwable t) {
        log.error("CircuitBreaker OPEN [mongoDB] - getCart failed. Error: {}", t.getMessage());
        return Flux.empty();
    }

    private Mono<Void> clearCartFallback(String sessionId, Throwable t) {
        log.error("CircuitBreaker OPEN [mongoDB] - clearCart failed. Error: {}", t.getMessage());
        return Mono.error(new RuntimeException("Cart service temporarily unavailable. Please try again later."));
    }
}
