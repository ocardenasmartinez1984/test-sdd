import { Routes } from '@angular/router';
import { authGuard } from './guards/auth.guard';

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./pages/login/login.component').then(m => m.LoginComponent) },
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: 'users', loadComponent: () => import('./pages/users/users.component').then(m => m.UsersComponent), canActivate: [authGuard] }
];
