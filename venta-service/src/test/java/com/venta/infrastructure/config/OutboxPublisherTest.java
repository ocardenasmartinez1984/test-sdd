package com.venta.infrastructure.config;

import com.venta.domain.model.OutboxEvent;
import com.venta.domain.repository.OutboxRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
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
    @DisplayName("Publish Pending Events Tests")
    class PublishPendingEventsTests {

        @Test
        @DisplayName("Should publish pending event and mark as SENT")
        void shouldPublishPendingEventAndMarkAsSent() {
            OutboxEvent pendingEvent = OutboxEvent.builder()
                    .id("event-1")
                    .aggregateId("order-1")
                    .eventType("STOCK_RESERVE")
                    .topic("stock-reserve")
                    .payload("{\"orderId\":\"order-1\"}")
                    .status(OutboxEvent.STATUS_PENDING)
                    .retryCount(0)
                    .createdAt(LocalDateTime.now())
                    .build();

            OutboxEvent sentEvent = OutboxEvent.builder()
                    .id("event-1")
                    .aggregateId("order-1")
                    .eventType("STOCK_RESERVE")
                    .topic("stock-reserve")
                    .payload("{\"orderId\":\"order-1\"}")
                    .status(OutboxEvent.STATUS_SENT)
                    .retryCount(0)
                    .createdAt(pendingEvent.getCreatedAt())
                    .processedAt(LocalDateTime.now())
                    .build();

            when(outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxEvent.STATUS_PENDING))
                    .thenReturn(Flux.just(pendingEvent));
            when(kafkaTemplate.send("stock-reserve", "order-1", "{\"orderId\":\"order-1\"}"))
                    .thenReturn(null);
            when(outboxRepository.save(any(OutboxEvent.class))).thenReturn(Mono.just(sentEvent));

            outboxPublisher.publishPendingEvents();

            verify(kafkaTemplate).send("stock-reserve", "order-1", "{\"orderId\":\"order-1\"}");
            verify(outboxRepository).save(any(OutboxEvent.class));
        }

        @Test
        @DisplayName("Should increment retry count on kafka send failure")
        void shouldIncrementRetryCountOnFailure() {
            OutboxEvent pendingEvent = OutboxEvent.builder()
                    .id("event-1")
                    .aggregateId("order-1")
                    .eventType("STOCK_RESERVE")
                    .topic("stock-reserve")
                    .payload("{\"orderId\":\"order-1\"}")
                    .status(OutboxEvent.STATUS_PENDING)
                    .retryCount(0)
                    .createdAt(LocalDateTime.now())
                    .build();

            when(outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxEvent.STATUS_PENDING))
                    .thenReturn(Flux.just(pendingEvent));
            when(kafkaTemplate.send("stock-reserve", "order-1", "{\"orderId\":\"order-1\"}"))
                    .thenThrow(new RuntimeException("Kafka unavailable"));
            when(outboxRepository.save(any(OutboxEvent.class)))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

            outboxPublisher.publishPendingEvents();

            verify(outboxRepository).save(argThat(event -> {
                assertThat(event.getRetryCount()).isEqualTo(1);
                assertThat(event.getStatus()).isEqualTo(OutboxEvent.STATUS_PENDING);
                return true;
            }));
        }

        @Test
        @DisplayName("Should mark event as FAILED after max retries")
        void shouldMarkAsFailedAfterMaxRetries() {
            OutboxEvent pendingEvent = OutboxEvent.builder()
                    .id("event-1")
                    .aggregateId("order-1")
                    .eventType("STOCK_RESERVE")
                    .topic("stock-reserve")
                    .payload("{\"orderId\":\"order-1\"}")
                    .status(OutboxEvent.STATUS_PENDING)
                    .retryCount(4) // Will become 5 after increment, hitting the max
                    .createdAt(LocalDateTime.now())
                    .build();

            when(outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxEvent.STATUS_PENDING))
                    .thenReturn(Flux.just(pendingEvent));
            when(kafkaTemplate.send("stock-reserve", "order-1", "{\"orderId\":\"order-1\"}"))
                    .thenThrow(new RuntimeException("Kafka unavailable"));
            when(outboxRepository.save(any(OutboxEvent.class)))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

            outboxPublisher.publishPendingEvents();

            verify(outboxRepository).save(argThat(event -> {
                assertThat(event.getRetryCount()).isEqualTo(5);
                assertThat(event.getStatus()).isEqualTo(OutboxEvent.STATUS_FAILED);
                return true;
            }));
        }

        @Test
        @DisplayName("Should handle empty pending events")
        void shouldHandleEmptyPendingEvents() {
            when(outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxEvent.STATUS_PENDING))
                    .thenReturn(Flux.empty());

            outboxPublisher.publishPendingEvents();

            verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
            verify(outboxRepository, never()).save(any(OutboxEvent.class));
        }

        @Test
        @DisplayName("Should process multiple pending events")
        void shouldProcessMultiplePendingEvents() {
            OutboxEvent event1 = OutboxEvent.builder()
                    .id("event-1")
                    .aggregateId("order-1")
                    .eventType("STOCK_RESERVE")
                    .topic("stock-reserve")
                    .payload("{\"orderId\":\"order-1\"}")
                    .status(OutboxEvent.STATUS_PENDING)
                    .retryCount(0)
                    .createdAt(LocalDateTime.now().minusSeconds(2))
                    .build();

            OutboxEvent event2 = OutboxEvent.builder()
                    .id("event-2")
                    .aggregateId("order-2")
                    .eventType("DESPACHO_REQUEST")
                    .topic("despacho-request")
                    .payload("{\"orderId\":\"order-2\"}")
                    .status(OutboxEvent.STATUS_PENDING)
                    .retryCount(0)
                    .createdAt(LocalDateTime.now().minusSeconds(1))
                    .build();

            when(outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxEvent.STATUS_PENDING))
                    .thenReturn(Flux.just(event1, event2));
            when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(null);
            when(outboxRepository.save(any(OutboxEvent.class)))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

            outboxPublisher.publishPendingEvents();

            verify(kafkaTemplate).send("stock-reserve", "order-1", "{\"orderId\":\"order-1\"}");
            verify(kafkaTemplate).send("despacho-request", "order-2", "{\"orderId\":\"order-2\"}");
            verify(outboxRepository, times(2)).save(any(OutboxEvent.class));
        }
    }
}
