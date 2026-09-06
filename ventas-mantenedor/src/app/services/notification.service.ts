import { Injectable, signal } from '@angular/core';

/**
 * Notificación de UI: mensaje y su tipo (éxito o error).
 */
export interface Notification {
  message: string;
  type: 'success' | 'error';
}

/**
 * Servicio de notificaciones tipo toast para el mantenedor de Ventas.
 *
 * Expone el mensaje y su severidad mediante signals reactivos y auto-oculta la
 * notificación tras un tiempo fijo. Es un colaborador transversal de las
 * vistas para dar feedback al usuario.
 */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly DISPLAY_DURATION_MS = 5000;

  readonly message = signal('');
  readonly isError = signal(false);

  /**
   * Muestra una notificación de éxito.
   * @param message texto a mostrar.
   */
  success(message: string): void {
    this.show(message, false);
  }

  /**
   * Muestra una notificación de error.
   * @param message texto a mostrar.
   */
  error(message: string): void {
    this.show(message, true);
  }

  /**
   * Establece el mensaje y su severidad en los signals y programa su
   * ocultamiento automático tras {@link DISPLAY_DURATION_MS} milisegundos.
   * @param message texto a mostrar.
   * @param isError true para error, false para éxito.
   */
  private show(message: string, isError: boolean): void {
    this.message.set(message);
    this.isError.set(isError);
    setTimeout(() => this.message.set(''), this.DISPLAY_DURATION_MS);
  }
}
