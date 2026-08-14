package com.despacho.infrastructure.kafka;

import com.despacho.application.DespachoApplicationService;
import com.despacho.domain.event.DespachoRequestEvent;
import com.despacho.domain.event.DespachoResponseEvent;
import com.despacho.domain.model.Dispatch;
import com.despacho.domain.model.Dispatch.DispatchStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DespachoConsumerTest {

    @Mock
    private DespachoApplicationService despachoApplicationService;

    @Mock
    private DespachoProducer despachoProducer;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private DespachoConsumer despachoConsumer;

    @Test
    @DisplayName("Should process despacho request and send success response")
    void shouldProcessDespachoRequestSuccessfully() {
        Map<String, Object> message = new HashMap<>();
        message.put("orderId", "order-1");
        message.put("productId", "product-1");
        message.put("quantity", 5);
        message.put("customerId", "customer-1");

        DespachoRequestEvent request = DespachoRequestEvent.builder()
                .orderId("order-1")
                .productId("product-1")
                .quantity(5)
                .customerId("customer-1")
                .build();

        Dispatch dispatch = Dispatch.builder()
                .id("dispatch-1")
                .orderId("order-1")
                .trackingNumber("TRK-12345678")
                .status(DispatchStatus.PREPARANDO)
                .createdAt(LocalDateTime.now())
                .build();

        when(objectMapper.convertValue(message, DespachoRequestEvent.class)).thenReturn(request);
        when(despachoApplicationService.crearDespacho(request)).thenReturn(Mono.just(dispatch));
        doNothing().when(despachoProducer).sendDespachoResponse(any());

        despachoConsumer.consumeDespachoRequest(message);

        verify(objectMapper).convertValue(message, DespachoRequestEvent.class);
        verify(despachoApplicationService).crearDespacho(request);
    }

    @Test
    @DisplayName("Should send failure response on conversion exception")
    void shouldSendFailureResponseOnConversionException() {
        Map<String, Object> message = new HashMap<>();
        message.put("orderId", "order-1");

        when(objectMapper.convertValue(message, DespachoRequestEvent.class))
                .thenThrow(new IllegalArgumentException("Invalid message format"));
        doNothing().when(despachoProducer).sendDespachoResponse(any());

        despachoConsumer.consumeDespachoRequest(message);

        verify(despachoProducer).sendDespachoResponse(any(DespachoResponseEvent.class));
        verify(despachoApplicationService, never()).crearDespacho(any());
    }

    @Test
    @DisplayName("Should handle null orderId in error response")
    void shouldHandleNullOrderIdInErrorResponse() {
        Map<String, Object> message = new HashMap<>();
        // No orderId in the message

        when(objectMapper.convertValue(message, DespachoRequestEvent.class))
                .thenThrow(new IllegalArgumentException("Missing fields"));
        doNothing().when(despachoProducer).sendDespachoResponse(any());

        despachoConsumer.consumeDespachoRequest(message);

        verify(despachoProducer).sendDespachoResponse(any(DespachoResponseEvent.class));
    }
}
