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

    public Mono<Product> getCachedProduct(String productId) {
        return redisTemplate.opsForValue().get(PRODUCT_KEY_PREFIX + productId)
                .map(obj -> objectMapper.convertValue(obj, Product.class))
                .doOnNext(p -> log.debug("Cache HIT for product: {}", productId));
    }

    public Mono<Product> cacheProduct(Product product) {
        return redisTemplate.opsForValue()
                .set(PRODUCT_KEY_PREFIX + product.getId(), product, PRODUCT_TTL)
                .thenReturn(product)
                .doOnSuccess(p -> log.debug("Cached product: {}", product.getId()));
    }

    public Mono<Void> evictProduct(String productId) {
        return redisTemplate.delete(PRODUCT_KEY_PREFIX + productId)
                .then(redisTemplate.delete(ALL_PRODUCTS_KEY))
                .then()
                .doOnSuccess(v -> log.debug("Evicted cache for product: {}", productId));
    }

    public Mono<Void> evictAllProducts() {
        return redisTemplate.delete(ALL_PRODUCTS_KEY)
                .then()
                .doOnSuccess(v -> log.debug("Evicted all products cache"));
    }
}
