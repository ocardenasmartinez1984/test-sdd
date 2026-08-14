package com.stock.application;

import com.stock.domain.model.Product;
import com.stock.domain.repository.ProductRepository;
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

    public Mono<Boolean> exists(String productId) {
        return productRepository.existsById(productId);
    }

    public Mono<Boolean> isAvailable(String productId, int quantity) {
        return productRepository.findById(productId)
                .map(product -> (product.getQuantity() - product.getReservedQuantity()) >= quantity)
                .defaultIfEmpty(false);
    }

    public Mono<Integer> getAvailableQuantity(String productId) {
        return productRepository.findById(productId)
                .map(product -> product.getQuantity() - product.getReservedQuantity())
                .defaultIfEmpty(0);
    }

    public Mono<Boolean> reserve(String orderId, String productId, int quantity) {
        return productRepository.findById(productId)
                .flatMap(product -> {
                    int available = product.getQuantity() - product.getReservedQuantity();
                    if (available < quantity) {
                        log.warn("Reserve failed: insufficient stock for product {}. Available: {}, Requested: {}", productId, available, quantity);
                        return Mono.just(false);
                    }
                    product.setReservedQuantity(product.getReservedQuantity() + quantity);
                    return productRepository.save(product)
                            .doOnSuccess(saved -> log.info("Reserved {} units of product {} for order {}", quantity, productId, orderId))
                            .thenReturn(true);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Reserve failed: product {} not found for order {}", productId, orderId);
                    return Mono.just(false);
                }));
    }

    public Mono<Void> release(String orderId, String productId, int quantity) {
        return productRepository.findById(productId)
                .flatMap(product -> {
                    int newReserved = Math.max(0, product.getReservedQuantity() - quantity);
                    product.setReservedQuantity(newReserved);
                    return productRepository.save(product)
                            .doOnSuccess(saved -> log.info("Released {} units of product {} for order {}", quantity, productId, orderId));
                })
                .then();
    }

    public Mono<Void> confirmDispatch(String orderId, String productId, int quantity) {
        return productRepository.findById(productId)
                .flatMap(product -> {
                    product.setQuantity(product.getQuantity() - quantity);
                    product.setReservedQuantity(product.getReservedQuantity() - quantity);
                    return productRepository.save(product)
                            .doOnSuccess(saved -> log.info("Confirmed dispatch of {} units of product {} for order {}", quantity, productId, orderId));
                })
                .then();
    }

    public Mono<Product> getProduct(String productId) {
        return productRepository.findById(productId);
    }

    public Flux<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public Mono<Product> createProduct(Product product) {
        if (product.getReservedQuantity() == null) {
            product.setReservedQuantity(0);
        }
        return productRepository.save(product)
                .doOnSuccess(saved -> log.info("Created product: {} ({})", saved.getName(), saved.getId()));
    }

    public Mono<Product> updateStock(String productId, int quantity) {
        return productRepository.findById(productId)
                .flatMap(product -> {
                    product.setQuantity(quantity);
                    return productRepository.save(product)
                            .doOnSuccess(updated -> log.info("Updated stock for product {} to {}", productId, quantity));
                });
    }
}
