import { Component, inject } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive, Router } from '@angular/router';
import { AuthService } from './services/auth.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    @if (authService.isAuthenticated()) {
      <div class="app-layout">
        <nav class="sidebar">
          <div class="sidebar-header">
            <div class="logo">
              <span class="logo-icon">⚡</span>
              <div>
                <h1>SAGA</h1>
                <span class="subtitle">Microservices</span>
              </div>
            </div>
          </div>
          <ul class="nav-menu">
            <li>
              <a routerLink="/stock" routerLinkActive="active">
                <span class="material-icons">inventory_2</span>
                <span>Stock</span>
                <span class="nav-indicator"></span>
              </a>
            </li>
            <li>
              <a routerLink="/ventas" routerLinkActive="active">
                <span class="material-icons">shopping_cart</span>
                <span>Ventas</span>
                <span class="nav-indicator"></span>
              </a>
            </li>
            <li>
              <a routerLink="/despachos" routerLinkActive="active">
                <span class="material-icons">local_shipping</span>
                <span>Despachos</span>
                <span class="nav-indicator"></span>
              </a>
            </li>
          </ul>
          <div class="sidebar-footer">
            <div class="user-card">
              <div class="avatar">{{ authService.currentUser()?.fullName?.charAt(0) || 'U' }}</div>
              <div class="user-info">
                <span class="user-name">{{ authService.currentUser()?.fullName }}</span>
                <span class="user-role">{{ authService.currentUser()?.roles?.[0] || 'User' }}</span>
              </div>
            </div>
            <button class="btn-logout" (click)="logout()">
              <span class="material-icons">logout</span>
            </button>
          </div>
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
      display: flex;
      min-height: 100vh;
    }
    .sidebar {
      width: 280px;
      background: linear-gradient(180deg, rgba(15, 15, 35, 0.98) 0%, rgba(30, 27, 75, 0.95) 100%);
      backdrop-filter: blur(20px);
      border-right: 1px solid rgba(255,255,255,0.06);
      padding: 28px 0;
      position: fixed;
      height: 100vh;
      display: flex;
      flex-direction: column;
      z-index: 100;
    }
    .sidebar-header {
      padding: 0 24px 28px;
      border-bottom: 1px solid rgba(255,255,255,0.06);
    }
    .logo {
      display: flex;
      align-items: center;
      gap: 14px;
    }
    .logo-icon {
      font-size: 32px;
      animation: pulse 3s infinite;
    }
    .logo h1 {
      font-size: 24px;
      font-weight: 800;
      background: linear-gradient(135deg, #fff, #818cf8);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
    }
    .logo .subtitle {
      font-size: 11px;
      color: rgba(255,255,255,0.4);
      text-transform: uppercase;
      letter-spacing: 2px;
    }
    .nav-menu {
      list-style: none;
      padding: 20px 12px;
      flex: 1;
    }
    .nav-menu li {
      margin-bottom: 4px;
    }
    .nav-menu li a {
      display: flex;
      align-items: center;
      gap: 14px;
      padding: 14px 18px;
      color: rgba(255,255,255,0.5);
      text-decoration: none;
      font-weight: 500;
      font-size: 14px;
      border-radius: 12px;
      transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
      position: relative;
    }
    .nav-menu li a:hover {
      background: rgba(99, 102, 241, 0.08);
      color: rgba(255,255,255,0.9);
      transform: translateX(4px);
    }
    .nav-menu li a.active {
      background: linear-gradient(135deg, rgba(99, 102, 241, 0.2), rgba(6, 182, 212, 0.1));
      color: white;
      box-shadow: 0 4px 15px rgba(99, 102, 241, 0.15);
    }
    .nav-menu li a.active .material-icons {
      color: #818cf8;
    }
    .nav-indicator {
      display: none;
    }
    .nav-menu li a.active .nav-indicator {
      display: block;
      position: absolute;
      right: 12px;
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background: #818cf8;
      box-shadow: 0 0 10px #818cf8;
    }
    .sidebar-footer {
      padding: 16px 16px;
      border-top: 1px solid rgba(255,255,255,0.06);
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .user-card {
      display: flex;
      align-items: center;
      gap: 10px;
      flex: 1;
    }
    .avatar {
      width: 36px;
      height: 36px;
      border-radius: 10px;
      background: linear-gradient(135deg, var(--primary), var(--accent));
      display: flex;
      align-items: center;
      justify-content: center;
      font-weight: 700;
      font-size: 14px;
      color: white;
    }
    .user-name {
      display: block;
      font-size: 13px;
      font-weight: 600;
      color: rgba(255,255,255,0.8);
    }
    .user-role {
      display: block;
      font-size: 10px;
      color: rgba(255,255,255,0.4);
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }
    .btn-logout {
      width: 36px;
      height: 36px;
      border-radius: 10px;
      background: rgba(239, 68, 68, 0.1);
      border: 1px solid rgba(239, 68, 68, 0.2);
      color: #f87171;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all 0.3s;
    }
    .btn-logout:hover {
      background: rgba(239, 68, 68, 0.2);
      transform: scale(1.05);
    }
    .btn-logout .material-icons {
      font-size: 18px;
    }
    .main-content {
      flex: 1;
      margin-left: 280px;
      padding: 36px;
      min-height: 100vh;
    }

    @keyframes pulse {
      0%, 100% { opacity: 1; transform: scale(1); }
      50% { opacity: 0.8; transform: scale(1.05); }
    }
  `]
})
export class AppComponent {
  authService = inject(AuthService);
  private router = inject(Router);

  logout() {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
