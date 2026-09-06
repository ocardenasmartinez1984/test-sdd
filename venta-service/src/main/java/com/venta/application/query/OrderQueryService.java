package com.venta.application.query;

import com.venta.domain.exception.OrderNotFoundException;
import com.venta.domain.model.Order;
import com.venta.domain.model.Order.OrderStatus;
import com.venta.domain.repository.OrderRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Servicio de aplicación (lado <i>query</i> de CQRS) para las operaciones de
 * lectura sobre órdenes de venta.
 *
 * <p>Consulta MongoDB a través de {@link OrderRepository}. Cada método está
 * protegido por un circuit breaker sobre MongoDB; los fallbacks devuelven flujos
 * vacíos o repropagan un {@link OrderNotFoundException} legítimo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderQueryService {

    private final OrderRepository orderRepository;

    /**
     * Obtiene una orden por su identificador.
     *
     * @param orderId identificador de la orden
     * @return {@link Mono} con la orden encontrada
     * @throws OrderNotFoundException si la orden no existe
     */
    @CircuitBreaker(name = "mongoDB", fallbackMethod = "getVentaFallback")
    public Mono<Order> getVenta(String orderId) {
        return orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new OrderNotFoundException(orderId)));
    }

    /**
     * Lista todas las órdenes.
     *
     * @return flujo con todas las órdenes
     */
    @CircuitBreaker(name = "mongoDB", fallbackMethod = "listarVentasFallback")
    public Flux<Order> listarVentas() {
        return orderRepository.findAll();
    }

    /**
     * Lista las órdenes de un cliente.
     *
     * @param customerId identificador del cliente
     * @return flujo de órdenes del cliente
     */
    @CircuitBreaker(name = "mongoDB", fallbackMethod = "ventasPorClienteFallback")
    public Flux<Order> ventasPorCliente(String customerId) {
        return orderRepository.findByCustomerId(customerId);
    }

    /**
     * Lista las órdenes que están en un estado dado.
     *
     * @param status estado por el que filtrar
     * @return flujo de órdenes en ese estado
     */
    @CircuitBreaker(name = "mongoDB", fallbackMethod = "ventasPorEstadoFallback")
    public Flux<Order> ventasPorEstado(OrderStatus status) {
        return orderRepository.findByStatus(status);
    }

    // Fallback methods
    private Mono<Order> getVentaFallback(String orderId, Throwable t) {
        // A missing order is a legitimate 404, not a circuit-breaker outage:
        // repropagate it instead of masking it as "service unavailable".
        if (t instanceof OrderNotFoundException) {
            return Mono.error(t);
        }
        log.error("CircuitBreaker OPEN [mongoDB] - getVenta failed for order: {}. Error: {}", orderId, t.getMessage());
        return Mono.error(new RuntimeException("Sales service temporarily unavailable. Please try again later."));
    }

    private Flux<Order> listarVentasFallback(Throwable t) {
        log.error("CircuitBreaker OPEN [mongoDB] - listarVentas failed. Error: {}", t.getMessage());
        return Flux.empty();
    }

    private Flux<Order> ventasPorClienteFallback(String customerId, Throwable t) {
        log.error("CircuitBreaker OPEN [mongoDB] - ventasPorCliente failed. Error: {}", t.getMessage());
        return Flux.empty();
    }

    private Flux<Order> ventasPorEstadoFallback(OrderStatus status, Throwable t) {
        log.error("CircuitBreaker OPEN [mongoDB] - ventasPorEstado failed. Error: {}", t.getMessage());
        return Flux.empty();
    }
}
