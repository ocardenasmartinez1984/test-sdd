package com.venta.domain.saga;

import reactor.core.publisher.Mono;

/**
 * Puerto (patrón Strategy) para manejar de forma polimórfica un paso de la SAGA
 * según el tipo de evento recibido.
 *
 * <p>Permite añadir manejadores de pasos SAGA desacoplados del orquestador,
 * seleccionando el adecuado mediante {@link #canHandle(String)}.
 */
public interface SagaStepHandler {
    /**
     * Indica si este manejador puede procesar el tipo de evento dado.
     *
     * @param eventType identificador del tipo de evento SAGA
     * @return {@code true} si este manejador atiende ese tipo de evento
     */
    boolean canHandle(String eventType);

    /**
     * Procesa el evento SAGA de forma reactiva.
     *
     * @param event el evento a manejar
     * @return {@link Mono} que completa cuando el paso ha sido procesado
     */
    Mono<Void> handle(Object event);
}
