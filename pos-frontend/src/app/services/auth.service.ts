import { Injectable, signal, NgZone, inject, Injector } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';

/**
 * Respuesta de autenticación devuelta por el backend tras un login exitoso.
 *
 * Transporta el token JWT y los datos del usuario autenticado (nombre de
 * usuario, nombre completo y roles), que se persisten en `localStorage`.
 */
export interface AuthResponse {
  token: string;
  username: string;
  fullName: string;
  roles: string[];
}

/**
 * Servicio de autenticación y gestión de sesión del POS Frontend.
 *
 * Gestiona el login/logout contra `/api/v1/auth`, persiste el token JWT y el
 * usuario en `localStorage`, y expone su estado reactivo mediante signals
 * ({@link isAuthenticated}, {@link currentUser}). Controla además el vencimiento
 * automático de la sesión con un temporizador que expira a los 10 minutos y
 * redirige a `/login`.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly apiUrl = '/api/v1/auth';
  private readonly TOKEN_KEY = 'pos_token';
  private readonly USER_KEY = 'pos_user';
  private readonly LOGIN_TIME_KEY = 'pos_login_time';
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
   * Autentica al usuario contra el backend y, si tiene éxito, persiste la
   * sesión (token, usuario, hora de login) e inicia el temporizador de
   * expiración.
   * @param username nombre de usuario.
   * @param password contraseña.
   * @returns observable con la respuesta de autenticación (token y datos).
   */
  login(username: string, password: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/login`, { username, password }).pipe(
      tap(response => this.storeAuth(response))
    );
  }

  /**
   * Cierra la sesión: detiene el temporizador, borra el token y datos del
   * usuario de `localStorage` y actualiza los signals de estado a "no
   * autenticado".
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
   * calcula el tiempo transcurrido desde el login y expira la sesión si ya
   * venció, o reinicia el temporizador con el tiempo restante.
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
   * Programa el vencimiento automático de la sesión. El temporizador corre
   * fuera de la zona de Angular para no disparar detección de cambios y, al
   * cumplirse, expira la sesión dentro de la zona.
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
   * Expira la sesión: cierra sesión y navega a `/login`. El Router se resuelve
   * de forma diferida mediante el inyector para evitar dependencias circulares.
   */
  private expireSession(): void {
    this.logout();
    const router = this.injector.get(Router);
    router.navigate(['/login']);
  }
}
