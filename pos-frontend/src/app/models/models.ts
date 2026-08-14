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
