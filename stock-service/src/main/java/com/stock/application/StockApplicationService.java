package com.stock.application;

import com.stock.domain.model.Product;
import com.stock.domain.repository.ProductRepository;
import com.stock.infrastructure.config.ProductCacheService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockApplicationService {

    private final ProductRepository productRepository;
    private final ProductCacheService productCacheService;

    @CircuitBreaker(name = "mongoDB", fallbackMethod = "existsFallback")
    public Mono<Boolean> exists(String productId) {
        return productRepository.existsById(productId);
    }

    @CircuitBreaker(name = "mongoDB", fallbackMethod = "isAvailableFallback")
    public Mono<Boolean> isAvailable(String productId, int quantity) {
        return productRepository.findById(productId)
                .map(product -> (product.getQuantity() - product.getReservedQuantity()) >= quantity)
                .defaultIfEmpty(false);
    }

    @CircuitBreaker(name = "mongoDB", fallbackMethod = "getAvailableQuantityFallback")
    public Mono<Integer> getAvailableQuantity(String productId) {
        return productRepository.findById(productId)
                .map(product -> product.getQuantity() - product.getReservedQuantity())
                .defaultIfEmpty(0);
    }

    @CircuitBreaker(name = "mongoDB", fallbackMethod = "reserveFallback")
    public Mono<Boolean> reserve(String orderId, String productId, int quantity) {
        return productRepository.findById(productId)
                .flatMap(product -> {
                    int alreadyReserved = product.getReservedByOrder().getOrDefault(orderId, 0);
                    int delta = quantity - alreadyReserved;
                    if (delta == 0) {
                        log.info("Reserve unchanged: order {} already reserves {} of product {} (idempotent)", orderId, quantity, productId);
                        return Mono.just(true);
                    }
                    int available = product.getQuantity() - product.getReservedQuantity();
                    if (delta > available) {
                        log.warn("Reserve failed: insufficient stock for product {}. Available: {}, Additional requested: {}", productId, available, delta);
                        return Mono.just(false);
                    }
                    product.setReservedQuantity(product.getReservedQuantity() + delta);
                    product.getReservedByOrder().put(orderId, quantity);
                    return productRepository.save(product)
                            .flatMap(saved -> productCacheService.evictProduct(productId).thenReturn(saved))
                            .doOnSuccess(saved -> log.info("Reserved {} units (delta {}) of product {} for order {}", quantity, delta, productId, orderId))
                            .thenReturn(true);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Reserve failed: product {} not found for order {}", productId, orderId);
                    return Mono.just(false);
                }));
    }

    @CircuitBreaker(name = "mongoDB", fallbackMethod = "releaseFallback")
    public Mono<Void> release(String orderId, String productId, int quantity) {
        return productRepository.findById(productId)
                .flatMap(product -> {
                    Integer reserved = product.getReservedByOrder().get(orderId);
                    if (reserved == null) {
                        log.info("Release skipped: order {} has no active reservation on product {} (idempotent)", orderId, productId);
                        return Mono.empty();
                    }
                    int newReserved = Math.max(0, product.getReservedQuantity() - reserved);
                    product.setReservedQuantity(newReserved);
                    product.getReservedByOrder().remove(orderId);
                    return productRepository.save(product)
                            .flatMap(saved -> productCacheService.evictProduct(productId).thenReturn(saved))
                            .doOnSuccess(saved -> log.info("Released {} units of product {} for order {}", reserved, productId, orderId));
                })
                .then();
    }

    @CircuitBreaker(name = "mongoDB", fallbackMethod = "confirmDispatchFallback")
    public Mono<Void> confirmDispatch(String orderId, String productId, int quantity) {
        return productRepository.findById(productId)
                .flatMap(product -> {
                    Integer reserved = product.getReservedByOrder().get(orderId);
                    if (reserved == null) {
                        log.info("Confirm dispatch skipped: order {} has no active reservation on product {} (idempotent)", orderId, productId);
                        return Mono.empty();
                    }
                    product.setQuantity(product.getQuantity() - reserved);
                    product.setReservedQuantity(Math.max(0, product.getReservedQuantity() - reserved));
                    product.getReservedByOrder().remove(orderId);
                    return productRepository.save(product)
                            .flatMap(saved -> productCacheService.evictProduct(productId).thenReturn(saved))
                            .doOnSuccess(saved -> log.info("Confirmed dispatch of {} units of product {} for order {}", reserved, productId, orderId));
                })
                .then();
    }

    @CircuitBreaker(name = "mongoDB", fallbackMethod = "getProductFallback")
    public Mono<Product> getProduct(String productId) {
        return productCacheService.getCachedProduct(productId)
                .switchIfEmpty(
                    productRepository.findById(productId)
                        .flatMap(productCacheService::cacheProduct)
                );
    }

    @CircuitBreaker(name = "mongoDB", fallbackMethod = "getAllProductsFallback")
    public Flux<Product> getAllProducts() {
        return productRepository.findAll();
    }

    @CircuitBreaker(name = "mongoDB", fallbackMethod = "createProductFallback")
    public Mono<Product> createProduct(Product product) {
        if (product.getReservedQuantity() == null) {
            product.setReservedQuantity(0);
        }
        return productRepository.save(product)
                .flatMap(productCacheService::cacheProduct)
                .doOnSuccess(saved -> log.info("Created product: {} ({})", saved.getName(), saved.getId()));
    }

    @CircuitBreaker(name = "mongoDB", fallbackMethod = "updateStockFallback")
    public Mono<Product> updateStock(String productId, int quantity) {
        return productRepository.findById(productId)
                .flatMap(product -> {
                    product.setQuantity(quantity);
                    return productRepository.save(product)
                            .flatMap(saved -> productCacheService.evictProduct(productId).thenReturn(saved))
                            .doOnSuccess(updated -> log.info("Updated stock for product {} to {}", productId, quantity));
                });
    }

    // Fallback methods
    private Mono<Boolean> existsFallback(String productId, Throwable t) {
        log.error("CircuitBreaker OPEN [mongoDB] - exists failed for product: {}. Error: {}", productId, t.getMessage());
        return Mono.just(false);
    }

    private Mono<Boolean> isAvailableFallback(String productId, int quantity, Throwable t) {
        log.error("CircuitBreaker OPEN [mongoDB] - isAvailable failed for product: {}. Error: {}", productId, t.getMessage());
        return Mono.just(false);
    }

    private Mono<Integer> getAvailableQuantityFallback(String productId, Throwable t) {
        log.error("CircuitBreaker OPEN [mongoDB] - getAvailableQuantity failed for product: {}. Error: {}", productId, t.getMessage());
        return Mono.just(0);
    }

    private Mono<Boolean> reserveFallback(String orderId, String productId, int quantity, Throwable t) {
        log.error("CircuitBreaker OPEN [mongoDB] - reserve failed for order: {}. Error: {}", orderId, t.getMessage());
        return Mono.just(false);
    }

    private Mono<Void> releaseFallback(String orderId, String productId, int quantity, Throwable t) {
        log.error("CircuitBreaker OPEN [mongoDB] - release failed for order: {}. Error: {}", orderId, t.getMessage());
        return Mono.empty();
    }

    private Mono<Void> confirmDispatchFallback(String orderId, String productId, int quantity, Throwable t) {
        log.error("CircuitBreaker OPEN [mongoDB] - confirmDispatch failed for order: {}. Error: {}", orderId, t.getMessage());
        return Mono.empty();
    }

    private Mono<Product> getProductFallback(String productId, Throwable t) {
        log.error("CircuitBreaker OPEN [mongoDB] - getProduct failed for product: {}. Error: {}", productId, t.getMessage());
        return Mono.empty();
    }

    private Flux<Product> getAllProductsFallback(Throwable t) {
        log.error("CircuitBreaker OPEN [mongoDB] - getAllProducts failed. Error: {}", t.getMessage());
        return Flux.empty();
    }

    private Mono<Product> createProductFallback(Product product, Throwable t) {
        log.error("CircuitBreaker OPEN [mongoDB] - createProduct failed. Error: {}", t.getMessage());
        return Mono.error(new RuntimeException("Service temporarily unavailable. Please try again later."));
    }

    private Mono<Product> updateStockFallback(String productId, int quantity, Throwable t) {
        log.error("CircuitBreaker OPEN [mongoDB] - updateStock failed for product: {}. Error: {}", productId, t.getMessage());
        return Mono.error(new RuntimeException("Service temporarily unavailable. Please try again later."));
    }
}
