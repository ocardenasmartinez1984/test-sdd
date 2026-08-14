package com.stock.interfaces.rest;

import com.stock.application.StockApplicationService;
import com.stock.domain.model.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
public class StockController {

    private final StockApplicationService stockApplicationService;

    @GetMapping
    public Flux<Product> getAllProducts() {
        return stockApplicationService.getAllProducts();
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<Product>> getProduct(@PathVariable String id) {
        return stockApplicationService.getProduct(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/available")
    public Mono<ResponseEntity<Map<String, Integer>>> getAvailableQuantity(@PathVariable String id) {
        return stockApplicationService.exists(id)
                .flatMap(exists -> {
                    if (!exists) {
                        return Mono.just(ResponseEntity.notFound().<Map<String, Integer>>build());
                    }
                    return stockApplicationService.getAvailableQuantity(id)
                            .map(available -> ResponseEntity.ok(Map.of("availableQuantity", available)));
                });
    }

    @GetMapping("/{id}/exists")
    public Mono<ResponseEntity<Map<String, Boolean>>> exists(@PathVariable String id) {
        return stockApplicationService.exists(id)
                .map(exists -> ResponseEntity.ok(Map.of("exists", exists)));
    }

    @PostMapping
    public Mono<ResponseEntity<Product>> createProduct(@RequestBody Product product) {
        return stockApplicationService.createProduct(product)
                .map(ResponseEntity::ok);
    }

    @PutMapping("/{id}/quantity")
    public Mono<ResponseEntity<Product>> updateStock(@PathVariable String id, @RequestBody Map<String, Integer> body) {
        Integer quantity = body.get("quantity");
        if (quantity == null) {
            return Mono.just(ResponseEntity.badRequest().build());
        }
        return stockApplicationService.updateStock(id, quantity)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
