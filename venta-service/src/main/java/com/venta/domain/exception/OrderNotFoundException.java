package com.venta.domain.exception;

/**
 * Se lanza cuando una orden referenciada por un paso de la SAGA o por un comando
 * no existe.
 *
 * <p>Usar una excepción de dominio específica (en lugar de una
 * {@link RuntimeException} genérica) permite a llamadores, manejadores de error y
 * tests distinguir un agregado realmente inexistente de un fallo arbitrario de
 * infraestructura.
 */
public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(String orderId) {
        super("Order not found: " + orderId);
    }
}
