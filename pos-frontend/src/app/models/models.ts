export interface Product {
  id: string;
  sku: string;
  name: string;
  quantity: number;
  reservedQuantity: number;
  price: number;
}

export interface CartItem {
  product: Product;
  quantity: number;
}

export interface OrderCreateRequest {
  customerId: string;
  productId: string;
  quantity: number;
  totalAmount: number;
}

export interface Order {
  id: string;
  customerId: string;
  productId: string;
  quantity: number;
  totalAmount: number;
  status: string;
  createdAt: string;
}

export interface CartAddRequest {
  sessionId: string;
  productId: string;
  quantity: number;
  unitPrice: number;
}

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
