package com.venta.application.command;

import com.venta.domain.event.StockReserveEvent;
import com.venta.domain.exception.InvalidOrderStateException;
import com.venta.domain.exception.OrderNotFoundException;
import com.venta.domain.model.Order;
import com.venta.domain.model.Order.OrderStatus;
import com.venta.domain.port.StockEventPublisher;
import com.venta.domain.repository.OrderRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Servicio de aplicación (lado <i>command</i> de CQRS) para las operaciones de
 * escritura sobre órdenes de venta.
 *
 * <p>Inicia la SAGA de venta al crear una orden y gestiona su cancelación y
 * cambios de estado, publicando los comandos de stock correspondientes mediante
 * {@link StockEventPublisher} y persistiendo en MongoDB vía
 * {@link OrderRepository}. Cada operación está protegida por un circuit breaker
 * sobre MongoDB con su método de fallback.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCommandService {

    private final OrderRepository orderRepository;
    private final StockEventPublisher stockEventPublisher;

    /**
     * Crea una nueva orden e inicia la SAGA de venta.
     *
     * <p>Fija el estado en {@link OrderStatus#PENDING} y las marcas de tiempo,
     * persiste la orden en MongoDB y, tras guardarla con éxito, publica un
     * {@link StockReserveEvent} para que el stock-service reserve las unidades.
     *
     * @param order orden a crear (sin estado ni fechas asignadas)
     * @return {@link Mono} con la orden persistida
     */
    @CircuitBreaker(name = "mongoDB", fallbackMethod = "crearVentaFallback")
    public Mono<Order> crearVenta(Order order) {
        order.setStatus(OrderStatus.PENDING);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        return orderRepository.save(order)
                .doOnSuccess(savedOrder -> {
                    log.info("Order created: {}", savedOrder.getId());

                    StockReserveEvent event = StockReserveEvent.builder()
                            .orderId(savedOrder.getId())
                            .productId(savedOrder.getProductId())
                            .quantity(savedOrder.getQuantity())
                            .build();

                    stockEventPublisher.reserveStock(event);
                    log.info("Stock reserve event sent for order: {}", savedOrder.getId());
                });
    }

    /**
     * Cancela una orden existente y, si procede, compensa el stock reservado.
     *
     * <p>Rechaza la cancelación si la orden está {@code COMPLETED} o
     * {@code CANCELLED}. Al cancelar una orden que estaba en {@code STOCK_RESERVED}
     * o {@code DISPATCHING} publica un evento de compensación de stock para
     * liberar las unidades reservadas.
     *
     * @param orderId identificador de la orden a cancelar
     * @return {@link Mono} con la orden cancelada
     * @throws OrderNotFoundException si la orden no existe
     * @throws InvalidOrderStateException si la orden no puede cancelarse por su estado
     */
    @CircuitBreaker(name = "mongoDB", fallbackMethod = "cancelarVentaFallback")
    public Mono<Order> cancelarVenta(String orderId) {
        return orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new OrderNotFoundException(orderId)))
                .flatMap(order -> {
                    if (order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.CANCELLED) {
                        return Mono.error(new InvalidOrderStateException("Cannot cancel order in status: " + order.getStatus()));
                    }

                    OrderStatus previousStatus = order.getStatus();
                    order.setStatus(OrderStatus.CANCELLED);
                    order.setUpdatedAt(LocalDateTime.now());

                    return orderRepository.save(order)
                            .doOnSuccess(savedOrder -> {
                                if (previousStatus == OrderStatus.STOCK_RESERVED || previousStatus == OrderStatus.DISPATCHING) {
                                    StockReserveEvent compensateEvent = StockReserveEvent.builder()
                                            .orderId(savedOrder.getId())
                                            .productId(savedOrder.getProductId())
                                            .quantity(savedOrder.getQuantity())
                                            .build();
                                    stockEventPublisher.compensateStock(compensateEvent);
                                    log.info("Stock compensate event sent for order: {}", orderId);
                                }
                                log.info("Order cancelled: {}", orderId);
                            });
                });
    }

    /**
     * Actualiza el estado de una orden de forma directa (sin efectos SAGA).
     *
     * @param orderId identificador de la orden
     * @param nuevoEstado nuevo estado a asignar
     * @return {@link Mono} con la orden actualizada
     * @throws OrderNotFoundException si la orden no existe
     */
    @CircuitBreaker(name = "mongoDB", fallbackMethod = "actualizarEstadoFallback")
    public Mono<Order> actualizarEstado(String orderId, OrderStatus nuevoEstado) {
        return orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new OrderNotFoundException(orderId)))
                .flatMap(order -> {
                    order.setStatus(nuevoEstado);
                    order.setUpdatedAt(LocalDateTime.now());
                    return orderRepository.save(order);
                })
                .doOnSuccess(saved -> log.info("Order {} status updated to: {}", orderId, nuevoEstado));
    }

    // Fallback methods
    private Mono<Order> crearVentaFallback(Order order, Throwable t) {
        log.error("CircuitBreaker OPEN [mongoDB] - crearVenta failed. Error: {}", t.getMessage());
        return Mono.error(new RuntimeException("Sales service temporarily unavailable. Please try again later."));
    }

    private Mono<Order> cancelarVentaFallback(String orderId, Throwable t) {
        log.error("CircuitBreaker OPEN [mongoDB] - cancelarVenta failed for order: {}. Error: {}", orderId, t.getMessage());
        return Mono.error(new RuntimeException("Sales service temporarily unavailable. Please try again later."));
    }

    private Mono<Order> actualizarEstadoFallback(String orderId, OrderStatus nuevoEstado, Throwable t) {
        log.error("CircuitBreaker OPEN [mongoDB] - actualizarEstado failed for order: {}. Error: {}", orderId, t.getMessage());
        return Mono.error(new RuntimeException("Sales service temporarily unavailable. Please try again later."));
    }
}
