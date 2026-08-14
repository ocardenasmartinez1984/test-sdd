# Stock Service - Spec Driven Development

## Descripción
Microservicio encargado de gestionar el inventario de productos. Participa en el patrón SAGA como **participante**, respondiendo a solicitudes de reserva de stock del orquestador (Venta).

## Responsabilidades
- Gestionar el catálogo de productos (CRUD)
- Reservar stock cuando se crea una orden
- Liberar stock (compensación) cuando una orden falla

## API REST

### GET /api/stock
**Descripción:** Listar todos los productos
**Response:** `200 OK` - Array de Product

### GET /api/stock/{id}
**Descripción:** Obtener un producto por ID
**Response:** `200 OK` - Product | `404 Not Found`

### POST /api/stock
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

### PUT /api/stock/{id}
**Descripción:** Actualizar un producto existente
**Response:** `200 OK` - Product actualizado | `404 Not Found`

## Eventos Kafka

### Consume: `stock-reserve-topic`
**Evento:** StockReserveEvent
```json
{
  "orderId": "string",
  "productId": "string",
  "quantity": 5
}
```
**Comportamiento:**
1. Buscar producto por ID
2. Verificar disponibilidad (quantity - reservedQuantity >= solicitado)
3. Si hay stock: reservar y responder success=true
4. Si no hay stock: responder success=false con razón

### Consume: `stock-compensate-topic`
**Evento:** StockReserveEvent (mismo formato)
**Comportamiento:**
1. Buscar producto por ID
2. Liberar la cantidad reservada
3. Restaurar el stock disponible

### Produce: `stock-reserve-response-topic`
**Evento:** StockReserveResponseEvent
```json
{
  "orderId": "string",
  "productId": "string",
  "success": true,
  "reason": "string (null si success)"
}
```

## Modelo de Datos (MongoDB)

### Product
| Campo | Tipo | Descripción |
|-------|------|-------------|
| id | String | ID auto-generado |
| sku | String | Código único del producto |
| name | String | Nombre del producto |
| quantity | int | Cantidad total en inventario |
| reservedQuantity | int | Cantidad actualmente reservada |
| price | double | Precio unitario |

## Reglas de Negocio
1. Un producto solo puede ser reservado si `quantity - reservedQuantity >= cantidad solicitada`
2. La compensación siempre libera el stock reservado y lo devuelve al disponible
3. No se permite stock negativo

## Configuración
- **Puerto:** 8081
- **Base de datos:** MongoDB - `stock_db`
- **Consumer group:** `stock-group`
