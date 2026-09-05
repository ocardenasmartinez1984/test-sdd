package com.venta.interfaces.rest;

import com.venta.application.CartService;
import com.venta.domain.model.CartItem;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @PostMapping
    public Mono<ResponseEntity<CartItem>> addToCart(@RequestBody AddToCartRequest request) {
        return cartService.addToCart(request.getSessionId(), request.getProductId(),
                        request.getQuantity(), request.getUnitPrice())
                .map(cartItem -> ResponseEntity.status(HttpStatus.CREATED).body(cartItem));
    }

    @PutMapping
    public Mono<ResponseEntity<CartItem>> setQuantity(@RequestBody AddToCartRequest request) {
        return cartService.setQuantity(request.getSessionId(), request.getProductId(),
                        request.getQuantity(), request.getUnitPrice())
                .map(cartItem -> ResponseEntity.ok(cartItem))
                .defaultIfEmpty(ResponseEntity.noContent().build());
    }

    @GetMapping("/{sessionId}")
    public Flux<CartItem> getCart(@PathVariable String sessionId) {
        return cartService.getCart(sessionId);
    }

    @DeleteMapping("/{sessionId}/{productId}")
    public Mono<ResponseEntity<Void>> removeFromCart(@PathVariable String sessionId,
                                                     @PathVariable String productId) {
        return cartService.removeFromCart(sessionId, productId)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }

    @DeleteMapping("/{sessionId}")
    public Mono<ResponseEntity<Void>> clearCart(@PathVariable String sessionId) {
        return cartService.clearCart(sessionId)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AddToCartRequest {
        private String sessionId;
        private String productId;
        private Integer quantity;
        private Double unitPrice;
    }
}
