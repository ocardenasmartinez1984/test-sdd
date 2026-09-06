import { Routes } from '@angular/router';
import { authGuard } from './guards/auth.guard';

/**
 * Tabla de rutas del mantenedor de Ventas.
 *
 * Define la navegación con carga diferida: `/login` público y las vistas
 * `/stock`, `/ventas` y `/despachos` protegidas por el {@link authGuard}. La
 * raíz redirige a `/ventas`.
 */
export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./pages/login/login.component').then(m => m.LoginComponent) },
  { path: '', redirectTo: 'ventas', pathMatch: 'full' },
  { path: 'stock', loadComponent: () => import('./pages/stock/stock.component').then(m => m.StockComponent), canActivate: [authGuard] },
  { path: 'ventas', loadComponent: () => import('./pages/ventas/ventas.component').then(m => m.VentasComponent), canActivate: [authGuard] },
  { path: 'despachos', loadComponent: () => import('./pages/despachos/despachos.component').then(m => m.DespachosComponent), canActivate: [authGuard] }
];
