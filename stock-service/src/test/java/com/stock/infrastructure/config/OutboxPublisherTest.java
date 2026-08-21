package com.stock.infrastructure.config;

import com.stock.domain.model.OutboxEvent;
import com.stock.domain.repository.OutboxRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxPublisher Unit Tests")
class OutboxPublisherTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private OutboxPublisher outboxPublisher;

    private OutboxEvent pendingEvent;

    @BeforeEach
    void setUp() {
        pendingEvent = OutboxEvent.builder()
                .id("event-1")
                .aggregateId("product-1")
                .eventType("STOCK_RESERVED")
                .topic("saga.stock.reserve-reply")
                .payload("{\"orderId\":\"order-1\",\"productId\":\"product-1\",\"success\":true}")
                .status(OutboxEvent.STATUS_PENDING)
                .retryCount(0)
                .createdAt(LocalDateTime.now().minusSeconds(5))
                .build();
    }

    @Nested
    @DisplayName("PublishPendingEvents Tests")
    class PublishPendingEventsTests {

        @Test
        @DisplayName("Should publish pending events and mark as SENT")
        void shouldPublishPendingEventsAndMarkAsSent() {
            when(outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxEvent.STATUS_PENDING))
                    .thenReturn(Flux.just(pendingEvent));
            when(outboxRepository.save(any(OutboxEvent.class))).thenReturn(Mono.just(pendingEvent));

            outboxPublisher.publishPendingEvents();

            verify(kafkaTemplate).send("saga.stock.reserve-reply", "product-1", pendingEvent.getPayload());

            ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxRepository).save(captor.capture());
            OutboxEvent savedEvent = captor.getValue();
            assertThat(savedEvent.getStatus()).isEqualTo(OutboxEvent.STATUS_SENT);
            assertThat(savedEvent.getProcessedAt()).isNotNull();
        }

        @Test
        @DisplayName("Should not reprocess SENT events")
        void shouldNotReprocessSentEvents() {
            when(outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxEvent.STATUS_PENDING))
                    .thenReturn(Flux.empty());

            outboxPublisher.publishPendingEvents();

            verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
            verify(outboxRepository, never()).save(any(OutboxEvent.class));
        }

        @Test
        @DisplayName("Should process multiple pending events in order")
        void shouldProcessMultiplePendingEvents() {
            OutboxEvent event2 = OutboxEvent.builder()
                    .id("event-2")
                    .aggregateId("product-2")
                    .eventType("STOCK_RELEASED")
                    .topic("saga.stock.compensate-reply")
                    .payload("{\"orderId\":\"order-2\"}")
                    .status(OutboxEvent.STATUS_PENDING)
                    .retryCount(0)
                    .createdAt(LocalDateTime.now().minusSeconds(3))
                    .build();

            when(outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxEvent.STATUS_PENDING))
                    .thenReturn(Flux.just(pendingEvent, event2));
            when(outboxRepository.save(any(OutboxEvent.class))).thenReturn(Mono.just(pendingEvent));

            outboxPublisher.publishPendingEvents();

            verify(kafkaTemplate).send("saga.stock.reserve-reply", "product-1", pendingEvent.getPayload());
            verify(kafkaTemplate).send("saga.stock.compensate-reply", "product-2", event2.getPayload());
        }
    }

    @Nested
    @DisplayName("Retry Logic Tests")
    class RetryLogicTests {

        @Test
        @DisplayName("Should increment retry count on Kafka failure")
        void shouldIncrementRetryCountOnKafkaFailure() {
            when(outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxEvent.STATUS_PENDING))
                    .thenReturn(Flux.just(pendingEvent));
            when(kafkaTemplate.send(anyString(), anyString(), any()))
                    .thenThrow(new RuntimeException("Kafka broker unavailable"));
            when(outboxRepository.save(any(OutboxEvent.class))).thenReturn(Mono.just(pendingEvent));

            outboxPublisher.publishPendingEvents();

            ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxRepository).save(captor.capture());
            OutboxEvent savedEvent = captor.getValue();
            assertThat(savedEvent.getRetryCount()).isEqualTo(1);
            assertThat(savedEvent.getStatus()).isEqualTo(OutboxEvent.STATUS_PENDING);
        }

        @Test
        @DisplayName("Should mark event as FAILED after max retries (5)")
        void shouldMarkAsFailedAfterMaxRetries() {
            pendingEvent.setRetryCount(4); // Next failure will be 5th retry

            when(outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxEvent.STATUS_PENDING))
                    .thenReturn(Flux.just(pendingEvent));
            when(kafkaTemplate.send(anyString(), anyString(), any()))
                    .thenThrow(new RuntimeException("Kafka permanently down"));
            when(outboxRepository.save(any(OutboxEvent.class))).thenReturn(Mono.just(pendingEvent));

            outboxPublisher.publishPendingEvents();

            ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxRepository).save(captor.capture());
            OutboxEvent savedEvent = captor.getValue();
            assertThat(savedEvent.getRetryCount()).isEqualTo(5);
            assertThat(savedEvent.getStatus()).isEqualTo(OutboxEvent.STATUS_FAILED);
        }

        @Test
        @DisplayName("Should keep event as PENDING when retry count is below max")
        void shouldKeepPendingWhenBelowMaxRetries() {
            pendingEvent.setRetryCount(2); // 3rd retry, still below max

            when(outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxEvent.STATUS_PENDING))
                    .thenReturn(Flux.just(pendingEvent));
            when(kafkaTemplate.send(anyString(), anyString(), any()))
                    .thenThrow(new RuntimeException("Kafka temporary failure"));
            when(outboxRepository.save(any(OutboxEvent.class))).thenReturn(Mono.just(pendingEvent));

            outboxPublisher.publishPendingEvents();

            ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxRepository).save(captor.capture());
            OutboxEvent savedEvent = captor.getValue();
            assertThat(savedEvent.getRetryCount()).isEqualTo(3);
            assertThat(savedEvent.getStatus()).isEqualTo(OutboxEvent.STATUS_PENDING);
        }
    }
}
