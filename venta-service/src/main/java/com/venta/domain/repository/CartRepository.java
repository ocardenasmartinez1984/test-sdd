package com.venta.domain.repository;

import com.venta.domain.model.CartItem;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Repositorio reactivo de ítems de carrito sobre MongoDB.
 *
 * <p>Ofrece las operaciones CRUD de {@link ReactiveMongoRepository} y consultas
 * derivadas usadas por {@code CartService} y por el {@code CartExpirer} para
 * gestionar carritos y liberar reservas expiradas.
 */
@Repository
public interface CartRepository extends ReactiveMongoRepository<CartItem, String> {

    /**
     * Devuelve todos los ítems de una sesión de carrito.
     *
     * @param sessionId identificador de la sesión
     * @return flujo de ítems del carrito
     */
    Flux<CartItem> findBySessionId(String sessionId);

    /**
     * Devuelve los ítems de una sesión que están en un estado dado.
     *
     * @param sessionId identificador de la sesión
     * @param status estado del ítem por el que filtrar
     * @return flujo de ítems que coinciden
     */
    Flux<CartItem> findBySessionIdAndStatus(String sessionId, String status);

    /**
     * Busca un ítem concreto (producto) dentro de una sesión de carrito.
     *
     * @param sessionId identificador de la sesión
     * @param productId identificador del producto
     * @return el ítem si existe, o vacío en caso contrario
     */
    Mono<CartItem> findBySessionIdAndProductId(String sessionId, String productId);

    /**
     * Busca ítems en un estado dado cuya fecha de expiración es anterior al
     * umbral; usado por el {@code CartExpirer} para detectar carritos abandonados.
     *
     * @param status estado del ítem por el que filtrar (normalmente RESERVED)
     * @param threshold instante límite de expiración
     * @return flujo de ítems expirados candidatos a liberar
     */
    Flux<CartItem> findByStatusAndExpiresAtBefore(String status, LocalDateTime threshold);
}
