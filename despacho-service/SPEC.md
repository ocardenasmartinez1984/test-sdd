# Despacho Service - Spec Driven Development

## Descripción
Microservicio encargado de gestionar los despachos/envíos de productos. Participa en el patrón SAGA como **participante**, procesando solicitudes de despacho del orquestador (Venta).

## Responsabilidades
- Procesar solicitudes de despacho
- Generar números de tracking
- Gestionar el estado de los envíos
- Exponer API REST para consultas de despachos

## API REST

### GET /api/v1/despachos
**Descripción:** Listar todos los despachos
**Response:** `200 OK` - Array de Dispatch

### GET /api/v1/despachos/order/{orderId}
**Descripción:** Obtener despacho por ID de orden
**Response:** `200 OK` - Dispatch | `404 Not Found`

### GET /api/v1/despachos/tracking/{trackingNumber}
**Descripción:** Obtener despacho por número de tracking
**Response:** `200 OK` - Dispatch | `404 Not Found`

### GET /api/v1/despachos/status/{status}
**Descripción:** Listar despachos por estado
**Response:** `200 OK` - Array de Dispatch

## Eventos Kafka

### Consume: `saga.despacho.create-command`
**Evento:** DespachoRequestEvent
```json
{
  "orderId": "string",
  "productId": "string",
  "quantity": 2,
  "customerId": "string",
  "deliveryAddress": "string"
}
```
**Comportamiento:**
1. Generar número de tracking (formato: TRK-XXXXXXXX)
2. Crear registro de despacho con estado PROCESSING
3. Responder con success=true y trackingNumber
4. En caso de error: responder con success=false y razón

### Produce: `saga.despacho.create-reply`
**Evento:** DespachoResponseEvent
```json
{
  "orderId": "string",
  "success": true,
  "trackingNumber": "TRK-A1B2C3D4",
  "reason": "string (null si success)"
}
```

## Modelo de Datos (MongoDB)

### Dispatch
| Campo | Tipo | Descripción |
|-------|------|-------------|
| id | String | ID auto-generado |
| orderId | String | ID de la orden asociada |
| productId | String | ID del producto |
| quantity | int | Cantidad despachada |
| customerId | String | ID del cliente |
| deliveryAddress | String | Dirección de entrega |
| trackingNumber | String | Número de seguimiento |
| status | DispatchStatus | Estado del despacho |
| createdAt | LocalDateTime | Fecha de creación |
| updatedAt | LocalDateTime | Última actualización |

### DispatchStatus (enum)
- `PENDING` - Pendiente de procesamiento
- `PROCESSING` - En proceso
- `SHIPPED` - Enviado
- `DELIVERED` - Entregado
- `FAILED` - Fallido
- `CANCELLED` - Cancelado

## Reglas de Negocio
1. Cada despacho genera un número de tracking único
2. Un despacho inicia en estado PROCESSING
3. Si el procesamiento falla, se responde con success=false para que la SAGA compense
4. No puede haber dos despachos para la misma orden

## Configuración
- **Puerto:** 8083
- **Base de datos:** MongoDB - `despacho_db`
- **Consumer group:** `despacho-group`
