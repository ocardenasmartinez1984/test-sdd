package com.stock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Punto de arranque del microservicio de stock (inventario) del sistema POS.
 *
 * <p>Arranca el contexto de Spring Boot que expone la API REST de productos,
 * consume los comandos de la SAGA de ventas por Kafka (reserva, compensación y
 * confirmación de stock) y gestiona el inventario en MongoDB con caché en Redis.
 * Habilita la ejecución de tareas programadas ({@code @EnableScheduling}) que
 * necesita el {@link com.stock.infrastructure.config.OutboxPublisher} para
 * publicar de forma periódica los eventos pendientes del patrón outbox.</p>
 */
@SpringBootApplication
@EnableScheduling
public class StockServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(StockServiceApplication.class, args);
    }
}
