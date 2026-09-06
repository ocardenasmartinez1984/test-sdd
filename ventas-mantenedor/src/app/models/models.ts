// --- Stock Domain ---

/**
 * Producto del inventario (dominio de stock) con su SKU, cantidad total,
 * cantidad reservada y precio.
 */
export interface Product {
  id?: string;
  sku: string;
  name: string;
  quantity: number;
  reservedQuantity?: number;
  price: number;
}

// --- Venta (Order) Domain ---

/**
 * Orden de venta (dominio de ventas) con su estado SAGA, motivo de fallo y
 * marcas de tiempo.
 */
export interface Order {
  id?: string;
  customerId: string;
  productId: string;
  quantity: number;
  totalAmount: number;
  status?: OrderStatus;
  failureReason?: string;
  createdAt?: string;
  updatedAt?: string;
}

/**
 * Estados posibles de una orden a lo largo de la SAGA (desde pendiente hasta
 * completada, cancelada o con fallo de stock/despacho).
 */
export type OrderStatus =
  'PENDING' | 'STOCK_RESERVED' | 'STOCK_FAILED' |
  'DISPATCHING' | 'DISPATCH_FAILED' | 'COMPLETED' | 'CANCELLED';

/**
 * Datos necesarios para crear una orden de venta.
 */
export interface OrderCreateRequest {
  customerId: string;
  productId: string;
  quantity: number;
  totalAmount: number;
}

// --- Despacho (Dispatch) Domain ---

/**
 * Despacho (dominio de despacho) asociado a una orden, con número de
 * seguimiento, estado y marcas de tiempo.
 */
export interface Dispatch {
  id?: string;
  orderId: string;
  productId: string;
  quantity: number;
  customerId: string;
  trackingNumber?: string;
  status?: DispatchStatus;
  createdAt?: string;
  updatedAt?: string;
}

/**
 * Estados posibles de un despacho (desde preparación hasta entrega, fallo o
 * cancelación).
 */
export type DispatchStatus =
  'PREPARANDO' | 'ENVIADO' | 'EN_CAMINO' | 'ENTREGADO' | 'FALLIDO' | 'CANCELADO';
