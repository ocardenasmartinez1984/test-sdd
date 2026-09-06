import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';

/**
 * Interceptor HTTP funcional que añade el token JWT a las peticiones salientes.
 *
 * Si hay token y la URL no pertenece a los endpoints de autenticación
 * (`/api/v1/auth/`), clona la petición agregando la cabecera
 * `Authorization: Bearer <token>`; en caso contrario la deja pasar intacta.
 * @param req petición HTTP saliente.
 * @param next siguiente manejador de la cadena de interceptores.
 * @returns el flujo del evento HTTP resultante.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const token = authService.getToken();

  if (token && !req.url.includes('/api/v1/auth/')) {
    const cloned = req.clone({
      setHeaders: { Authorization: `Bearer ${token}` }
    });
    return next(cloned);
  }

  return next(req);
};
