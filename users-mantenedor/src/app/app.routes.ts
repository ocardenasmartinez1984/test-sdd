import { Routes } from '@angular/router';
import { authGuard } from './guards/auth.guard';

/**
 * Tabla de rutas del mantenedor de Usuarios.
 *
 * Define `/login` público y la vista `/users` protegida por el
 * {@link authGuard}; la raíz redirige a `/login`. La carga de componentes es
 * diferida (lazy loading).
 */
export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./pages/login/login.component').then(m => m.LoginComponent) },
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: 'users', loadComponent: () => import('./pages/users/users.component').then(m => m.UsersComponent), canActivate: [authGuard] }
];
