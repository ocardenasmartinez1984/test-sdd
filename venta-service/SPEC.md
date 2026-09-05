# Venta Service - Spec Driven Development

## Descripción
Microservicio orquestador de la SAGA. Coordina el flujo completo de una venta: creación de orden → reserva de stock → solicitud de despacho. Maneja compensaciones ante fallos.

## Responsabilidades
- Crear y gestionar órdenes de venta
- Gestionar el carrito de compras (reservar/actualizar/liberar stock por sesión)
- Orquestar la SAGA (secuencia de pasos distribuidos)
- Manejar compensaciones cuando algún paso falla
- Reconciliar órdenes atascadas y expirar carritos abandonados
- Exponer API REST para consultas de órdenes

## Flujo SAGA (Orquestación)

```
[Cliente] → POST /api/ventas → [Orden PENDING]
                                      │
                                      ▼
                          Produce: saga.stock.reserve-command
                                      │
                                      ▼
                          Consume: saga.stock.reserve-reply
                                      │
                              ┌───────┴───────┐
                              │               │
                         success=true    success=false
                              │               │
                              ▼               ▼
                    [STOCK_RESERVED]    [STOCK_FAILED]
                              │               (fin)
                              ▼
                  Produce: saga.despacho.create-command
                              │
                              ▼
                  Consume: saga.despacho.create-reply
                              │
                      ┌───────┴───────┐
                      │               │
                 success=true    success=false
                      │               │
                      ▼               ▼
                [COMPLETED]    [DISPATCH_FAILED]
                                      │
                                      ▼
                          Produce: saga.stock.compensate-command
                                  (rollback stock)
```

## API REST

Prefijos base: `/api/v1/ventas` (órdenes) y `/api/v1/cart` (carrito)

### POST /api/v1/ventas
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
**Response:** `201 Created` - Order con status PENDING

### GET /api/v1/ventas/{id}
**Descripción:** Obtener orden por ID
**Response:** `200 OK` - Order | `404 Not Found`

### GET /api/v1/ventas
**Descripción:** Listar todas las órdenes
**Response:** `200 OK` - Array de Order

### GET /api/v1/ventas/customer/{customerId}
**Descripción:** Listar órdenes por cliente
**Response:** `200 OK` - Array de Order

### GET /api/v1/ventas/status/{status}
**Descripción:** Listar órdenes por estado
**Response:** `200 OK` - Array de Order

## API REST - Carrito (`/api/v1/cart`)

El carrito reserva stock por sesión mientras el cliente compra. Cada `CartItem`
tiene un `expiresAt` (10 min); los abandonados se liberan automáticamente.

### POST /api/v1/cart
**Descripción:** Agregar al carrito (cantidad **aditiva**). Reserva stock por el nuevo total del ítem.
**Request Body:** `{ "sessionId": "s", "productId": "p", "quantity": 1, "unitPrice": 10.0 }`
**Response:** `201 Created` - CartItem

### PUT /api/v1/cart
**Descripción:** Fijar la cantidad **absoluta** del ítem (usado al incrementar/decrementar en la UI).
Re-reserva el nuevo total; el stock aplica solo el delta, por lo que **reducir la cantidad libera stock**.
Si `quantity <= 0`, elimina el ítem y libera su reserva.
**Response:** `200 OK` - CartItem | `204 No Content` (si se eliminó)

### GET /api/v1/cart/{sessionId}
**Descripción:** Listar los ítems RESERVED de una sesión
**Response:** `200 OK` - Array de CartItem

### DELETE /api/v1/cart/{sessionId}/{productId}
**Descripción:** Quitar un producto del carrito y liberar su reserva
**Response:** `204 No Content`

### DELETE /api/v1/cart/{sessionId}
**Descripción:** Vaciar el carrito: compensa (libera) todos los ítems RESERVED y los borra
**Response:** `204 No Content`

## Procesos en segundo plano

### SagaReconciler (`@Scheduled`, cada 60s)
Reenvía el comando pendiente para órdenes atascadas más de `saga.reconciler.stuck-after`
(por defecto 2 min) en PENDING (re-reserva) o STOCK_RESERVED (re-solicita despacho).
La reserva de stock es idempotente, por lo que reenviar no infla el reservado.

### CartExpirer (`@Scheduled`, cada 60s)
Busca `CartItem` RESERVED cuyo `expiresAt` ya pasó, emite un evento de compensación
para liberar su reserva de stock y borra el ítem. Evita que carritos abandonados
mantengan stock reservado indefinidamente.

## Eventos Kafka

### Produce: `saga.stock.reserve-command`
**Cuándo:** Al crear una nueva orden
**Evento:** StockReserveEvent

### Produce: `saga.stock.compensate-command`
**Cuándo:** Cuando el despacho falla (compensación)
**Evento:** StockReserveEvent

### Produce: `saga.despacho.create-command`
**Cuándo:** Cuando la reserva de stock es exitosa
**Evento:** DespachoRequestEvent

### Consume: `saga.stock.reserve-reply`
**Evento:** StockReserveResponseEvent
**Comportamiento:**
- success=true → actualizar orden a STOCK_RESERVED, solicitar despacho
- success=false → actualizar orden a STOCK_FAILED

### Consume: `saga.despacho.create-reply`
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
- `DISPATCHING` - Despacho en proceso
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
