// --- Stock Domain ---

export interface Product {
  id?: string;
  sku: string;
  name: string;
  quantity: number;
  reservedQuantity?: number;
  price: number;
}

// --- Venta (Order) Domain ---

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

export type OrderStatus =
  'PENDING' | 'STOCK_RESERVED' | 'STOCK_FAILED' |
  'DISPATCHING' | 'DISPATCH_FAILED' | 'COMPLETED' | 'CANCELLED';

export interface OrderCreateRequest {
  customerId: string;
  productId: string;
  quantity: number;
  totalAmount: number;
}

// --- Despacho (Dispatch) Domain ---

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

export type DispatchStatus =
  'PREPARANDO' | 'ENVIADO' | 'EN_CAMINO' | 'ENTREGADO' | 'FALLIDO' | 'CANCELADO';
