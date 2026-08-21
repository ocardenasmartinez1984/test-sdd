import { Injectable, signal, NgZone, inject, Injector } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';

export interface AuthResponse {
  token: string;
  username: string;
  fullName: string;
  roles: string[];
}

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

  login(username: string, password: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/login`, { username, password }).pipe(
      tap(response => this.storeAuth(response))
    );
  }

  logout(): void {
    this.clearSessionTimer();
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.USER_KEY);
    localStorage.removeItem(this.LOGIN_TIME_KEY);
    this.isAuthenticated.set(false);
    this.currentUser.set(null);
  }

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

  private storeAuth(response: AuthResponse): void {
    localStorage.setItem(this.TOKEN_KEY, response.token);
    localStorage.setItem(this.USER_KEY, JSON.stringify(response));
    localStorage.setItem(this.LOGIN_TIME_KEY, Date.now().toString());
    this.isAuthenticated.set(true);
    this.currentUser.set(response);
    this.startSessionTimer(this.SESSION_DURATION_MS);
  }

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

  private expireSession(): void {
    this.logout();
    const router = this.injector.get(Router);
    router.navigate(['/login']);
  }
}
