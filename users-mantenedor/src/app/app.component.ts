import { Component, inject } from '@angular/core';
import { RouterOutlet, Router } from '@angular/router';
import { AuthService } from './services/auth.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  template: `
    @if (authService.isAuthenticated()) {
      <div class="app-layout">
        <nav class="top-bar">
          <div class="logo">
            <span class="logo-icon">👤</span>
            <h1>Mantenedor de Usuarios</h1>
          </div>
          <button class="btn-logout" (click)="logout()">
            <span class="material-icons">logout</span>
            Salir
          </button>
        </nav>
        <main class="main-content">
          <router-outlet />
        </main>
      </div>
    } @else {
      <router-outlet />
    }
  `,
  styles: [`
    .app-layout {
      min-height: 100vh;
      display: flex;
      flex-direction: column;
    }
    .top-bar {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 16px 36px;
      background: linear-gradient(180deg, rgba(15, 15, 35, 0.98) 0%, rgba(30, 27, 75, 0.95) 100%);
      backdrop-filter: blur(20px);
      border-bottom: 1px solid rgba(255,255,255,0.06);
      position: sticky;
      top: 0;
      z-index: 100;
    }
    .logo {
      display: flex;
      align-items: center;
      gap: 14px;
    }
    .logo-icon {
      font-size: 28px;
    }
    .logo h1 {
      font-size: 20px;
      font-weight: 700;
      background: linear-gradient(135deg, #fff, #818cf8);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
    }
    .btn-logout {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 10px 18px;
      border-radius: 10px;
      background: rgba(239, 68, 68, 0.1);
      border: 1px solid rgba(239, 68, 68, 0.2);
      color: #f87171;
      cursor: pointer;
      font-size: 14px;
      font-weight: 500;
      font-family: 'Inter', sans-serif;
      transition: all 0.3s;
    }
    .btn-logout:hover {
      background: rgba(239, 68, 68, 0.2);
      transform: scale(1.02);
    }
    .btn-logout .material-icons {
      font-size: 18px;
    }
    .main-content {
      flex: 1;
      padding: 36px;
    }
  `]
})
/**
 * Componente raíz del mantenedor de Usuarios.
 *
 * Actúa como shell: muestra la barra superior con el botón de cerrar sesión
 * cuando hay sesión activa y delega el contenido al `<router-outlet>`.
 * Colabora con {@link AuthService} para el estado de autenticación y el
 * logout.
 */
export class AppComponent {
  authService = inject(AuthService);
  private router = inject(Router);

  /** Cierra la sesión del usuario y navega a la pantalla de login. */
  logout() {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
