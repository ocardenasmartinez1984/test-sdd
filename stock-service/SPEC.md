# Stock Service - Spec Driven Development

## Descripción
Microservicio encargado de gestionar el inventario de productos. Participa en el patrón SAGA como **participante**, respondiendo a solicitudes de reserva de stock del orquestador (Venta).

## Responsabilidades
- Gestionar el catálogo de productos (CRUD)
- Reservar stock cuando se crea una orden o se agrega al carrito (idempotente y actualizable)
- Liberar stock (compensación) cuando una orden falla o un carrito se cierra/expira

## API REST

Prefijo base: `/api/v1/stock`

### GET /api/v1/stock
**Descripción:** Listar todos los productos
**Response:** `200 OK` - Array de Product

### GET /api/v1/stock/{id}
**Descripción:** Obtener un producto por ID
**Response:** `200 OK` - Product | `404 Not Found`

### GET /api/v1/stock/{id}/available
**Descripción:** Obtener la cantidad disponible (quantity - reservedQuantity)
**Response:** `200 OK` - `{ "availableQuantity": n }` | `404 Not Found`

### GET /api/v1/stock/{id}/exists
**Descripción:** Comprobar si un producto existe
**Response:** `200 OK` - `{ "exists": true|false }`

### POST /api/v1/stock
**Descripción:** Crear un nuevo producto
**Request Body:**
```json
{
  "sku": "string",
  "name": "string",
  "quantity": 100,
  "price": 29.99
}
```
**Response:** `200 OK` - Product creado

### PUT /api/v1/stock/{id}/quantity
**Descripción:** Fijar la cantidad total en inventario de un producto
**Request Body:** `{ "quantity": 200 }`
**Response:** `200 OK` - Product actualizado | `404 Not Found` | `400 Bad Request` (si falta quantity)

## Eventos Kafka

### Consume: `saga.stock.reserve-command`
**Evento:** StockReserveEvent
```json
{
  "orderId": "string",
  "productId": "string",
  "quantity": 5
}
```
**Comportamiento (reserva idempotente por delta):**
1. Buscar producto por ID
2. Calcular `delta = quantity - reservadoPreviamentePorEsaOrden` (ver `reservedByOrder`)
3. Si `delta == 0`: no hacer nada (idempotente ante reenvíos del reconciler / redelivery)
4. Si `delta > disponible`: responder success=false (el reservado nunca supera el disponible)
5. En otro caso: aplicar el delta a `reservedQuantity`, registrar `reservedByOrder[orderId] = quantity`, responder success=true

### Consume: `saga.stock.compensate-command`
**Evento:** StockReserveEvent (mismo formato)
**Comportamiento:**
1. Buscar producto por ID
2. Si la orden tiene reserva activa (`reservedByOrder[orderId]`): liberar esa cantidad exacta y eliminar la entrada
3. Idempotente: si no hay reserva para esa orden, no hace nada

### Produce: `saga.stock.reserve-reply`
**Evento:** StockReserveResponseEvent
```json
{
  "sagaId": "string",
  "orderId": "string",
  "productId": "string",
  "success": true,
  "reason": "string (null si success)"
}
```

> El consumer usa `ErrorHandlingDeserializer` (envuelve `StringDeserializer`/`JsonDeserializer`),
> de modo que un mensaje mal formado no bloquea el consumo del topic.

## Modelo de Datos (MongoDB)

### Product
| Campo | Tipo | Descripción |
|-------|------|-------------|
| id | String | ID auto-generado |
| sku | String | Código único del producto |
| name | String | Nombre del producto |
| quantity | int | Cantidad total en inventario |
| reservedQuantity | int | Cantidad actualmente reservada (suma de `reservedByOrder`) |
| reservedByOrder | Map<String,int> | Cantidad reservada por cada orden/carrito. Permite reserva idempotente y actualizable por delta |
| price | double | Precio unitario |

## Reglas de Negocio
1. Un producto solo puede reservar hasta `quantity - reservedQuantity` (el reservado nunca supera el disponible)
2. La reserva es **idempotente** por `orderId`: reenviar la misma cantidad no vuelve a sumar
3. La reserva es **actualizable**: cambiar la cantidad de una orden aplica solo el delta (subir o bajar)
4. La compensación libera exactamente la cantidad reservada por esa orden
5. No se permite stock negativo

## Configuración
- **Puerto:** 8081
- **Base de datos:** MongoDB - `stock_db`
- **Consumer group:** `stock-group`
