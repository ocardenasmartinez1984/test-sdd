import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Order, OrderCreateRequest } from '../models/models';

/**
 * Servicio HTTP para la creación de ventas desde el POS.
 *
 * Envía las órdenes al recurso `/api/v1/ventas` del backend, que actúa como
 * orquestador SAGA (reserva de stock y generación de despacho). Es el
 * colaborador principal del proceso de cobro del punto de venta.
 */
@Injectable({ providedIn: 'root' })
export class VentaService {
  private readonly apiUrl = '/api/v1/ventas';

  constructor(private http: HttpClient) {}

  /**
   * Crea una nueva orden de venta e inicia la SAGA en el backend.
   * @param order datos de la orden (cliente, producto, cantidad, total).
   * @returns observable con la orden creada y su estado inicial.
   */
  create(order: OrderCreateRequest): Observable<Order> {
    return this.http.post<Order>(this.apiUrl, order);
  }
}
