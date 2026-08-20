import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CartAddRequest, CartItemResponse } from '../models/models';

@Injectable({ providedIn: 'root' })
export class CartService {
  private readonly apiUrl = '/api/v1/cart';

  constructor(private http: HttpClient) {}

  addToCart(request: CartAddRequest): Observable<CartItemResponse> {
    return this.http.post<CartItemResponse>(this.apiUrl, request);
  }

  getCart(sessionId: string): Observable<CartItemResponse[]> {
    return this.http.get<CartItemResponse[]>(`${this.apiUrl}/${sessionId}`);
  }

  removeFromCart(sessionId: string, productId: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${sessionId}/${productId}`);
  }

  clearCart(sessionId: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${sessionId}`);
  }
}
