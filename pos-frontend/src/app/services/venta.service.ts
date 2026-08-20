import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Order, OrderCreateRequest } from '../models/models';

@Injectable({ providedIn: 'root' })
export class VentaService {
  private readonly apiUrl = '/api/v1/ventas';

  constructor(private http: HttpClient) {}

  create(order: OrderCreateRequest): Observable<Order> {
    return this.http.post<Order>(this.apiUrl, order);
  }
}
