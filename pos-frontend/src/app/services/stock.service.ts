import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Product } from '../models/models';

/**
 * Servicio HTTP para consultar el catálogo de productos y su stock.
 *
 * Recupera los productos del recurso `/api/v1/stock` del backend, usado por el
 * catálogo del POS para mostrar disponibilidad y precios.
 */
@Injectable({ providedIn: 'root' })
export class StockService {
  private readonly apiUrl = '/api/v1/stock';

  constructor(private http: HttpClient) {}

  /**
   * Obtiene el listado completo de productos con su stock actual.
   * @returns observable con todos los productos del catálogo.
   */
  getAll(): Observable<Product[]> {
    return this.http.get<Product[]>(this.apiUrl);
  }
}
