package com.despacho.infrastructure.config;

import com.despacho.domain.model.OutboxEvent;
import com.despacho.domain.repository.OutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

    @Nested
    @DisplayName("publishPendingEvents Tests")
    class PublishPendingEventsTests {

        @Test
        @DisplayName("Should do nothing when no pending events exist")
        void shouldDoNothingWhenNoPendingEvents() {
            when(outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxEvent.STATUS_PENDING))
                    .thenReturn(Flux.empty());

            outboxPublisher.publishPendingEvents();

            verify(outboxRepository).findByStatusOrderByCreatedAtAsc(OutboxEvent.STATUS_PENDING);
            verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
            verify(outboxRepository, never()).save(any(OutboxEvent.class));
        }

        @Test
        @DisplayName("Should publish one event and mark as SENT")
        void shouldPublishOneEventAndMarkAsSent() {
            OutboxEvent pendingEvent = OutboxEvent.builder()
                    .id("event-1")
                    .aggregateId("order-1")
                    .eventType("DESPACHO_CREATED")
                    .topic("despacho-response")
                    .payload("{\"orderId\":\"order-1\",\"success\":true}")
                    .status(OutboxEvent.STATUS_PENDING)
                    .retryCount(0)
                    .createdAt(LocalDateTime.now())
                    .build();

            when(outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxEvent.STATUS_PENDING))
                    .thenReturn(Flux.just(pendingEvent));
            when(outboxRepository.save(any(OutboxEvent.class)))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

            outboxPublisher.publishPendingEvents();

            verify(kafkaTemplate).send("despacho-response", "order-1",
                    "{\"orderId\":\"order-1\",\"success\":true}");

            ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxRepository).save(captor.capture());

            OutboxEvent savedEvent = captor.getValue();
            assertThat(savedEvent.getStatus()).isEqualTo(OutboxEvent.STATUS_SENT);
            assertThat(savedEvent.getProcessedAt()).isNotNull();
        }

        @Test
        @DisplayName("Should increment retryCount when Kafka throws exception")
        void shouldIncrementRetryCountWhenKafkaThrows() {
            OutboxEvent pendingEvent = OutboxEvent.builder()
                    .id("event-1")
                    .aggregateId("order-1")
                    .eventType("DESPACHO_CREATED")
                    .topic("despacho-response")
                    .payload("{\"orderId\":\"order-1\"}")
                    .status(OutboxEvent.STATUS_PENDING)
                    .retryCount(0)
                    .createdAt(LocalDateTime.now())
                    .build();

            when(outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxEvent.STATUS_PENDING))
                    .thenReturn(Flux.just(pendingEvent));
            when(kafkaTemplate.send(anyString(), anyString(), any()))
                    .thenThrow(new RuntimeException("Kafka broker unavailable"));
            when(outboxRepository.save(any(OutboxEvent.class)))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

            outboxPublisher.publishPendingEvents();

            ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxRepository).save(captor.capture());

            OutboxEvent savedEvent = captor.getValue();
            assertThat(savedEvent.getRetryCount()).isEqualTo(1);
            assertThat(savedEvent.getStatus()).isEqualTo(OutboxEvent.STATUS_PENDING);
        }

        @Test
        @DisplayName("Should mark as FAILED when retryCount reaches 5")
        void shouldMarkAsFailedWhenRetryCountReachesFive() {
            OutboxEvent pendingEvent = OutboxEvent.builder()
                    .id("event-1")
                    .aggregateId("order-1")
                    .eventType("DESPACHO_CREATED")
                    .topic("despacho-response")
                    .payload("{\"orderId\":\"order-1\"}")
                    .status(OutboxEvent.STATUS_PENDING)
                    .retryCount(4) // Next retry will be 5
                    .createdAt(LocalDateTime.now())
                    .build();

            when(outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxEvent.STATUS_PENDING))
                    .thenReturn(Flux.just(pendingEvent));
            when(kafkaTemplate.send(anyString(), anyString(), any()))
                    .thenThrow(new RuntimeException("Kafka broker unavailable"));
            when(outboxRepository.save(any(OutboxEvent.class)))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

            outboxPublisher.publishPendingEvents();

            ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxRepository).save(captor.capture());

            OutboxEvent savedEvent = captor.getValue();
            assertThat(savedEvent.getRetryCount()).isEqualTo(5);
            assertThat(savedEvent.getStatus()).isEqualTo(OutboxEvent.STATUS_FAILED);
        }
    }
}
