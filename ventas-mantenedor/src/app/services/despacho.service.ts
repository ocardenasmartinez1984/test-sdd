import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Dispatch, DispatchStatus } from '../models/models';

/**
 * Servicio HTTP para la gestión de despachos en el mantenedor de Ventas.
 *
 * Encapsula las consultas y la actualización de estado contra el recurso
 * `/api/v1/despachos` del despacho-service, permitiendo buscar por orden,
 * número de seguimiento o estado.
 */
@Injectable({ providedIn: 'root' })
export class DespachoService {
  private readonly apiUrl = '/api/v1/despachos';

  constructor(private http: HttpClient) {}

  /**
   * Obtiene todos los despachos.
   * @returns observable con la lista de despachos.
   */
  getAll(): Observable<Dispatch[]> {
    return this.http.get<Dispatch[]>(this.apiUrl);
  }

  /**
   * Obtiene el despacho asociado a una orden.
   * @param orderId identificador de la orden.
   * @returns observable con el despacho de esa orden.
   */
  getByOrderId(orderId: string): Observable<Dispatch> {
    return this.http.get<Dispatch>(`${this.apiUrl}/order/${orderId}`);
  }

  /**
   * Obtiene un despacho por su número de seguimiento.
   * @param trackingNumber número de seguimiento del envío.
   * @returns observable con el despacho correspondiente.
   */
  getByTracking(trackingNumber: string): Observable<Dispatch> {
    return this.http.get<Dispatch>(`${this.apiUrl}/tracking/${trackingNumber}`);
  }

  /**
   * Obtiene los despachos que se encuentran en un estado determinado.
   * @param status estado por el que filtrar.
   * @returns observable con los despachos en ese estado.
   */
  getByStatus(status: DispatchStatus): Observable<Dispatch[]> {
    return this.http.get<Dispatch[]>(`${this.apiUrl}/status/${status}`);
  }

  /**
   * Actualiza el estado de un despacho.
   * @param id identificador del despacho.
   * @param status nuevo estado (enviado como parámetro de consulta).
   * @returns observable con el despacho actualizado.
   */
  updateStatus(id: string, status: DispatchStatus): Observable<Dispatch> {
    const params = new HttpParams().set('status', status);
    return this.http.put<Dispatch>(`${this.apiUrl}/${id}/status`, null, { params });
  }
}
