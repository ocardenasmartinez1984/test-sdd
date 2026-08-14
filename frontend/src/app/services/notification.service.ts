import { Injectable, signal } from '@angular/core';

export interface Notification {
  message: string;
  type: 'success' | 'error';
}

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly DISPLAY_DURATION_MS = 5000;

  readonly message = signal('');
  readonly isError = signal(false);

  success(message: string): void {
    this.show(message, false);
  }

  error(message: string): void {
    this.show(message, true);
  }

  private show(message: string, isError: boolean): void {
    this.message.set(message);
    this.isError.set(isError);
    setTimeout(() => this.message.set(''), this.DISPLAY_DURATION_MS);
  }
}
