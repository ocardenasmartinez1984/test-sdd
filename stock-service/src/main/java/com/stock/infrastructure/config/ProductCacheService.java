package com.stock.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.domain.model.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Servicio de caché de productos sobre Redis (capa de infraestructura).
 *
 * <p>Encapsula el acceso reactivo a Redis mediante {@link ReactiveRedisTemplate}
 * para almacenar, recuperar e invalidar productos, dando soporte a la estrategia
 * <i>cache-aside</i> del {@link com.stock.application.StockApplicationService}.
 * Las entradas usan el prefijo {@code product:} con un TTL de 5 minutos y una
 * clave agregada {@code products:all} con TTL de 2 minutos.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCacheService {

    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String PRODUCT_KEY_PREFIX = "product:";
    private static final String ALL_PRODUCTS_KEY = "products:all";
    private static final Duration PRODUCT_TTL = Duration.ofMinutes(5);
    private static final Duration ALL_PRODUCTS_TTL = Duration.ofMinutes(2);

    /**
     * Recupera un producto de la caché Redis.
     *
     * @param productId identificador del producto
     * @return un {@link Mono} con el producto cacheado, o vacío si no está en caché
     */
    public Mono<Product> getCachedProduct(String productId) {
        return redisTemplate.opsForValue().get(PRODUCT_KEY_PREFIX + productId)
                .map(obj -> objectMapper.convertValue(obj, Product.class))
                .doOnNext(p -> log.debug("Cache HIT for product: {}", productId));
    }

    /**
     * Almacena un producto en la caché Redis con el TTL configurado.
     *
     * @param product producto a cachear
     * @return un {@link Mono} que emite el mismo producto tras almacenarlo
     */
    public Mono<Product> cacheProduct(Product product) {
        return redisTemplate.opsForValue()
                .set(PRODUCT_KEY_PREFIX + product.getId(), product, PRODUCT_TTL)
                .thenReturn(product)
                .doOnSuccess(p -> log.debug("Cached product: {}", product.getId()));
    }

    /**
     * Invalida la caché de un producto y la de la lista agregada de productos.
     *
     * @param productId identificador del producto cuya caché se elimina
     * @return un {@link Mono} que completa cuando ambas entradas se han eliminado
     */
    public Mono<Void> evictProduct(String productId) {
        return redisTemplate.delete(PRODUCT_KEY_PREFIX + productId)
                .then(redisTemplate.delete(ALL_PRODUCTS_KEY))
                .then()
                .doOnSuccess(v -> log.debug("Evicted cache for product: {}", productId));
    }

    /**
     * Invalida la entrada de caché de la lista agregada de todos los productos.
     *
     * @return un {@link Mono} que completa cuando la entrada se ha eliminado
     */
    public Mono<Void> evictAllProducts() {
        return redisTemplate.delete(ALL_PRODUCTS_KEY)
                .then()
                .doOnSuccess(v -> log.debug("Evicted all products cache"));
    }
}
