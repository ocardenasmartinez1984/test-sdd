import { Routes } from '@angular/router';
import { authGuard } from './guards/auth.guard';

/**
 * Tabla de rutas de la aplicación POS Frontend.
 *
 * Define la navegación con carga diferida (lazy loading) de componentes:
 * `/login` para la pantalla de autenticación y la ruta raíz para el punto de
 * venta ({@link PosComponent}), protegida por el {@link authGuard}. Cualquier
 * ruta desconocida redirige a la raíz.
 */
export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./pages/login/login.component').then(m => m.LoginComponent)
  },
  {
    path: '',
    loadComponent: () => import('./pages/pos/pos.component').then(m => m.PosComponent),
    canActivate: [authGuard]
  },
  { path: '**', redirectTo: '' }
];
