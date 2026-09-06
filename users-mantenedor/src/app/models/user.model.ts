/**
 * Usuario del sistema tal como lo devuelve el backend, con su estado
 * (habilitado), fecha de creación y roles asignados.
 */
export interface User {
  id: number;
  username: string;
  email: string;
  fullName: string;
  enabled: boolean;
  createdAt: string;
  roles: string[];
}

/**
 * Datos necesarios para crear un nuevo usuario.
 */
export interface CreateUserRequest {
  username: string;
  email: string;
  password: string;
  fullName: string;
  roles: string[];
}

/**
 * Datos para actualizar un usuario existente; todos los campos son opcionales
 * (solo se envían los que se desean modificar).
 */
export interface UpdateUserRequest {
  email?: string;
  fullName?: string;
  enabled?: boolean;
  password?: string;
  roles?: string[];
}
