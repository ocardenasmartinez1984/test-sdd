import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { routes } from './app.routes';
import { authInterceptor } from './interceptors/auth.interceptor';

/**
 * Configuración global del mantenedor de Usuarios (bootstrap standalone).
 *
 * Registra los proveedores raíz: detección de cambios por zona con
 * coalescencia de eventos, el enrutador con las {@link routes} y el cliente
 * HTTP con el {@link authInterceptor} que adjunta el token JWT.
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor]))
  ]
};
