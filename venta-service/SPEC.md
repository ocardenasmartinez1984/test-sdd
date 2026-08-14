# Venta Service - Spec Driven Development

## Descripción
Microservicio orquestador de la SAGA. Coordina el flujo completo de una venta: creación de orden → reserva de stock → solicitud de despacho. Maneja compensaciones ante fallos.

## Responsabilidades
- Crear y gestionar órdenes de venta
- Orquestar la SAGA (secuencia de pasos distribuidos)
- Manejar compensaciones cuando algún paso falla
- Exponer API REST para consultas de órdenes

## Flujo SAGA (Orquestación)

```
[Cliente] → POST /api/ventas → [Orden PENDING]
                                      │
                                      ▼
                          Produce: stock-reserve-topic
                                      │
                                      ▼
                          Consume: stock-reserve-response-topic
                                      │
                              ┌───────┴───────┐
                              │               │
                         success=true    success=false
                              │               │
                              ▼               ▼
                    [STOCK_RESERVED]    [STOCK_FAILED]
                              │               (fin)
                              ▼
                  Produce: despacho-request-topic
                              │
                              ▼
                  Consume: despacho-response-topic
                              │
                      ┌───────┴───────┐
                      │               │
                 success=true    success=false
                      │               │
                      ▼               ▼
                [COMPLETED]    [DISPATCH_FAILED]
                                      │
                                      ▼
                          Produce: stock-compensate-topic
                                  (rollback stock)
```

## API REST

### POST /api/ventas
**Descripción:** Crear una nueva orden e iniciar la SAGA
**Request Body:**
```json
{
  "customerId": "string",
  "productId": "string",
  "quantity": 2,
  "totalAmount": 59.98
}
```
**Response:** `200 OK` - Order con status PENDING

### GET /api/ventas/{id}
**Descripción:** Obtener orden por ID
**Response:** `200 OK` - Order | `404 Not Found`

### GET /api/ventas
**Descripción:** Listar todas las órdenes
**Response:** `200 OK` - Array de Order

### GET /api/ventas/customer/{customerId}
**Descripción:** Listar órdenes por cliente
**Response:** `200 OK` - Array de Order

### GET /api/ventas/status/{status}
**Descripción:** Listar órdenes por estado
**Response:** `200 OK` - Array de Order

## Eventos Kafka

### Produce: `stock-reserve-topic`
**Cuándo:** Al crear una nueva orden
**Evento:** StockReserveEvent

### Produce: `stock-compensate-topic`
**Cuándo:** Cuando el despacho falla (compensación)
**Evento:** StockReserveEvent

### Produce: `despacho-request-topic`
**Cuándo:** Cuando la reserva de stock es exitosa
**Evento:** DespachoRequestEvent

### Consume: `stock-reserve-response-topic`
**Evento:** StockReserveResponseEvent
**Comportamiento:**
- success=true → actualizar orden a STOCK_RESERVED, solicitar despacho
- success=false → actualizar orden a STOCK_FAILED

### Consume: `despacho-response-topic`
**Evento:** DespachoResponseEvent
**Comportamiento:**
- success=true → actualizar orden a COMPLETED
- success=false → actualizar orden a DISPATCH_FAILED, enviar compensación de stock

## Modelo de Datos (MongoDB)

### Order
| Campo | Tipo | Descripción |
|-------|------|-------------|
| id | String | ID auto-generado |
| customerId | String | ID del cliente |
| productId | String | ID del producto |
| quantity | int | Cantidad solicitada |
| totalAmount | double | Monto total |
| status | OrderStatus | Estado actual de la orden |
| failureReason | String | Razón del fallo (si aplica) |
| createdAt | LocalDateTime | Fecha de creación |
| updatedAt | LocalDateTime | Última actualización |

### OrderStatus (enum)
- `PENDING` - Orden creada, esperando reserva de stock
- `STOCK_RESERVED` - Stock reservado, esperando despacho
- `STOCK_FAILED` - Falló la reserva de stock
- `DISPATCHED` - Despacho en proceso
- `DISPATCH_FAILED` - Falló el despacho
- `COMPLETED` - SAGA completada exitosamente
- `CANCELLED` - Orden cancelada

## Reglas de Negocio
1. Una orden inicia en estado PENDING
2. Si la reserva de stock falla, la orden queda en STOCK_FAILED (estado terminal)
3. Si el despacho falla, se compensa el stock y la orden queda en DISPATCH_FAILED
4. Solo las órdenes con stock reservado pueden solicitar despacho
5. La compensación de stock se dispara automáticamente ante fallo de despacho

## Configuración
- **Puerto:** 8082
- **Base de datos:** MongoDB - `venta_db`
- **Consumer group:** `venta-group`
