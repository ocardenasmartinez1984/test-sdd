package com.venta.application;

import com.venta.domain.event.StockReserveEvent;
import com.venta.domain.model.CartItem;
import com.venta.domain.repository.CartRepository;
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
public class CartService {

    private final CartRepository cartRepository;
    private final VentaProducer ventaProducer;

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
                    ventaProducer.sendStockReserve(event);
                    log.info("Stock reserve event sent for cart item: {}", savedItem.getId());
                });
    }

    public Mono<Void> removeFromCart(String sessionId, String productId) {
        return cartRepository.findBySessionIdAndProductId(sessionId, productId)
                .flatMap(cartItem -> {
                    cartItem.setStatus(CartItem.STATUS_RELEASED);

                    StockReserveEvent compensateEvent = StockReserveEvent.builder()
                            .orderId(cartItem.getId())
                            .productId(cartItem.getProductId())
                            .quantity(cartItem.getQuantity())
                            .build();
                    ventaProducer.sendStockCompensate(compensateEvent);
                    log.info("Stock compensate event sent for cart item: {}", cartItem.getId());

                    return cartRepository.delete(cartItem);
                });
    }

    public Flux<CartItem> getCart(String sessionId) {
        return cartRepository.findBySessionIdAndStatus(sessionId, CartItem.STATUS_RESERVED);
    }

    public Mono<Void> clearCart(String sessionId) {
        return cartRepository.findBySessionId(sessionId)
                .filter(item -> CartItem.STATUS_RESERVED.equals(item.getStatus()))
                .doOnNext(item -> {
                    StockReserveEvent compensateEvent = StockReserveEvent.builder()
                            .orderId(item.getId())
                            .productId(item.getProductId())
                            .quantity(item.getQuantity())
                            .build();
                    ventaProducer.sendStockCompensate(compensateEvent);
                    log.info("Stock compensate event sent for cart item: {}", item.getId());
                })
                .then(cartRepository.deleteAll(cartRepository.findBySessionId(sessionId)))
                .then();
    }
}
