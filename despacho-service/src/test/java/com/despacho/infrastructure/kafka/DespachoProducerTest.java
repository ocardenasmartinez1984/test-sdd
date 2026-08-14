package com.despacho.infrastructure.kafka;

import com.despacho.domain.event.DespachoResponseEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DespachoProducerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private DespachoProducer despachoProducer;

    @Test
    @DisplayName("Should send despacho response with success to correct topic")
    void shouldSendSuccessResponse() {
        DespachoResponseEvent event = DespachoResponseEvent.builder()
                .orderId("order-1")
                .success(true)
                .trackingNumber("TRK-12345678")
                .build();

        despachoProducer.sendDespachoResponse(event);

        verify(kafkaTemplate).send("despacho-response", "order-1", event);
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

        verify(kafkaTemplate).send("despacho-response", "order-1", event);
    }
}
