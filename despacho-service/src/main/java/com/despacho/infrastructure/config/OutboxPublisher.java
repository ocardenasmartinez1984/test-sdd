package com.despacho.infrastructure.config;

import com.despacho.domain.model.OutboxEvent;
import com.despacho.domain.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Publicador periódico de eventos del patrón Transactional Outbox.
 *
 * <p>Pertenece a la capa de infraestructura y garantiza la entrega confiable de
 * eventos a Kafka: lee los {@link OutboxEvent} pendientes desde MongoDB
 * (a través de {@link OutboxRepository}) y los emite mediante
 * {@link KafkaTemplate}, marcando su estado según el resultado.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Tarea programada que procesa los eventos pendientes del outbox.
     *
     * <p>Se ejecuta cada segundo. Recupera los eventos en estado
     * {@code PENDING} ordenados por antigüedad y, para cada uno, intenta
     * publicarlo en su tópico usando el {@code aggregateId} como clave:</p>
     * <ul>
     *   <li>Si el envío tiene éxito, marca el evento como {@code SENT} y fija la
     *       fecha de procesamiento.</li>
     *   <li>Si falla, incrementa el contador de reintentos; tras 5 intentos lo
     *       marca como {@code FAILED}, en caso contrario lo deja para reintentar.</li>
     * </ul>
     * <p>Finalmente persiste el estado actualizado de cada evento en MongoDB.</p>
     */
    @Scheduled(fixedDelay = 1000)
    public void publishPendingEvents() {
        outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxEvent.STATUS_PENDING)
                .flatMap(event -> {
                    try {
                        kafkaTemplate.send(event.getTopic(), event.getAggregateId(), event.getPayload());
                        event.setStatus(OutboxEvent.STATUS_SENT);
                        event.setProcessedAt(LocalDateTime.now());
                        log.debug("Outbox event sent: {} to topic {}", event.getId(), event.getTopic());
                    } catch (Exception e) {
                        event.setRetryCount(event.getRetryCount() + 1);
                        if (event.getRetryCount() >= 5) {
                            event.setStatus(OutboxEvent.STATUS_FAILED);
                            log.error("Outbox event FAILED after {} retries: {}", event.getRetryCount(), event.getId());
                        } else {
                            log.warn("Outbox event retry {}: {}", event.getRetryCount(), event.getId());
                        }
                    }
                    return outboxRepository.save(event);
                })
                .subscribe();
    }
}
