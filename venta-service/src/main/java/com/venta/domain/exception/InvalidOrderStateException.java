package com.venta.domain.exception;

/**
 * Raised when a business rule prevents an operation — e.g. trying to cancel an
 * already-completed order.
 */
public class InvalidOrderStateException extends RuntimeException {

    public InvalidOrderStateException(String message) {
        super(message);
    }
}
