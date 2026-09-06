import { Injectable, signal, NgZone, inject, Injector } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';

/**
 * Respuesta de autenticación devuelta por el backend tras un login/registro
 * exitoso: token JWT y datos del usuario. Se persiste en `localStorage`.
 */
export interface AuthResponse {
  token: string;
  username: string;
  fullName: string;
  roles: string[];
}

/**
 * Credenciales enviadas al backend para iniciar sesión.
 */
export interface LoginRequest {
  username: string;
  password: string;
}

/**
 * Datos enviados al backend para registrar un nuevo usuario.
 */
export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
  fullName: string;
}

/**
 * Servicio de autenticación y gestión de sesión del mantenedor de Ventas.
 *
 * Gestiona login y registro contra `/api/v1/auth`, persiste el token JWT y el
 * usuario en `localStorage` y expone su estado reactivo mediante signals
 * ({@link isAuthenticated}, {@link currentUser}). Controla el vencimiento
 * automático de la sesión (10 minutos) con un temporizador que, al expirar,
 * cierra sesión y redirige a `/login`.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly apiUrl = '/api/v1/auth';
  private readonly TOKEN_KEY = 'auth_token';
  private readonly USER_KEY = 'auth_user';
  private readonly LOGIN_TIME_KEY = 'auth_login_time';
  private readonly SESSION_DURATION_MS = 10 * 60 * 1000; // 10 minutos

  private sessionTimer: ReturnType<typeof setTimeout> | null = null;
  private injector = inject(Injector);
  private ngZone = inject(NgZone);
  private http = inject(HttpClient);

  isAuthenticated = signal(this.hasToken());
  currentUser = signal<AuthResponse | null>(this.getStoredUser());

  constructor() {
    this.initSessionCheck();
  }

  /**
   * Autentica al usuario y, si tiene éxito, persiste la sesión e inicia el
   * temporizador de expiración.
   * @param request credenciales de acceso.
   * @returns observable con la respuesta de autenticación.
   */
  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/login`, request).pipe(
      tap(response => this.storeAuth(response))
    );
  }

  /**
   * Registra un nuevo usuario y, si tiene éxito, lo autentica persistiendo la
   * sesión e iniciando el temporizador de expiración.
   * @param request datos de registro del usuario.
   * @returns observable con la respuesta de autenticación.
   */
  register(request: RegisterRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/register`, request).pipe(
      tap(response => this.storeAuth(response))
    );
  }

  /**
   * Cierra la sesión: detiene el temporizador, borra el token y datos del
   * usuario de `localStorage` y actualiza los signals a "no autenticado".
   */
  logout(): void {
    this.clearSessionTimer();
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.USER_KEY);
    localStorage.removeItem(this.LOGIN_TIME_KEY);
    this.isAuthenticated.set(false);
    this.currentUser.set(null);
  }

  /**
   * Devuelve el token JWT almacenado, o null si no hay sesión.
   * @returns token JWT o null.
   */
  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  private hasToken(): boolean {
    return !!localStorage.getItem(this.TOKEN_KEY);
  }

  private getStoredUser(): AuthResponse | null {
    const user = localStorage.getItem(this.USER_KEY);
    return user ? JSON.parse(user) : null;
  }

  /**
   * Persiste la respuesta de autenticación en `localStorage`, actualiza los
   * signals de estado e inicia el temporizador de expiración de sesión.
   * @param response respuesta de autenticación a almacenar.
   */
  private storeAuth(response: AuthResponse): void {
    localStorage.setItem(this.TOKEN_KEY, response.token);
    localStorage.setItem(this.USER_KEY, JSON.stringify(response));
    localStorage.setItem(this.LOGIN_TIME_KEY, Date.now().toString());
    this.isAuthenticated.set(true);
    this.currentUser.set(response);
    this.startSessionTimer(this.SESSION_DURATION_MS);
  }

  /**
   * Al arrancar el servicio, verifica si existe una sesión previa vigente:
   * expira la sesión si ya venció o reinicia el temporizador con el tiempo
   * restante.
   */
  private initSessionCheck(): void {
    if (!this.hasToken()) {
      return;
    }

    const loginTime = localStorage.getItem(this.LOGIN_TIME_KEY);
    if (!loginTime) {
      this.expireSession();
      return;
    }

    const elapsed = Date.now() - parseInt(loginTime, 10);
    const remaining = this.SESSION_DURATION_MS - elapsed;

    if (remaining <= 0) {
      this.expireSession();
    } else {
      this.startSessionTimer(remaining);
    }
  }

  /**
   * Programa el vencimiento automático de la sesión fuera de la zona de
   * Angular y, al cumplirse, expira la sesión dentro de la zona.
   * @param duration milisegundos hasta la expiración.
   */
  private startSessionTimer(duration: number): void {
    this.clearSessionTimer();
    this.ngZone.runOutsideAngular(() => {
      this.sessionTimer = setTimeout(() => {
        this.ngZone.run(() => this.expireSession());
      }, duration);
    });
  }

  private clearSessionTimer(): void {
    if (this.sessionTimer) {
      clearTimeout(this.sessionTimer);
      this.sessionTimer = null;
    }
  }

  /**
   * Expira la sesión: cierra sesión y navega a `/login`, resolviendo el Router
   * de forma diferida para evitar dependencias circulares.
   */
  private expireSession(): void {
    this.logout();
    const router = this.injector.get(Router);
    router.navigate(['/login']);
  }
}
