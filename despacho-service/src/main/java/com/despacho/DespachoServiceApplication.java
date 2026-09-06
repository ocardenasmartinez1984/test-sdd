package com.despacho;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Punto de entrada del microservicio de despacho (dispatch) del sistema POS.
 *
 * <p>Arranca la aplicación Spring Boot que gestiona la creación y el seguimiento
 * de despachos dentro del flujo SAGA de ventas: reacciona a los comandos de
 * despacho publicados por el servicio de ventas, crea los envíos en MongoDB
 * (de forma reactiva) y responde vía Kafka con el resultado de la operación.</p>
 *
 * <p>Habilita la programación de tareas con {@link org.springframework.scheduling.annotation.EnableScheduling}
 * para permitir la publicación periódica de eventos pendientes del patrón Outbox.</p>
 */
@SpringBootApplication
@EnableScheduling
public class DespachoServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DespachoServiceApplication.class, args);
    }
}
