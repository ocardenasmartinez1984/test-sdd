package com.venta.infrastructure.config;

import com.venta.domain.model.OutboxEvent;
import com.venta.domain.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Relaya a Kafka los {@link OutboxEvent} pendientes.
 *
 * <p>Aquí se garantizan dos propiedades de corrección que a la implementación
 * anterior le faltaban:
 *
 * <ol>
 *   <li><b>Confirmación real de entrega.</b> {@code KafkaTemplate.send(...)} es
 *       asíncrono y devuelve un future; el acuse del broker (o el fallo) solo se
 *       materializa cuando ese future completa. Un {@code try/catch} envolvente
 *       casi nunca se dispara, así que antes los eventos se marcaban {@code SENT}
 *       incluso con Kafka caído. Ahora puenteamos el future del envío hacia la
 *       tubería reactiva y solo marcamos {@code SENT} después de que el broker
 *       realmente acuse el registro.</li>
 *   <li><b>Sin ejecuciones solapadas.</b> El método programado es reactivo; con un
 *       {@code subscribe()} de tipo fire-and-forget un lote lento podría solaparse
 *       con el siguiente tick y publicar las mismas filas dos veces. Un flag de
 *       guarda hace que cada tick se omita mientras el lote anterior sigue en
 *       vuelo, y los eventos se procesan secuencialmente (concatMap) para
 *       preservar el orden.</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    static final int MAX_RETRIES = 5;

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /** Prevents a new tick from starting while the previous batch is still running. */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * Disparador programado que publica el lote de eventos pendientes.
     *
     * <p>Usa el flag {@code running} para omitir el tick si el lote anterior sigue
     * en curso; lanza {@link #drainPendingEvents()} de forma asíncrona y registra
     * los errores del lote.
     */
    @Scheduled(fixedDelay = 1000)
    public void publishPendingEvents() {
        if (!running.compareAndSet(false, true)) {
            log.trace("Outbox publish tick skipped: previous batch still running");
            return;
        }

        drainPendingEvents()
                .doFinally(signal -> running.set(false))
                .subscribe(
                        null,
                        error -> log.error("Outbox publish batch failed", error));
    }

    /**
     * Tubería reactiva que publica todos los eventos pendientes en orden de
     * creación. Extraída para poder testearse de forma determinista con
     * StepVerifier.
     *
     * @return {@link Mono} que completa cuando se ha procesado todo el lote
     */
    Mono<Void> drainPendingEvents() {
        return outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxEvent.STATUS_PENDING)
                .concatMap(this::publishOne)
                .then();
    }

    /**
     * Publica un único evento y persiste su nuevo estado.
     *
     * <p>Envía el evento a Kafka; si el broker lo acusa lo marca {@code SENT}, y si
     * falla incrementa el contador de reintentos (marcándolo {@code FAILED} al
     * agotarlos). En ambos casos guarda el evento actualizado en MongoDB.
     *
     * @param event evento del outbox a publicar
     * @return {@link Mono} con el evento tras persistir su estado
     */
    private Mono<OutboxEvent> publishOne(OutboxEvent event) {
        return sendToKafka(event)
                .then(Mono.fromRunnable(() -> markSent(event)))
                .thenReturn(event)
                .onErrorResume(error -> {
                    markFailure(event, error);
                    return Mono.just(event);
                })
                .flatMap(outboxRepository::save);
    }

    /**
     * Puentea el future del envío de Kafka en un Mono que solo completa cuando el
     * broker ha acusado el registro (o falla si la entrega no prosperó).
     *
     * @param event evento del outbox a enviar
     * @return {@link Mono} que completa al confirmar la entrega o emite error si falla
     */
    private Mono<Void> sendToKafka(OutboxEvent event) {
        return Mono.fromFuture(
                        kafkaTemplate.send(event.getTopic(), event.getAggregateId(), event.getPayload()))
                .then();
    }

    /**
     * Marca el evento como enviado, fijando el estado {@code SENT} y la marca de
     * tiempo de procesamiento.
     *
     * @param event evento entregado con éxito
     */
    private void markSent(OutboxEvent event) {
        event.setStatus(OutboxEvent.STATUS_SENT);
        event.setProcessedAt(LocalDateTime.now());
        log.debug("Outbox event sent: {} to topic {}", event.getId(), event.getTopic());
    }

    /**
     * Registra un fallo de envío incrementando el contador de reintentos.
     *
     * <p>Si se alcanza {@link #MAX_RETRIES}, marca el evento como {@code FAILED};
     * en caso contrario solo deja constancia del reintento.
     *
     * @param event evento cuyo envío falló
     * @param error causa del fallo de entrega
     */
    private void markFailure(OutboxEvent event, Throwable error) {
        event.setRetryCount(event.getRetryCount() + 1);
        if (event.getRetryCount() >= MAX_RETRIES) {
            event.setStatus(OutboxEvent.STATUS_FAILED);
            log.error("Outbox event FAILED after {} retries: {} ({})",
                    event.getRetryCount(), event.getId(), error.getMessage());
        } else {
            log.warn("Outbox event retry {}: {} ({})",
                    event.getRetryCount(), event.getId(), error.getMessage());
        }
    }
}
