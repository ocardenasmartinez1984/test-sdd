/**
 * Producto del catálogo con su información de stock y precio.
 * Refleja la entidad de inventario expuesta por el stock-service.
 */
export interface Product {
  id: string;
  sku: string;
  name: string;
  quantity: number;
  reservedQuantity: number;
  price: number;
}

/**
 * Ítem del carrito en el estado local del cliente: asocia un producto con la
 * cantidad seleccionada por el usuario.
 */
export interface CartItem {
  product: Product;
  quantity: number;
}

/**
 * Datos necesarios para crear una orden de venta (petición al venta-service).
 */
export interface OrderCreateRequest {
  customerId: string;
  productId: string;
  quantity: number;
  totalAmount: number;
}

/**
 * Orden de venta devuelta por el backend, con su estado y fecha de creación.
 */
export interface Order {
  id: string;
  customerId: string;
  productId: string;
  quantity: number;
  totalAmount: number;
  status: string;
  createdAt: string;
}

/**
 * Petición para agregar/actualizar un ítem del carrito (reserva de stock).
 */
export interface CartAddRequest {
  sessionId: string;
  productId: string;
  quantity: number;
  unitPrice: number;
}

/**
 * Respuesta del backend para un ítem del carrito reservado, con su estado y
 * fechas de creación y expiración de la reserva.
 */
export interface CartItemResponse {
  id: string;
  sessionId: string;
  productId: string;
  quantity: number;
  unitPrice: number;
  status: string;
  createdAt: string;
  expiresAt: string;
}
