package com.venta.infrastructure.config;

import com.venta.domain.model.OutboxEvent;
import com.venta.domain.repository.OutboxRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private OutboxPublisher outboxPublisher;

    private static OutboxEvent pending(String id, String topic, String aggregateId, int retryCount) {
        return OutboxEvent.builder()
                .id(id)
                .aggregateId(aggregateId)
                .eventType("STOCK_RESERVE")
                .topic(topic)
                .payload("{\"orderId\":\"" + aggregateId + "\"}")
                .status(OutboxEvent.STATUS_PENDING)
                .retryCount(retryCount)
                .createdAt(LocalDateTime.now())
                .build();
    }

    /** A completed send future, as KafkaTemplate returns on a successful ack. */
    @SuppressWarnings("unchecked")
    private CompletableFuture<SendResult<String, Object>> ackedFuture() {
        return CompletableFuture.completedFuture((SendResult<String, Object>) mock(SendResult.class));
    }

    private CompletableFuture<SendResult<String, Object>> failedFuture(String message) {
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException(message));
        return future;
    }

    @Nested
    @DisplayName("Publish Pending Events Tests")
    class PublishPendingEventsTests {

        @Test
        @DisplayName("Should publish pending event and mark as SENT once Kafka acks")
        void shouldPublishPendingEventAndMarkAsSent() {
            OutboxEvent event = pending("event-1", "stock-reserve", "order-1", 0);

            when(outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxEvent.STATUS_PENDING))
                    .thenReturn(Flux.just(event));
            when(kafkaTemplate.send("stock-reserve", "order-1", event.getPayload()))
                    .thenReturn(ackedFuture());
            when(outboxRepository.save(any(OutboxEvent.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            StepVerifier.create(outboxPublisher.drainPendingEvents()).verifyComplete();

            verify(kafkaTemplate).send("stock-reserve", "order-1", event.getPayload());
            verify(outboxRepository).save(argThat(saved -> {
                assertThat(saved.getStatus()).isEqualTo(OutboxEvent.STATUS_SENT);
                assertThat(saved.getProcessedAt()).isNotNull();
                return true;
            }));
        }

        @Test
        @DisplayName("Should NOT mark as SENT when Kafka delivery future fails")
        void shouldIncrementRetryWhenKafkaFutureFails() {
            OutboxEvent event = pending("event-1", "stock-reserve", "order-1", 0);

            when(outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxEvent.STATUS_PENDING))
                    .thenReturn(Flux.just(event));
            when(kafkaTemplate.send("stock-reserve", "order-1", event.getPayload()))
                    .thenReturn(failedFuture("Kafka unavailable"));
            when(outboxRepository.save(any(OutboxEvent.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            StepVerifier.create(outboxPublisher.drainPendingEvents()).verifyComplete();

            verify(outboxRepository).save(argThat(saved -> {
                assertThat(saved.getRetryCount()).isEqualTo(1);
                assertThat(saved.getStatus()).isEqualTo(OutboxEvent.STATUS_PENDING);
                return true;
            }));
        }

        @Test
        @DisplayName("Should mark event as FAILED after max retries")
        void shouldMarkAsFailedAfterMaxRetries() {
            OutboxEvent event = pending("event-1", "stock-reserve", "order-1", OutboxPublisher.MAX_RETRIES - 1);

            when(outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxEvent.STATUS_PENDING))
                    .thenReturn(Flux.just(event));
            when(kafkaTemplate.send("stock-reserve", "order-1", event.getPayload()))
                    .thenReturn(failedFuture("Kafka unavailable"));
            when(outboxRepository.save(any(OutboxEvent.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            StepVerifier.create(outboxPublisher.drainPendingEvents()).verifyComplete();

            verify(outboxRepository).save(argThat(saved -> {
                assertThat(saved.getRetryCount()).isEqualTo(OutboxPublisher.MAX_RETRIES);
                assertThat(saved.getStatus()).isEqualTo(OutboxEvent.STATUS_FAILED);
                return true;
            }));
        }

        @Test
        @DisplayName("Should handle empty pending events")
        void shouldHandleEmptyPendingEvents() {
            when(outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxEvent.STATUS_PENDING))
                    .thenReturn(Flux.empty());

            StepVerifier.create(outboxPublisher.drainPendingEvents()).verifyComplete();

            verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
            verify(outboxRepository, never()).save(any(OutboxEvent.class));
        }

        @Test
        @DisplayName("Should process multiple pending events in order")
        void shouldProcessMultiplePendingEvents() {
            OutboxEvent event1 = pending("event-1", "stock-reserve", "order-1", 0);
            OutboxEvent event2 = pending("event-2", "despacho-request", "order-2", 0);

            when(outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxEvent.STATUS_PENDING))
                    .thenReturn(Flux.just(event1, event2));
            when(kafkaTemplate.send("stock-reserve", "order-1", event1.getPayload()))
                    .thenReturn(ackedFuture());
            when(kafkaTemplate.send("despacho-request", "order-2", event2.getPayload()))
                    .thenReturn(ackedFuture());
            when(outboxRepository.save(any(OutboxEvent.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            StepVerifier.create(outboxPublisher.drainPendingEvents()).verifyComplete();

            verify(kafkaTemplate).send("stock-reserve", "order-1", event1.getPayload());
            verify(kafkaTemplate).send("despacho-request", "order-2", event2.getPayload());
            verify(outboxRepository, times(2)).save(any(OutboxEvent.class));
        }
    }
}
