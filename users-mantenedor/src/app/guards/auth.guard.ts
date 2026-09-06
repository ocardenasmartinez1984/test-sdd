import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { HttpClient } from '@angular/common/http';
import { catchError, map, of } from 'rxjs';

/**
 * Guard de ruta que protege las vistas autenticadas del mantenedor de Usuarios.
 *
 * Redirige a `/login` si no hay token; si existe, lo valida contra
 * `/api/v1/auth/validate` y, ante token inválido o error de red, cierra la
 * sesión y redirige a `/login`. Solo permite activar la ruta con token válido.
 * @returns observable/boolean que indica si la ruta puede activarse.
 */
export const authGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const http = inject(HttpClient);

  const token = authService.getToken();

  if (!token) {
    router.navigate(['/login']);
    return false;
  }

  return http.get<boolean>(`/api/v1/auth/validate`, { params: { token } }).pipe(
    map(isValid => {
      if (isValid) {
        return true;
      }
      authService.logout();
      router.navigate(['/login']);
      return false;
    }),
    catchError(() => {
      authService.logout();
      router.navigate(['/login']);
      return of(false);
    })
  );
};
