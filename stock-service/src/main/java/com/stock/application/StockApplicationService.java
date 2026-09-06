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

/**
 * Servicio de aplicación (capa de aplicación DDD) que orquesta los casos de uso
 * del inventario.
 *
 * <p>Coordina el repositorio de dominio {@link ProductRepository} con la caché
 * de productos {@link ProductCacheService} para consultar disponibilidad,
 * reservar, liberar y confirmar stock durante la SAGA de ventas, así como para
 * el mantenimiento del catálogo (alta y ajuste de existencias). Todas las
 * operaciones son reactivas ({@link Mono}/{@link Flux}) y están protegidas con
 * un circuit breaker de Resilience4j (instancia {@code mongoDB}); cada método
 * público declara un método de fallback que degrada la respuesta cuando el
 * circuito está abierto.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockApplicationService {

    private final ProductRepository productRepository;
    private final ProductCacheService productCacheService;

    /**
     * Comprueba si existe un producto con el identificador dado.
     *
     * @param productId identificador del producto a verificar
     * @return un {@link Mono} con {@code true} si el producto existe,
     *         {@code false} en caso contrario o si el circuito está abierto
     */
    @CircuitBreaker(name = "mongoDB", fallbackMethod = "existsFallback")
    public Mono<Boolean> exists(String productId) {
        return productRepository.existsById(productId);
    }

    /**
     * Determina si hay stock disponible suficiente para atender una cantidad.
     *
     * <p>Calcula la disponibilidad como {@code quantity - reservedQuantity} del
     * producto y la compara con la cantidad solicitada.</p>
     *
     * @param productId identificador del producto
     * @param quantity  cantidad requerida
     * @return un {@link Mono} con {@code true} si el disponible cubre la cantidad
     *         solicitada; {@code false} si no alcanza, si el producto no existe o
     *         si el circuito está abierto
     */
    @CircuitBreaker(name = "mongoDB", fallbackMethod = "isAvailableFallback")
    public Mono<Boolean> isAvailable(String productId, int quantity) {
        return productRepository.findById(productId)
                .map(product -> (product.getQuantity() - product.getReservedQuantity()) >= quantity)
                .defaultIfEmpty(false);
    }

    /**
     * Devuelve la cantidad disponible (no reservada) de un producto.
     *
     * <p>Se calcula como {@code quantity - reservedQuantity}.</p>
     *
     * @param productId identificador del producto
     * @return un {@link Mono} con las unidades disponibles; {@code 0} si el
     *         producto no existe o si el circuito está abierto
     */
    @CircuitBreaker(name = "mongoDB", fallbackMethod = "getAvailableQuantityFallback")
    public Mono<Integer> getAvailableQuantity(String productId) {
        return productRepository.findById(productId)
                .map(product -> product.getQuantity() - product.getReservedQuantity())
                .defaultIfEmpty(0);
    }

    /**
     * Reserva stock de un producto para un pedido de forma idempotente.
     *
     * <p>Aplica únicamente el <i>delta</i> entre la cantidad solicitada y la ya
     * reservada por ese mismo pedido (registrada en {@code reservedByOrder}):</p>
     * <ul>
     *   <li>Si el delta es cero (reintento o reenvío con la misma cantidad), no
     *       modifica nada y responde {@code true} (idempotencia).</li>
     *   <li>Si el delta supera el stock disponible, no reserva y responde
     *       {@code false}.</li>
     *   <li>En caso contrario incrementa {@code reservedQuantity}, actualiza el
     *       mapa {@code reservedByOrder}, persiste el producto en MongoDB e
     *       invalida su entrada de caché en Redis.</li>
     * </ul>
     *
     * @param orderId   identificador del pedido (o ítem de carrito) que reserva
     * @param productId identificador del producto a reservar
     * @param quantity  cantidad total que debe quedar reservada para el pedido
     * @return un {@link Mono} con {@code true} si la reserva quedó garantizada;
     *         {@code false} si no hay stock suficiente, el producto no existe o
     *         el circuito está abierto
     */
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

    /**
     * Libera (compensa) la reserva de stock que un pedido mantiene sobre un
     * producto, como paso de compensación de la SAGA.
     *
     * <p>Si el pedido no tiene reserva activa sobre el producto, la operación se
     * omite (idempotente). En caso contrario descuenta las unidades reservadas de
     * {@code reservedQuantity} (sin bajar de cero), elimina la entrada del mapa
     * {@code reservedByOrder}, persiste el producto e invalida su caché en
     * Redis.</p>
     *
     * @param orderId   identificador del pedido cuya reserva se libera
     * @param productId identificador del producto afectado
     * @param quantity  cantidad de la operación (informativa; se libera lo
     *                  efectivamente reservado por el pedido)
     * @return un {@link Mono} que completa cuando la liberación finaliza
     */
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

    /**
     * Confirma el despacho de un pedido, consolidando de forma definitiva la
     * salida de stock (paso final de la SAGA).
     *
     * <p>Si el pedido no tiene reserva activa sobre el producto, la operación se
     * omite (idempotente). En caso contrario descuenta las unidades reservadas
     * tanto del stock físico ({@code quantity}) como del reservado
     * ({@code reservedQuantity}, sin bajar de cero), elimina la entrada de
     * {@code reservedByOrder}, persiste el producto e invalida su caché en
     * Redis.</p>
     *
     * @param orderId   identificador del pedido cuyo despacho se confirma
     * @param productId identificador del producto despachado
     * @param quantity  cantidad de la operación (informativa; se consolida lo
     *                  efectivamente reservado por el pedido)
     * @return un {@link Mono} que completa cuando la confirmación finaliza
     */
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

    /**
     * Obtiene un producto aplicando la estrategia de caché <i>cache-aside</i>.
     *
     * <p>Primero intenta recuperarlo de la caché Redis; si no está (cache miss),
     * lo lee de MongoDB y lo almacena en caché para futuras consultas.</p>
     *
     * @param productId identificador del producto a obtener
     * @return un {@link Mono} con el producto; vacío si no existe o si el
     *         circuito está abierto
     */
    @CircuitBreaker(name = "mongoDB", fallbackMethod = "getProductFallback")
    public Mono<Product> getProduct(String productId) {
        return productCacheService.getCachedProduct(productId)
                .switchIfEmpty(
                    productRepository.findById(productId)
                        .flatMap(productCacheService::cacheProduct)
                );
    }

    /**
     * Recupera todos los productos del catálogo directamente desde MongoDB.
     *
     * @return un {@link Flux} con todos los productos; vacío si el circuito está
     *         abierto
     */
    @CircuitBreaker(name = "mongoDB", fallbackMethod = "getAllProductsFallback")
    public Flux<Product> getAllProducts() {
        return productRepository.findAll();
    }

    /**
     * Crea un nuevo producto en el catálogo.
     *
     * <p>Inicializa la cantidad reservada a cero si no viene informada, persiste
     * el producto en MongoDB y lo deja cacheado en Redis.</p>
     *
     * @param product producto a dar de alta
     * @return un {@link Mono} con el producto persistido
     * @throws RuntimeException (vía fallback) si el circuito está abierto,
     *         indicando indisponibilidad temporal del servicio
     */
    @CircuitBreaker(name = "mongoDB", fallbackMethod = "createProductFallback")
    public Mono<Product> createProduct(Product product) {
        if (product.getReservedQuantity() == null) {
            product.setReservedQuantity(0);
        }
        return productRepository.save(product)
                .flatMap(productCacheService::cacheProduct)
                .doOnSuccess(saved -> log.info("Created product: {} ({})", saved.getName(), saved.getId()));
    }

    /**
     * Ajusta el stock físico ({@code quantity}) de un producto a un valor dado.
     *
     * <p>Localiza el producto, fija su cantidad, lo persiste en MongoDB e
     * invalida su entrada de caché en Redis.</p>
     *
     * @param productId identificador del producto a actualizar
     * @param quantity  nueva cantidad física de stock
     * @return un {@link Mono} con el producto actualizado; vacío si el producto
     *         no existe
     * @throws RuntimeException (vía fallback) si el circuito está abierto,
     *         indicando indisponibilidad temporal del servicio
     */
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
