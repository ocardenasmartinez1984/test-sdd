package com.venta.application.saga;

import com.venta.domain.event.DespachoRequestEvent;
import com.venta.domain.event.DespachoResponseEvent;
import com.venta.domain.event.StockReserveEvent;
import com.venta.domain.event.StockReserveResponseEvent;
import com.venta.domain.exception.OrderNotFoundException;
import com.venta.domain.model.Order;
import com.venta.domain.model.Order.OrderStatus;
import com.venta.domain.port.DespachoEventPublisher;
import com.venta.domain.port.StockEventPublisher;
import com.venta.domain.repository.OrderRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Orquesta la SAGA de venta entre los servicios de stock y despacho.
 *
 * <p><b>Manejo de fallos.</b> Cada manejador está protegido por un circuit
 * breaker sobre la dependencia de MongoDB. Antes los métodos de fallback
 * tragaban el fallo devolviendo {@code Mono.empty()}, lo que descartaba
 * silenciosamente el evento Kafka y dejaba la orden atascada en un estado
 * intermedio para siempre. Ahora los fallbacks <b>propagan</b> el error para que
 * el fallo llegue al listener de Kafka, cuyo {@code DefaultErrorHandler}
 * reintenta y, al agotar los reintentos, enruta el registro al tópico
 * dead-letter en lugar de perderlo. Las órdenes que aun así queden atascadas
 * (por ejemplo, porque nunca llega una respuesta aguas abajo) las barre
 * {@link com.venta.application.saga.SagaReconciler}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SagaOrchestrator {

    private final OrderRepository orderRepository;
    private final StockEventPublisher stockEventPublisher;
    private final DespachoEventPublisher despachoEventPublisher;

    /**
     * Procesa la respuesta del stock-service a una reserva y avanza la SAGA.
     *
     * <p>Si la reserva tuvo éxito, pasa la orden a {@link OrderStatus#STOCK_RESERVED}
     * y publica un {@link DespachoRequestEvent} para solicitar el despacho. Si
     * falló, la marca como {@link OrderStatus#STOCK_FAILED} y guarda el motivo.
     *
     * @param event respuesta con orden, producto, éxito y motivo
     * @return {@link Mono} que completa al persistir el nuevo estado
     * @throws OrderNotFoundException si la orden referenciada no existe
     */
    @CircuitBreaker(name = "mongoDB", fallbackMethod = "handleStockResponseFallback")
    public Mono<Void> handleStockResponse(StockReserveResponseEvent event) {
        return orderRepository.findById(event.getOrderId())
                .switchIfEmpty(Mono.error(new OrderNotFoundException(event.getOrderId())))
                .flatMap(order -> {
                    if (Boolean.TRUE.equals(event.getSuccess())) {
                        order.setStatus(OrderStatus.STOCK_RESERVED);
                        order.setUpdatedAt(LocalDateTime.now());

                        return orderRepository.save(order)
                                .doOnSuccess(savedOrder -> {
                                    DespachoRequestEvent despachoEvent = DespachoRequestEvent.builder()
                                            .orderId(savedOrder.getId())
                                            .productId(savedOrder.getProductId())
                                            .quantity(savedOrder.getQuantity())
                                            .customerId(savedOrder.getCustomerId())
                                            .build();

                                    despachoEventPublisher.requestDespacho(despachoEvent);
                                    log.info("Despacho request sent for order: {}", savedOrder.getId());
                                });
                    } else {
                        order.setStatus(OrderStatus.STOCK_FAILED);
                        order.setFailureReason(event.getReason());
                        order.setUpdatedAt(LocalDateTime.now());

                        return orderRepository.save(order)
                                .doOnSuccess(savedOrder ->
                                        log.info("Stock reservation failed for order: {}", savedOrder.getId()));
                    }
                })
                .then();
    }

    /**
     * Procesa la respuesta del despacho-service y avanza o compensa la SAGA.
     *
     * <p>Si el despacho fue aceptado, pasa la orden a {@link OrderStatus#DISPATCHING}.
     * Si falló, la marca como {@link OrderStatus#DISPATCH_FAILED} y publica un
     * evento de compensación de stock para liberar las unidades reservadas.
     *
     * @param event respuesta con orden, éxito, tracking y motivo
     * @return {@link Mono} que completa al persistir el nuevo estado
     * @throws OrderNotFoundException si la orden referenciada no existe
     */
    @CircuitBreaker(name = "mongoDB", fallbackMethod = "handleDespachoResponseFallback")
    public Mono<Void> handleDespachoResponse(DespachoResponseEvent event) {
        return orderRepository.findById(event.getOrderId())
                .switchIfEmpty(Mono.error(new OrderNotFoundException(event.getOrderId())))
                .flatMap(order -> {
                    if (Boolean.TRUE.equals(event.getSuccess())) {
                        order.setStatus(OrderStatus.DISPATCHING);
                        order.setUpdatedAt(LocalDateTime.now());

                        return orderRepository.save(order)
                                .doOnSuccess(savedOrder ->
                                        log.info("Order dispatching: {} (tracking: {})", savedOrder.getId(), event.getTrackingNumber()));
                    } else {
                        order.setStatus(OrderStatus.DISPATCH_FAILED);
                        order.setFailureReason(event.getReason());
                        order.setUpdatedAt(LocalDateTime.now());

                        return orderRepository.save(order)
                                .doOnSuccess(savedOrder -> {
                                    StockReserveEvent compensateEvent = StockReserveEvent.builder()
                                            .orderId(savedOrder.getId())
                                            .productId(savedOrder.getProductId())
                                            .quantity(savedOrder.getQuantity())
                                            .build();
                                    stockEventPublisher.compensateStock(compensateEvent);
                                    log.info("Dispatch failed for order: {}. Stock compensate sent.", savedOrder.getId());
                                });
                    }
                })
                .then();
    }

    /**
     * Cierra la SAGA al notificarse la entrega de un pedido.
     *
     * <p>Si la orden está en {@link OrderStatus#DISPATCHING}, la marca como
     * {@link OrderStatus#COMPLETED} y publica un evento de confirmación de stock
     * que convierte la reserva en un descuento definitivo. Es idempotente: si la
     * orden no está en despacho no realiza cambios.
     *
     * @param orderId identificador de la orden entregada
     * @return {@link Mono} que completa tras procesar la entrega
     * @throws OrderNotFoundException si la orden referenciada no existe
     */
    @CircuitBreaker(name = "mongoDB", fallbackMethod = "handleDespachoDeliveredFallback")
    public Mono<Void> handleDespachoDelivered(String orderId) {
        return orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new OrderNotFoundException(orderId)))
                .flatMap(order -> {
                    if (order.getStatus() == OrderStatus.DISPATCHING) {
                        order.setStatus(OrderStatus.COMPLETED);
                        order.setUpdatedAt(LocalDateTime.now());

                        return orderRepository.save(order)
                                .doOnSuccess(savedOrder -> {
                                    // Confirm the sale: turn the reservation into an actual
                                    // stock decrement (quantity -= reserved) in the stock service.
                                    StockReserveEvent confirmEvent = StockReserveEvent.builder()
                                            .orderId(savedOrder.getId())
                                            .productId(savedOrder.getProductId())
                                            .quantity(savedOrder.getQuantity())
                                            .build();
                                    stockEventPublisher.confirmStock(confirmEvent);
                                    log.info("Order completed (delivered): {}. Stock confirm sent.", savedOrder.getId());
                                });
                    }
                    return Mono.just(order);
                })
                .then();
    }

    // ------------------------------------------------------------------
    // Métodos de fallback
    //
    // Una orden inexistente (OrderNotFoundException) es una condición terminal y
    // no reintentable: reintentar nunca hará aparecer el agregado, así que
    // completamos vacío para que el listener confirme el offset y no envenenemos
    // la DLQ.
    //
    // Cualquier otro error (circuit breaker ABIERTO, timeout de BD, conflicto de
    // escritura) es transitorio: lo PROPAGAMOS para que el DefaultErrorHandler del
    // listener de Kafka reintente y, al agotarse, publique en el tópico
    // dead-letter. Esto es lo que evita que el evento SAGA se pierda en silencio.
    // ------------------------------------------------------------------

    private Mono<Void> handleStockResponseFallback(StockReserveResponseEvent event, Throwable t) {
        return terminalOrPropagate("handleStockResponse", event.getOrderId(), t);
    }

    private Mono<Void> handleDespachoResponseFallback(DespachoResponseEvent event, Throwable t) {
        return terminalOrPropagate("handleDespachoResponse", event.getOrderId(), t);
    }

    private Mono<Void> handleDespachoDeliveredFallback(String orderId, Throwable t) {
        return terminalOrPropagate("handleDespachoDelivered", orderId, t);
    }

    private Mono<Void> terminalOrPropagate(String operation, String orderId, Throwable t) {
        if (t instanceof OrderNotFoundException) {
            log.error("{} - order not found (non-retryable): {}", operation, orderId);
            return Mono.empty();
        }
        log.error("{} - transient failure for order {} ({}). Propagating for retry/DLQ.",
                operation, orderId, t.getMessage());
        return Mono.error(t);
    }
}
