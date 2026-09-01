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
 * Relays pending {@link OutboxEvent}s to Kafka.
 *
 * <p>Two correctness properties are enforced here that the previous
 * implementation lacked:
 *
 * <ol>
 *   <li><b>Real delivery confirmation.</b> {@code KafkaTemplate.send(...)} is
 *       asynchronous and returns a future; the broker acknowledgement (or
 *       failure) only materialises when that future completes. A surrounding
 *       {@code try/catch} therefore almost never fires, so events used to be
 *       marked {@code SENT} even when Kafka was down. We now bridge the send
 *       future into the reactive pipeline and only mark {@code SENT} after the
 *       broker actually acknowledges the record.</li>
 *   <li><b>No overlapping runs.</b> The scheduled method is reactive; with a
 *       fire-and-forget {@code subscribe()} a slow batch could overlap with the
 *       next tick and publish the same rows twice. A guard flag makes each tick
 *       skip while the previous batch is still in flight, and events are
 *       processed sequentially (concatMap) to preserve ordering.</li>
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
     * Reactive pipeline that publishes every pending event in creation order.
     * Extracted so it can be unit-tested deterministically with StepVerifier.
     */
    Mono<Void> drainPendingEvents() {
        return outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxEvent.STATUS_PENDING)
                .concatMap(this::publishOne)
                .then();
    }

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
     * Bridges the Kafka send future into a Mono that only completes once the
     * broker has acknowledged the record (or errors if delivery failed).
     */
    private Mono<Void> sendToKafka(OutboxEvent event) {
        return Mono.fromFuture(
                        kafkaTemplate.send(event.getTopic(), event.getAggregateId(), event.getPayload()))
                .then();
    }

    private void markSent(OutboxEvent event) {
        event.setStatus(OutboxEvent.STATUS_SENT);
        event.setProcessedAt(LocalDateTime.now());
        log.debug("Outbox event sent: {} to topic {}", event.getId(), event.getTopic());
    }

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
