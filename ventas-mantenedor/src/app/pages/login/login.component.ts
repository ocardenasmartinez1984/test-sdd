import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService, LoginRequest, RegisterRequest } from '../../services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="login-container">
      <div class="bg-orbs">
        <div class="orb orb-1"></div>
        <div class="orb orb-2"></div>
        <div class="orb orb-3"></div>
      </div>
      <div class="login-card">
        <div class="login-header">
          <div class="logo-pulse">⚡</div>
          <h1>SAGA</h1>
          <p>Microservices Platform</p>
        </div>

        @if (error()) {
          <div class="alert alert-error">{{ error() }}</div>
        }

        @if (!isRegister()) {
          <form (ngSubmit)="onLogin()">
            <div class="form-group">
              <label>Usuario</label>
              <input [(ngModel)]="loginForm.username" name="username" placeholder="Tu nombre de usuario" required>
            </div>
            <div class="form-group">
              <label>Contraseña</label>
              <input type="password" [(ngModel)]="loginForm.password" name="password" placeholder="Tu contraseña" required>
            </div>
            <button type="submit" class="btn btn-primary btn-block" [disabled]="loading()">
              @if (loading()) {
                <span class="spinner"></span> Ingresando...
              } @else {
                Iniciar Sesión
              }
            </button>
          </form>
          <p class="toggle-link">
            ¿No tienes cuenta? <a (click)="isRegister.set(true)">Registrarse</a>
          </p>
        } @else {
          <form (ngSubmit)="onRegister()">
            <div class="form-group">
              <label>Nombre Completo</label>
              <input [(ngModel)]="registerForm.fullName" name="fullName" placeholder="Tu nombre" required>
            </div>
            <div class="form-group">
              <label>Usuario</label>
              <input [(ngModel)]="registerForm.username" name="username" placeholder="Nombre de usuario" required>
            </div>
            <div class="form-group">
              <label>Email</label>
              <input type="email" [(ngModel)]="registerForm.email" name="email" placeholder="tu@email.com" required>
            </div>
            <div class="form-group">
              <label>Contraseña</label>
              <input type="password" [(ngModel)]="registerForm.password" name="password" placeholder="Mínimo 6 caracteres" required>
            </div>
            <button type="submit" class="btn btn-success btn-block" [disabled]="loading()">
              @if (loading()) {
                <span class="spinner"></span> Registrando...
              } @else {
                Crear Cuenta
              }
            </button>
          </form>
          <p class="toggle-link">
            ¿Ya tienes cuenta? <a (click)="isRegister.set(false)">Iniciar Sesión</a>
          </p>
        }
      </div>
    </div>
  `,
  styles: [`
    .login-container {
      display: flex;
      align-items: center;
      justify-content: center;
      min-height: 100vh;
      position: relative;
      overflow: hidden;
    }
    .bg-orbs {
      position: absolute;
      width: 100%;
      height: 100%;
      pointer-events: none;
    }
    .orb {
      position: absolute;
      border-radius: 50%;
      filter: blur(80px);
      animation: float 10s ease-in-out infinite;
    }
    .orb-1 {
      width: 400px;
      height: 400px;
      background: rgba(99, 102, 241, 0.2);
      top: -100px;
      left: -100px;
      animation-delay: 0s;
    }
    .orb-2 {
      width: 300px;
      height: 300px;
      background: rgba(6, 182, 212, 0.15);
      bottom: -50px;
      right: -50px;
      animation-delay: -3s;
    }
    .orb-3 {
      width: 200px;
      height: 200px;
      background: rgba(16, 185, 129, 0.12);
      top: 50%;
      left: 60%;
      animation-delay: -6s;
    }
    @keyframes float {
      0%, 100% { transform: translate(0, 0) scale(1); }
      33% { transform: translate(30px, -30px) scale(1.05); }
      66% { transform: translate(-20px, 20px) scale(0.95); }
    }
    .login-card {
      background: linear-gradient(145deg, rgba(30, 27, 75, 0.8), rgba(15, 15, 35, 0.9));
      backdrop-filter: blur(40px);
      border: 1px solid rgba(255,255,255,0.08);
      border-radius: 24px;
      padding: 48px;
      width: 100%;
      max-width: 440px;
      box-shadow: 0 30px 80px rgba(0,0,0,0.5);
      position: relative;
      z-index: 1;
      animation: fadeInUp 0.6s ease;
    }
    .login-header {
      text-align: center;
      margin-bottom: 36px;
    }
    .logo-pulse {
      font-size: 48px;
      margin-bottom: 12px;
      display: inline-block;
      animation: logoPulse 3s ease-in-out infinite;
    }
    @keyframes logoPulse {
      0%, 100% { transform: scale(1); filter: brightness(1); }
      50% { transform: scale(1.1); filter: brightness(1.3); }
    }
    .login-header h1 {
      font-size: 36px;
      font-weight: 800;
      background: linear-gradient(135deg, #fff, #818cf8);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
      margin-bottom: 4px;
    }
    .login-header p {
      color: rgba(255,255,255,0.4);
      font-size: 13px;
      letter-spacing: 1px;
    }
    .btn-block {
      width: 100%;
      padding: 15px;
      font-size: 15px;
      margin-top: 8px;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
    }
    .spinner {
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
    .toggle-link {
      text-align: center;
      margin-top: 20px;
      font-size: 14px;
      color: rgba(255,255,255,0.4);
    }
    .toggle-link a {
      color: #818cf8;
      cursor: pointer;
      font-weight: 600;
      transition: all 0.2s;
    }
    .toggle-link a:hover {
      color: #a5b4fc;
      text-decoration: underline;
    }

    @keyframes fadeInUp {
      from { opacity: 0; transform: translateY(30px); }
      to { opacity: 1; transform: translateY(0); }
    }
  `]
})
/**
 * Componente de la pantalla de inicio de sesión y registro del mantenedor.
 *
 * Alterna entre los formularios de login y registro y delega la autenticación
 * en {@link AuthService}. Gestiona el estado de carga y errores de la UI y
 * navega a `/ventas` al autenticar o registrar correctamente.
 */
export class LoginComponent {
  isRegister = signal(false);
  loading = signal(false);
  error = signal('');

  loginForm: LoginRequest = { username: '', password: '' };
  registerForm: RegisterRequest = { username: '', email: '', password: '', fullName: '' };

  constructor(private authService: AuthService, private router: Router) {}

  /**
   * Envía las credenciales al {@link AuthService}; navega a `/ventas` en caso
   * de éxito o muestra el error devuelto por el backend.
   */
  onLogin() {
    this.loading.set(true);
    this.error.set('');
    this.authService.login(this.loginForm).subscribe({
      next: () => this.router.navigate(['/ventas']),
      error: (err) => {
        this.error.set(err.error?.error || 'Error al iniciar sesión');
        this.loading.set(false);
      }
    });
  }

  /**
   * Envía los datos de registro al {@link AuthService}; navega a `/ventas` en
   * caso de éxito o muestra el error devuelto por el backend.
   */
  onRegister() {
    this.loading.set(true);
    this.error.set('');
    this.authService.register(this.registerForm).subscribe({
      next: () => this.router.navigate(['/ventas']),
      error: (err) => {
        this.error.set(err.error?.error || 'Error al registrarse');
        this.loading.set(false);
      }
    });
  }
}
