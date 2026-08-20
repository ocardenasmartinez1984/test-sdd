package com.venta.infrastructure.config;

import com.venta.domain.model.OutboxEvent;
import com.venta.domain.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

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
