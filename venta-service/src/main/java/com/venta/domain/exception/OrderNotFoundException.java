package com.venta.domain.exception;

/**
 * Raised when an order referenced by a SAGA step or command cannot be found.
 *
 * <p>Using a specific domain exception (instead of a bare
 * {@link RuntimeException}) lets callers, error handlers and tests distinguish a
 * genuinely missing aggregate from arbitrary infrastructure failures.
 */
public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(String orderId) {
        super("Order not found: " + orderId);
    }
}
