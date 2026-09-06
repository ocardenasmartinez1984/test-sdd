package com.stock.domain.repository;

import com.stock.domain.model.Product;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Repositorio reactivo del agregado {@link Product} sobre MongoDB.
 *
 * <p>Puerto de persistencia de la capa de dominio. Extiende
 * {@link ReactiveMongoRepository} para heredar las operaciones CRUD reactivas
 * (findById, save, findAll, existsById, etc.) sobre la colección de productos y
 * añade consultas derivadas específicas del inventario.</p>
 */
@Repository
public interface ProductRepository extends ReactiveMongoRepository<Product, String> {

    /**
     * Busca un producto por su SKU (código único de referencia comercial).
     *
     * @param sku SKU del producto a localizar
     * @return un {@link Mono} con el producto encontrado, o vacío si no existe
     */
    Mono<Product> findBySku(String sku);
}
