package com.despacho.infrastructure.kafka;

import com.despacho.domain.event.DespachoResponseEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class DespachoProducerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private DespachoProducer despachoProducer;

    @Nested
    @DisplayName("Send Response Tests")
    class SendResponseTests {

        @Test
        @DisplayName("Should send despacho response with success to correct topic")
        void shouldSendSuccessResponse() {
            DespachoResponseEvent event = DespachoResponseEvent.builder()
                    .orderId("order-1")
                    .success(true)
                    .trackingNumber("TRK-12345678")
                    .build();

            despachoProducer.sendDespachoResponse(event);

            verify(kafkaTemplate).send("saga.despacho.create-reply", "order-1", event);
        }

        @Test
        @DisplayName("Should send despacho response with failure to correct topic")
        void shouldSendFailureResponse() {
            DespachoResponseEvent event = DespachoResponseEvent.builder()
                    .orderId("order-1")
                    .success(false)
                    .reason("Cannot dispatch - address invalid")
                    .build();

            despachoProducer.sendDespachoResponse(event);

            verify(kafkaTemplate).send("saga.despacho.create-reply", "order-1", event);
        }

        @Test
        @DisplayName("Should propagate exception when KafkaTemplate throws")
        void shouldPropagateExceptionWhenKafkaTemplateThrows() {
            DespachoResponseEvent event = DespachoResponseEvent.builder()
                    .orderId("order-1")
                    .success(true)
                    .trackingNumber("TRK-12345678")
                    .build();

            when(kafkaTemplate.send("saga.despacho.create-reply", "order-1", event))
                    .thenThrow(new RuntimeException("Kafka broker unavailable"));

            org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () ->
                    despachoProducer.sendDespachoResponse(event));

            verify(kafkaTemplate).send("saga.despacho.create-reply", "order-1", event);
        }
    }

    @Nested
    @DisplayName("CircuitBreaker Fallback Tests")
    class CircuitBreakerFallbackTests {

        @Test
        @DisplayName("sendDespachoResponseFallback should handle gracefully without throwing")
        void sendDespachoResponseFallbackShouldHandleGracefully() throws Exception {
            DespachoResponseEvent event = DespachoResponseEvent.builder()
                    .orderId("order-1")
                    .success(true)
                    .trackingNumber("TRK-12345678")
                    .build();

            Method fallbackMethod = DespachoProducer.class.getDeclaredMethod(
                    "sendDespachoResponseFallback", DespachoResponseEvent.class, Throwable.class);
            fallbackMethod.setAccessible(true);

            assertThatNoException().isThrownBy(() ->
                    fallbackMethod.invoke(despachoProducer, event,
                            new RuntimeException("Kafka broker unavailable")));

            verifyNoInteractions(kafkaTemplate);
        }
    }
}
