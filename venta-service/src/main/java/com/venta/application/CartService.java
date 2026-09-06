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

/**
 * Servicio de aplicación que gestiona el carrito de compra y sus reservas de
 * stock.
 *
 * <p>Persiste los ítems en MongoDB vía {@link CartRepository} y coordina la
 * reserva/liberación de stock publicando comandos a través de
 * {@link StockEventPublisher}. Cada operación está protegida por un circuit
 * breaker sobre MongoDB con su método de fallback.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final StockEventPublisher stockEventPublisher;

    /**
     * Añade un producto al carrito (de forma aditiva) y reserva su stock.
     *
     * <p>Si el producto ya está en el carrito de la sesión, suma la cantidad,
     * actualiza el precio y renueva la expiración; si no, crea un nuevo ítem
     * RESERVED con expiración a 10 minutos. Tras guardar, publica un
     * {@link StockReserveEvent} para reservar el total en el stock-service.
     *
     * @param sessionId identificador de la sesión de carrito
     * @param productId identificador del producto
     * @param quantity cantidad a añadir
     * @param unitPrice precio unitario del producto
     * @return {@link Mono} con el ítem de carrito persistido
     */
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

    /**
     * Fija la cantidad del ítem de carrito a un valor absoluto (no aditivo). Se
     * usa cuando la UI incrementa o decrementa la cantidad. Reemite un comando de
     * reserva con el nuevo total; el stock-service aplica solo el delta (vía
     * reservedByOrder), de modo que reducir la cantidad libera stock
     * correctamente. Si la nueva cantidad es cero o menor, el ítem se elimina y su
     * reserva se libera por completo.
     *
     * @param sessionId identificador de la sesión de carrito
     * @param productId identificador del producto
     * @param quantity cantidad absoluta a establecer (si &le; 0 elimina el ítem)
     * @param unitPrice precio unitario del producto
     * @return {@link Mono} con el ítem actualizado, o vacío si se eliminó
     */
    @CircuitBreaker(name = "mongoDB", fallbackMethod = "setQuantityFallback")
    public Mono<CartItem> setQuantity(String sessionId, String productId, int quantity, double unitPrice) {
        if (quantity <= 0) {
            return removeFromCart(sessionId, productId).then(Mono.empty());
        }
        return cartRepository.findBySessionIdAndProductId(sessionId, productId)
                .flatMap(existingItem -> {
                    existingItem.setQuantity(quantity);
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
                    log.info("Stock reserve (set qty {}) event sent for cart item: {}", savedItem.getQuantity(), savedItem.getId());
                });
    }

    /**
     * Elimina un producto del carrito y libera su reserva de stock.
     *
     * <p>Marca el ítem como RELEASED, publica un evento de compensación de stock
     * para devolver las unidades reservadas y borra el ítem de MongoDB.
     *
     * @param sessionId identificador de la sesión de carrito
     * @param productId identificador del producto a eliminar
     * @return {@link Mono} que completa tras eliminar el ítem
     */
    @CircuitBreaker(name = "mongoDB", fallbackMethod = "removeFromCartFallback")
    public Mono<Void> removeFromCart(String sessionId, String productId) {        return cartRepository.findBySessionIdAndProductId(sessionId, productId)
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

    /**
     * Devuelve los ítems reservados del carrito de una sesión.
     *
     * @param sessionId identificador de la sesión de carrito
     * @return flujo de ítems en estado RESERVED
     */
    @CircuitBreaker(name = "mongoDB", fallbackMethod = "getCartFallback")
    public Flux<CartItem> getCart(String sessionId) {
        return cartRepository.findBySessionIdAndStatus(sessionId, CartItem.STATUS_RESERVED);
    }

    /**
     * Vacía por completo el carrito de una sesión, liberando el stock reservado.
     *
     * <p>Para cada ítem en estado RESERVED publica un evento de compensación de
     * stock y luego elimina todos los ítems de la sesión de MongoDB.
     *
     * @param sessionId identificador de la sesión de carrito
     * @return {@link Mono} que completa tras vaciar el carrito
     */
    @CircuitBreaker(name = "mongoDB", fallbackMethod = "clearCartFallback")
    public Mono<Void> clearCart(String sessionId) {
        return cartRepository.findBySessionId(sessionId)
                .collectList()
                .flatMap(items -> {
                    items.stream()
                            .filter(item -> CartItem.STATUS_RESERVED.equals(item.getStatus()))
                            .forEach(item -> {
                                StockReserveEvent compensateEvent = StockReserveEvent.builder()
                                        .orderId(item.getId())
                                        .productId(item.getProductId())
                                        .quantity(item.getQuantity())
                                        .build();
                                stockEventPublisher.compensateStock(compensateEvent);
                                log.info("Stock compensate event sent for cart item: {}", item.getId());
                            });
                    return cartRepository.deleteAll(items);
                });
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

    private Mono<CartItem> setQuantityFallback(String sessionId, String productId, int quantity, double unitPrice, Throwable t) {
        log.error("CircuitBreaker OPEN [mongoDB] - setQuantity failed. Error: {}", t.getMessage());
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
