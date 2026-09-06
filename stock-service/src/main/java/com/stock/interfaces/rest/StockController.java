package com.stock.interfaces.rest;

import com.stock.application.StockApplicationService;
import com.stock.domain.model.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Controlador REST (capa de interfaces DDD) que expone la API pública del
 * inventario bajo la ruta {@code /api/v1/stock}.
 *
 * <p>Es el adaptador de entrada HTTP que traduce las peticiones del cliente
 * (consulta de catálogo, disponibilidad, existencia, alta y ajuste de stock) en
 * invocaciones al {@link StockApplicationService}, devolviendo respuestas
 * reactivas ({@link Mono}/{@link Flux}) y los códigos HTTP adecuados.</p>
 */
@RestController
@RequestMapping("/api/v1/stock")
@RequiredArgsConstructor
public class StockController {

    private final StockApplicationService stockApplicationService;

    /**
     * Lista todos los productos del catálogo.
     *
     * @return un {@link Flux} con todos los productos disponibles
     */
    @GetMapping
    public Flux<Product> getAllProducts() {
        return stockApplicationService.getAllProducts();
    }

    /**
     * Obtiene un producto por su identificador.
     *
     * @param id identificador del producto
     * @return {@code 200 OK} con el producto, o {@code 404 Not Found} si no existe
     */
    @GetMapping("/{id}")
    public Mono<ResponseEntity<Product>> getProduct(@PathVariable String id) {
        return stockApplicationService.getProduct(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Devuelve la cantidad disponible (no reservada) de un producto.
     *
     * <p>Verifica primero la existencia del producto: si no existe responde
     * {@code 404 Not Found}; en caso contrario devuelve el disponible en un mapa
     * bajo la clave {@code availableQuantity}.</p>
     *
     * @param id identificador del producto
     * @return {@code 200 OK} con {@code {"availableQuantity": n}}, o
     *         {@code 404 Not Found} si el producto no existe
     */
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

    /**
     * Indica si existe un producto con el identificador dado.
     *
     * @param id identificador del producto
     * @return {@code 200 OK} con {@code {"exists": true|false}}
     */
    @GetMapping("/{id}/exists")
    public Mono<ResponseEntity<Map<String, Boolean>>> exists(@PathVariable String id) {
        return stockApplicationService.exists(id)
                .map(exists -> ResponseEntity.ok(Map.of("exists", exists)));
    }

    /**
     * Da de alta un nuevo producto en el catálogo.
     *
     * @param product producto recibido en el cuerpo de la petición
     * @return {@code 200 OK} con el producto creado
     */
    @PostMapping
    public Mono<ResponseEntity<Product>> createProduct(@RequestBody Product product) {
        return stockApplicationService.createProduct(product)
                .map(ResponseEntity::ok);
    }

    /**
     * Actualiza el stock físico de un producto.
     *
     * <p>Espera en el cuerpo un mapa con la clave {@code quantity}; si falta,
     * responde {@code 400 Bad Request}.</p>
     *
     * @param id   identificador del producto a actualizar
     * @param body cuerpo con la nueva cantidad bajo la clave {@code quantity}
     * @return {@code 200 OK} con el producto actualizado, {@code 400 Bad Request}
     *         si no se envía {@code quantity}, o {@code 404 Not Found} si el
     *         producto no existe
     */
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
