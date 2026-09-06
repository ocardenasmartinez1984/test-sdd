package com.venta.application.saga;

import com.venta.domain.event.DespachoRequestEvent;
import com.venta.domain.event.StockReserveEvent;
import com.venta.domain.model.Order;
import com.venta.domain.model.Order.OrderStatus;
import com.venta.domain.port.DespachoEventPublisher;
import com.venta.domain.port.StockEventPublisher;
import com.venta.domain.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Barre periódicamente las órdenes que quedaron atascadas en un estado
 * intermedio de la SAGA y las vuelve a impulsar.
 *
 * <p>Aun con reintentos y tópico dead-letter en los consumidores de respuestas,
 * una orden puede estancarse —por ejemplo, si un servicio aguas abajo se cayó
 * antes de emitir su respuesta, o si esa respuesta acabó en la DLQ—. Tal orden
 * queda indefinidamente en {@link OrderStatus#PENDING} (esperando stock) o
 * {@link OrderStatus#STOCK_RESERVED} (esperando despacho). Este reconciliador
 * detecta órdenes que no cambiaron durante más de {@code saga.reconciler.stuck-after}
 * y reemite el comando que debería hacerlas avanzar. La reemisión es segura
 * porque los manejadores aguas abajo son idempotentes por id/estado de orden.
 */
@Slf4j
@Component
public class SagaReconciler {

    private final OrderRepository orderRepository;
    private final StockEventPublisher stockEventPublisher;
    private final DespachoEventPublisher despachoEventPublisher;
    private final Duration stuckAfter;

    private final AtomicBoolean running = new AtomicBoolean(false);

    public SagaReconciler(OrderRepository orderRepository,
                          StockEventPublisher stockEventPublisher,
                          DespachoEventPublisher despachoEventPublisher,
                          @Value("${saga.reconciler.stuck-after:PT2M}") Duration stuckAfter) {
        this.orderRepository = orderRepository;
        this.stockEventPublisher = stockEventPublisher;
        this.despachoEventPublisher = despachoEventPublisher;
        this.stuckAfter = stuckAfter;
    }

    /**
     * Disparador programado del barrido de reconciliación.
     *
     * <p>Usa un flag atómico para evitar solapes: si un barrido anterior sigue en
     * curso, omite este tick. Lanza {@link #reconcile()} de forma asíncrona y
     * registra los errores sin propagarlos.
     */
    @Scheduled(fixedDelayString = "${saga.reconciler.interval-ms:60000}")
    public void reconcileStuckOrders() {
        if (!running.compareAndSet(false, true)) {
            log.trace("Reconciler tick skipped: previous sweep still running");
            return;
        }

        reconcile()
                .doFinally(signal -> running.set(false))
                .subscribe(
                        null,
                        error -> log.error("SAGA reconciler sweep failed", error));
    }

    /**
     * Barrido reactivo, extraído para poder testearse de forma determinista.
     *
     * <p>Calcula el umbral temporal y recupera las órdenes estancadas en
     * {@code PENDING} o {@code STOCK_RESERVED} para re-impulsarlas.
     *
     * @return {@link Mono} que completa cuando se han procesado todas las órdenes estancadas
     */
    Mono<Void> reconcile() {
        LocalDateTime threshold = LocalDateTime.now().minus(stuckAfter);
        return orderRepository
                .findByStatusInAndUpdatedAtBefore(
                        List.of(OrderStatus.PENDING, OrderStatus.STOCK_RESERVED), threshold)
                .doOnNext(this::redrive)
                .then();
    }

    /**
     * Reemite el comando SAGA adecuado según el estado en que se atascó la orden:
     * una reserva de stock si está en {@code PENDING}, o una solicitud de despacho
     * si está en {@code STOCK_RESERVED}. Para otros estados no hace nada.
     *
     * @param order orden estancada a re-impulsar
     */
    private void redrive(Order order) {
        switch (order.getStatus()) {
            case PENDING -> {
                log.warn("Reconciler: order {} stuck in PENDING, re-emitting stock reserve", order.getId());
                stockEventPublisher.reserveStock(StockReserveEvent.builder()
                        .orderId(order.getId())
                        .productId(order.getProductId())
                        .quantity(order.getQuantity())
                        .build());
            }
            case STOCK_RESERVED -> {
                log.warn("Reconciler: order {} stuck in STOCK_RESERVED, re-emitting despacho request", order.getId());
                despachoEventPublisher.requestDespacho(DespachoRequestEvent.builder()
                        .orderId(order.getId())
                        .productId(order.getProductId())
                        .quantity(order.getQuantity())
                        .customerId(order.getCustomerId())
                        .build());
            }
            default -> log.debug("Reconciler: no action for order {} in status {}", order.getId(), order.getStatus());
        }
    }
}
