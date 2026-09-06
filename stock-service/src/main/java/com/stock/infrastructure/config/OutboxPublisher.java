package com.stock.infrastructure.config;

import com.stock.domain.model.OutboxEvent;
import com.stock.domain.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Publicador programado del patrón Transactional Outbox (capa de infraestructura).
 *
 * <p>Ejecuta de forma periódica ({@code @Scheduled}) el drenaje de los eventos
 * pendientes almacenados en la colección outbox y los envía a Kafka mediante el
 * {@link KafkaTemplate}, actualizando su estado tras cada intento. Garantiza la
 * publicación fiable de eventos de dominio desacoplada de la transacción de
 * negocio.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Publica en Kafka los eventos outbox pendientes, ejecutándose cada segundo.
     *
     * <p>Recupera los eventos en estado {@code PENDING} ordenados por fecha de
     * creación y, por cada uno, intenta enviarlo a su topic. Si el envío tiene
     * éxito, marca el evento como {@code SENT} y registra la fecha de proceso; si
     * falla, incrementa el contador de reintentos y, tras 5 intentos, lo marca
     * como {@code FAILED}. Persiste el nuevo estado de cada evento.</p>
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
