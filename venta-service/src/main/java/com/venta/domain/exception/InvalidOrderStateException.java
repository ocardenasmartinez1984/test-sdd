package com.venta.domain.exception;

/**
 * Se lanza cuando una regla de negocio impide una operación; por ejemplo,
 * intentar cancelar una orden ya completada.
 */
public class InvalidOrderStateException extends RuntimeException {

    public InvalidOrderStateException(String message) {
        super(message);
    }
}
