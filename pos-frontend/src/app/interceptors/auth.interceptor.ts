import { HttpInterceptorFn } from '@angular/common/http';

/**
 * Interceptor HTTP funcional que añade autenticación a las peticiones salientes.
 *
 * Si existe un token JWT en `localStorage`, clona la petición agregando la
 * cabecera `Authorization: Bearer <token>` antes de continuar la cadena. Se
 * registra globalmente en {@link appConfig}.
 * @param req petición HTTP saliente.
 * @param next siguiente manejador de la cadena de interceptores.
 * @returns el flujo del evento HTTP resultante.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = localStorage.getItem('pos_token');
  if (token) {
    req = req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
  }
  return next(req);
};
