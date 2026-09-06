import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Product } from '../models/models';

/**
 * Servicio HTTP para la gestión del inventario en el mantenedor de Ventas.
 *
 * Encapsula las operaciones de lectura y escritura de productos contra el
 * recurso `/api/v1/stock` del stock-service (consulta, alta y actualización).
 */
@Injectable({ providedIn: 'root' })
export class StockService {
  private readonly apiUrl = '/api/v1/stock';

  constructor(private http: HttpClient) {}

  /**
   * Obtiene el listado completo de productos.
   * @returns observable con todos los productos.
   */
  getAll(): Observable<Product[]> {
    return this.http.get<Product[]>(this.apiUrl);
  }

  /**
   * Obtiene un producto por su identificador.
   * @param id identificador del producto.
   * @returns observable con el producto solicitado.
   */
  getById(id: string): Observable<Product> {
    return this.http.get<Product>(`${this.apiUrl}/${id}`);
  }

  /**
   * Crea un nuevo producto en el inventario.
   * @param product datos del producto a crear.
   * @returns observable con el producto creado.
   */
  create(product: Product): Observable<Product> {
    return this.http.post<Product>(this.apiUrl, product);
  }

  /**
   * Actualiza un producto existente.
   * @param id identificador del producto a actualizar.
   * @param product nuevos datos del producto.
   * @returns observable con el producto actualizado.
   */
  update(id: string, product: Product): Observable<Product> {
    return this.http.put<Product>(`${this.apiUrl}/${id}`, product);
  }
}
