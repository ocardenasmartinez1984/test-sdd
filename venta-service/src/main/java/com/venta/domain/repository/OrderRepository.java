package com.venta.domain.repository;

import com.venta.domain.model.Order;
import com.venta.domain.model.Order.OrderStatus;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.Collection;

/**
 * Repositorio reactivo de órdenes de venta sobre MongoDB.
 *
 * <p>Extiende {@link ReactiveMongoRepository} para las operaciones CRUD básicas
 * y define consultas derivadas usadas por la capa de aplicación y por la
 * reconciliación de SAGA.
 */
@Repository
public interface OrderRepository extends ReactiveMongoRepository<Order, String> {

    /**
     * Busca las órdenes de un cliente.
     *
     * @param customerId identificador del cliente
     * @return flujo de órdenes asociadas al cliente
     */
    Flux<Order> findByCustomerId(String customerId);

    /**
     * Busca las órdenes que se encuentran en un estado dado.
     *
     * @param status estado de la orden por el que filtrar
     * @return flujo de órdenes en ese estado
     */
    Flux<Order> findByStatus(OrderStatus status);

    /**
     * Órdenes que permanecen en alguno de los estados dados (normalmente
     * intermedios) y que no se han actualizado desde {@code threshold}. La usa el
     * reconciliador de SAGA para detectar transacciones estancadas porque se
     * perdió una respuesta de un servicio aguas abajo.
     *
     * @param statuses estados (intermedios) a considerar como potencialmente estancados
     * @param threshold marca temporal límite; se devuelven órdenes no actualizadas desde antes de este instante
     * @return flujo de órdenes candidatas a re-impulsar
     */
    Flux<Order> findByStatusInAndUpdatedAtBefore(Collection<OrderStatus> statuses, LocalDateTime threshold);
}
