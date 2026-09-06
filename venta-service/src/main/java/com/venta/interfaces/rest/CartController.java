package com.venta.interfaces.rest;

import com.venta.application.CartService;
import com.venta.domain.model.CartItem;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Controlador REST reactivo que expone la API del carrito de compra bajo
 * {@code /api/v1/cart}.
 *
 * <p>Adaptador de entrada que delega en {@link CartService} las operaciones de
 * añadir, actualizar cantidad, consultar, eliminar y vaciar el carrito,
 * traduciendo los resultados a respuestas HTTP.
 */
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    /**
     * Añade un producto al carrito (de forma aditiva) reservando su stock.
     *
     * @param request datos de sesión, producto, cantidad y precio unitario
     * @return el ítem de carrito creado con estado HTTP 201
     */
    @PostMapping
    public Mono<ResponseEntity<CartItem>> addToCart(@RequestBody AddToCartRequest request) {
        return cartService.addToCart(request.getSessionId(), request.getProductId(),
                        request.getQuantity(), request.getUnitPrice())
                .map(cartItem -> ResponseEntity.status(HttpStatus.CREATED).body(cartItem));
    }

    /**
     * Fija la cantidad de un producto del carrito a un valor absoluto.
     *
     * @param request datos de sesión, producto, cantidad y precio unitario
     * @return el ítem actualizado (HTTP 200) o HTTP 204 si se eliminó
     */
    @PutMapping
    public Mono<ResponseEntity<CartItem>> setQuantity(@RequestBody AddToCartRequest request) {
        return cartService.setQuantity(request.getSessionId(), request.getProductId(),
                        request.getQuantity(), request.getUnitPrice())
                .map(cartItem -> ResponseEntity.ok(cartItem))
                .defaultIfEmpty(ResponseEntity.noContent().build());
    }

    /**
     * Devuelve los ítems reservados del carrito de una sesión.
     *
     * @param sessionId identificador de la sesión de carrito
     * @return flujo de ítems del carrito
     */
    @GetMapping("/{sessionId}")
    public Flux<CartItem> getCart(@PathVariable String sessionId) {
        return cartService.getCart(sessionId);
    }

    /**
     * Elimina un producto del carrito liberando su reserva de stock.
     *
     * @param sessionId identificador de la sesión de carrito
     * @param productId identificador del producto a eliminar
     * @return respuesta HTTP 204 al completar
     */
    @DeleteMapping("/{sessionId}/{productId}")
    public Mono<ResponseEntity<Void>> removeFromCart(@PathVariable String sessionId,
                                                     @PathVariable String productId) {
        return cartService.removeFromCart(sessionId, productId)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }

    /**
     * Vacía por completo el carrito de una sesión liberando el stock reservado.
     *
     * @param sessionId identificador de la sesión de carrito
     * @return respuesta HTTP 204 al completar
     */
    @DeleteMapping("/{sessionId}")
    public Mono<ResponseEntity<Void>> clearCart(@PathVariable String sessionId) {
        return cartService.clearCart(sessionId)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }

    /**
     * DTO de solicitud para añadir o actualizar un ítem del carrito: agrupa la
     * sesión, el producto, la cantidad y el precio unitario. Lombok genera
     * constructores, getters y setters.
     */
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
