import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { User, CreateUserRequest, UpdateUserRequest } from '../models/user.model';

/**
 * Servicio HTTP para la gestión de usuarios en el mantenedor.
 *
 * Encapsula el CRUD completo contra el recurso `/api/v1/users` del
 * auth-service (listado, consulta, alta, actualización y borrado de usuarios).
 */
@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly apiUrl = '/api/v1/users';

  constructor(private http: HttpClient) {}

  /**
   * Obtiene el listado completo de usuarios.
   * @returns observable con todos los usuarios.
   */
  getAll(): Observable<User[]> {
    return this.http.get<User[]>(this.apiUrl);
  }

  /**
   * Obtiene un usuario por su identificador.
   * @param id identificador del usuario.
   * @returns observable con el usuario solicitado.
   */
  getById(id: number): Observable<User> {
    return this.http.get<User>(`${this.apiUrl}/${id}`);
  }

  /**
   * Crea un nuevo usuario.
   * @param request datos del usuario a crear.
   * @returns observable con el usuario creado.
   */
  create(request: CreateUserRequest): Observable<User> {
    return this.http.post<User>(this.apiUrl, request);
  }

  /**
   * Actualiza un usuario existente.
   * @param id identificador del usuario a actualizar.
   * @param request campos a modificar.
   * @returns observable con el usuario actualizado.
   */
  update(id: number, request: UpdateUserRequest): Observable<User> {
    return this.http.put<User>(`${this.apiUrl}/${id}`, request);
  }

  /**
   * Elimina un usuario.
   * @param id identificador del usuario a eliminar.
   * @returns observable que completa cuando el usuario es eliminado.
   */
  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
