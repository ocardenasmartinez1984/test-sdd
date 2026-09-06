import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CartAddRequest, CartItemResponse } from '../models/models';

/**
 * Servicio HTTP para la gestión del carrito de compras del POS.
 *
 * Encapsula las llamadas al recurso `/api/v1/cart` del backend (a través del
 * API Gateway). Cada operación del carrito reserva o libera stock en el
 * servicio de ventas/stock, por lo que actúa como colaborador del flujo de
 * reserva previo a la venta.
 */
@Injectable({ providedIn: 'root' })
export class CartService {
  private readonly apiUrl = '/api/v1/cart';

  constructor(private http: HttpClient) {}

  /**
   * Agrega un producto al carrito, generando una reserva de stock.
   * @param request datos de la reserva (sesión, producto, cantidad, precio unitario).
   * @returns observable con el ítem de carrito creado en el backend.
   */
  addToCart(request: CartAddRequest): Observable<CartItemResponse> {
    return this.http.post<CartItemResponse>(this.apiUrl, request);
  }

  /**
   * Establece la cantidad absoluta de un producto ya presente en el carrito,
   * ajustando la reserva de stock al nuevo total.
   * @param request datos de la reserva con la cantidad objetivo.
   * @returns observable con el ítem de carrito actualizado.
   */
  setQuantity(request: CartAddRequest): Observable<CartItemResponse> {
    return this.http.put<CartItemResponse>(this.apiUrl, request);
  }

  /**
   * Obtiene todos los ítems del carrito asociados a una sesión.
   * @param sessionId identificador de la sesión de carrito.
   * @returns observable con la lista de ítems reservados.
   */
  getCart(sessionId: string): Observable<CartItemResponse[]> {
    return this.http.get<CartItemResponse[]>(`${this.apiUrl}/${sessionId}`);
  }

  /**
   * Elimina un producto concreto del carrito y libera su reserva de stock.
   * @param sessionId identificador de la sesión de carrito.
   * @param productId identificador del producto a quitar.
   * @returns observable que completa cuando el backend elimina el ítem.
   */
  removeFromCart(sessionId: string, productId: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${sessionId}/${productId}`);
  }

  /**
   * Vacía por completo el carrito de una sesión, liberando todas sus reservas.
   * @param sessionId identificador de la sesión de carrito.
   * @returns observable que completa cuando el backend vacía el carrito.
   */
  clearCart(sessionId: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${sessionId}`);
  }
}
