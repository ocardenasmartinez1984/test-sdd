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
      <div class="login-box">
        <h1>🛒 Punto de Venta</h1>
        <p>Ingresa con tus credenciales para comenzar a vender</p>

        @if (error()) {
          <div class="error-msg">{{ error() }}</div>
        }

        <form (ngSubmit)="onLogin()">
          <div class="form-group">
            <label>Usuario</label>
            <input [(ngModel)]="username" name="username" placeholder="Tu usuario" required>
          </div>
          <div class="form-group">
            <label>Contraseña</label>
            <input type="password" [(ngModel)]="password" name="password" placeholder="Tu contraseña" required>
          </div>
          <button type="submit" class="btn-login" [disabled]="loading()">
            {{ loading() ? 'Ingresando...' : '🚀 Ingresar al POS' }}
          </button>
        </form>
      </div>
    </div>
  `
})
export class LoginComponent {
  username = '';
  password = '';
  error = signal('');
  loading = signal(false);

  constructor(private authService: AuthService, private router: Router) {}

  onLogin(): void {
    this.loading.set(true);
    this.error.set('');

    this.authService.login(this.username, this.password).subscribe({
      next: () => this.router.navigate(['/']),
      error: () => {
        this.error.set('Credenciales inválidas');
        this.loading.set(false);
      }
    });
  }
}
