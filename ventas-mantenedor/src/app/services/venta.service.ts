import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Order, OrderCreateRequest, OrderStatus } from '../models/models';

/**
 * Servicio HTTP para la gestión de órdenes de venta en el mantenedor.
 *
 * Encapsula el CRUD y las operaciones de estado contra el recurso
 * `/api/v1/ventas` del venta-service (orquestador SAGA), incluyendo consultas
 * por cliente y por estado, cancelación y actualización manual del estado.
 */
@Injectable({ providedIn: 'root' })
export class VentaService {
  private readonly apiUrl = '/api/v1/ventas';

  constructor(private http: HttpClient) {}

  /**
   * Obtiene todas las órdenes de venta.
   * @returns observable con la lista de órdenes.
   */
  getAll(): Observable<Order[]> {
    return this.http.get<Order[]>(this.apiUrl);
  }

  /**
   * Obtiene una orden por su identificador.
   * @param id identificador de la orden.
   * @returns observable con la orden solicitada.
   */
  getById(id: string): Observable<Order> {
    return this.http.get<Order>(`${this.apiUrl}/${id}`);
  }

  /**
   * Crea una nueva orden de venta, iniciando la SAGA en el backend.
   * @param order datos de la orden a crear.
   * @returns observable con la orden creada.
   */
  create(order: OrderCreateRequest): Observable<Order> {
    return this.http.post<Order>(this.apiUrl, order);
  }

  /**
   * Cancela una orden de venta, disparando las compensaciones de la SAGA.
   * @param id identificador de la orden a cancelar.
   * @returns observable con la orden actualizada.
   */
  cancel(id: string): Observable<Order> {
    return this.http.post<Order>(`${this.apiUrl}/${id}/cancel`, {});
  }

  /**
   * Actualiza manualmente el estado de una orden.
   * @param id identificador de la orden.
   * @param status nuevo estado a aplicar (enviado como parámetro de consulta).
   * @returns observable con la orden actualizada.
   */
  updateStatus(id: string, status: OrderStatus): Observable<Order> {
    const params = new HttpParams().set('status', status);
    return this.http.put<Order>(`${this.apiUrl}/${id}/status`, null, { params });
  }

  /**
   * Obtiene las órdenes asociadas a un cliente.
   * @param customerId identificador del cliente.
   * @returns observable con las órdenes del cliente.
   */
  getByCustomer(customerId: string): Observable<Order[]> {
    return this.http.get<Order[]>(`${this.apiUrl}/customer/${customerId}`);
  }

  /**
   * Obtiene las órdenes que se encuentran en un estado determinado.
   * @param status estado por el que filtrar.
   * @returns observable con las órdenes en ese estado.
   */
  getByStatus(status: string): Observable<Order[]> {
    return this.http.get<Order[]>(`${this.apiUrl}/status/${status}`);
  }
}
