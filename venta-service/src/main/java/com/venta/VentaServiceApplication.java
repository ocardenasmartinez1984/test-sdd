package com.venta;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Punto de arranque del venta-service, el microservicio orquestador de ventas
 * del sistema POS.
 *
 * <p>Actúa como coordinador de la SAGA de ventas: expone la API REST de ventas
 * y de carrito, publica y consume eventos Kafka hacia los servicios de stock y
 * despacho, y persiste las órdenes en MongoDB. La anotación
 * {@link org.springframework.scheduling.annotation.EnableScheduling} habilita
 * las tareas periódicas del servicio (publicación del outbox, reconciliación de
 * SAGA y expiración de carritos abandonados).
 */
@SpringBootApplication
@EnableScheduling
public class VentaServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(VentaServiceApplication.class, args);
    }
}
