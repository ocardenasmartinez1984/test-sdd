package com.venta.application.cart;

import com.venta.domain.event.StockReserveEvent;
import com.venta.domain.model.CartItem;
import com.venta.domain.port.StockEventPublisher;
import com.venta.domain.repository.CartRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Libera periódicamente el stock retenido por carritos abandonados.
 *
 * <p>Los ítems de carrito se crean con un {@code expiresAt} de 10 minutos, pero
 * nada actuaba sobre él, de modo que un carrito abandonado mantenía su stock
 * reservado para siempre —inflando el {@code reservedQuantity} del producto (por
 * ejemplo, un carrito con 30 unidades dejaba el producto mostrando 30 reservadas
 * indefinidamente)—. Este barredor encuentra ítems RESERVED cuyo {@code expiresAt}
 * ya pasó, emite un evento de compensación para liberar la reserva en el
 * stock-service y borra el ítem.
 */
@Slf4j
@Component
public class CartExpirer {

    private final CartRepository cartRepository;
    private final StockEventPublisher stockEventPublisher;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public CartExpirer(CartRepository cartRepository, StockEventPublisher stockEventPublisher) {
        this.cartRepository = cartRepository;
        this.stockEventPublisher = stockEventPublisher;
    }

    /**
     * Disparador programado de la expiración de carritos.
     *
     * <p>Usa un flag atómico para evitar solapes con un barrido en curso; lanza
     * {@link #expire()} de forma asíncrona y registra los errores.
     */
    @Scheduled(fixedDelayString = "${cart.expirer.interval-ms:60000}")
    public void expireAbandonedCarts() {
        if (!running.compareAndSet(false, true)) {
            log.trace("Cart expirer tick skipped: previous sweep still running");
            return;
        }
        expire()
                .doFinally(signal -> running.set(false))
                .subscribe(null, error -> log.error("Cart expirer sweep failed", error));
    }

    /**
     * Barrido reactivo, extraído para poder testearse de forma determinista.
     *
     * <p>Busca los ítems RESERVED ya expirados y los libera y elimina.
     *
     * @return {@link Mono} que completa cuando se han procesado todos los ítems expirados
     */
    Mono<Void> expire() {
        return cartRepository
                .findByStatusAndExpiresAtBefore(CartItem.STATUS_RESERVED, LocalDateTime.now())
                .flatMap(this::releaseAndDelete)
                .then();
    }

    /**
     * Libera la reserva de stock de un ítem de carrito expirado y luego lo borra.
     *
     * <p>Publica un evento de compensación de stock (usando el id del ítem como
     * orderId) para devolver las unidades y elimina el ítem de MongoDB.
     *
     * @param item ítem de carrito expirado a liberar y eliminar
     * @return {@link Mono} que completa tras borrar el ítem
     */
    private Mono<Void> releaseAndDelete(CartItem item) {
        StockReserveEvent compensateEvent = StockReserveEvent.builder()
                .orderId(item.getId())
                .productId(item.getProductId())
                .quantity(item.getQuantity())
                .build();
        stockEventPublisher.compensateStock(compensateEvent);
        log.info("Cart expirer: released reservation for abandoned cart item {} (product {}, qty {})",
                item.getId(), item.getProductId(), item.getQuantity());
        return cartRepository.delete(item);
    }
}
