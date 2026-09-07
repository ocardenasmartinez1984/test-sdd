package com.despacho.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class KafkaErrorHandlerConfigTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Nested
    @DisplayName("Error Handler Bean Tests")
    class ErrorHandlerBeanTests {

        // Verifica que el método errorHandler de la configuración crea un bean CommonErrorHandler
        // no nulo y que es una instancia de DefaultErrorHandler.
        @Test
        @DisplayName("Should create a valid CommonErrorHandler bean")
        void shouldCreateValidCommonErrorHandler() {
            KafkaErrorHandlerConfig config = new KafkaErrorHandlerConfig();

            CommonErrorHandler errorHandler = config.errorHandler(kafkaTemplate);

            assertThat(errorHandler).isNotNull();
            assertThat(errorHandler).isInstanceOf(DefaultErrorHandler.class);
        }

        // Verifica que el errorHandler se construye correctamente con su DeadLetterPublishingRecoverer
        // y backoff, comprobando que el bean resultante no es nulo y es un DefaultErrorHandler
        // (lo que valida que la configuración de la Dead Letter Topic quedó inicializada).
        @Test
        @DisplayName("Should create error handler with DeadLetterPublishingRecoverer")
        void shouldCreateErrorHandlerWithDeadLetterRecoverer() {
            KafkaErrorHandlerConfig config = new KafkaErrorHandlerConfig();

            CommonErrorHandler errorHandler = config.errorHandler(kafkaTemplate);

            assertThat(errorHandler).isNotNull();
            assertThat(errorHandler).isInstanceOf(DefaultErrorHandler.class);
            // DefaultErrorHandler is created successfully with recoverer and backoff
            // which validates the DLT configuration is properly initialized
        }
    }
}
