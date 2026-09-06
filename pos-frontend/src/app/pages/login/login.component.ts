import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="login-wrapper">
      <!-- Animated background orbs -->
      <div class="login-bg-orb"></div>
      <div class="login-bg-orb"></div>

      <div class="login-box">
        <div style="text-align:center; margin-bottom: 8px;">
          <span style="font-size: 48px;">🛒</span>
        </div>
        <h1>Punto de Venta</h1>
        <p>Ingresa con tus credenciales para comenzar</p>

        @if (error()) {
          <div class="error-msg">⚠️ {{ error() }}</div>
        }

        <form (ngSubmit)="onLogin()">
          <div class="form-group">
            <label>👤 Usuario</label>
            <input
              [(ngModel)]="username"
              name="username"
              placeholder="Ingresa tu usuario"
              autocomplete="username"
              required
              [disabled]="loading()">
          </div>
          <div class="form-group">
            <label>🔒 Contraseña</label>
            <input
              type="password"
              [(ngModel)]="password"
              name="password"
              placeholder="Ingresa tu contraseña"
              autocomplete="current-password"
              required
              [disabled]="loading()">
          </div>
          <button type="submit" class="btn-login" [disabled]="loading() || !username.trim() || !password.trim()">
            @if (loading()) {
              <span class="spinner"></span> Verificando...
            } @else {
              🚀 Ingresar al POS
            }
          </button>
        </form>

        <div style="text-align:center; margin-top: 24px; font-size: 12px; color: var(--text-light);">
          Sistema de Punto de Venta v2.0
        </div>
      </div>
    </div>
  `,
  styles: [`
    .spinner {
      display: inline-block;
      width: 16px;
      height: 16px;
      border: 2px solid rgba(255,255,255,0.3);
      border-top-color: white;
      border-radius: 50%;
      animation: spin 0.6s linear infinite;
    }
    @keyframes spin {
      to { transform: rotate(360deg); }
    }
  `]
})
/**
 * Componente de la pantalla de inicio de sesión del POS.
 *
 * Presenta el formulario de credenciales y delega la autenticación en
 * {@link AuthService}. Gestiona el estado de carga y los mensajes de error de
 * la UI, y navega al punto de venta al autenticar correctamente.
 */
export class LoginComponent {
  username = '';
  password = '';
  error = signal('');
  loading = signal(false);

  constructor(private authService: AuthService, private router: Router) {}

  /**
   * Envía las credenciales al {@link AuthService}. Valida que no estén vacías,
   * muestra el spinner mientras se procesa y, en caso de éxito, navega a la
   * raíz; ante error muestra "Credenciales inválidas" (401) o un error de
   * conexión.
   */
  onLogin(): void {
    if (!this.username.trim() || !this.password.trim()) return;

    this.loading.set(true);
    this.error.set('');

    this.authService.login(this.username, this.password).subscribe({
      next: () => this.router.navigate(['/']),
      error: (err) => {
        this.error.set(err.status === 401 ? 'Credenciales inválidas' : 'Error de conexión con el servidor');
        this.loading.set(false);
      }
    });
  }
}
