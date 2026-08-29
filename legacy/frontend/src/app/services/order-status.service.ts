import { Injectable } from '@angular/core';
import { OrderStatus } from '../models/models';

export interface StatusConfig {
  label: string;
  icon: string;
  badgeClass: string;
}

@Injectable({ providedIn: 'root' })
export class OrderStatusService {
  private readonly statusMap: Record<OrderStatus, StatusConfig> = {
    'PENDING': { label: 'Pendiente', icon: '⏳', badgeClass: 'badge-pending' },
    'STOCK_RESERVED': { label: 'Stock Reservado', icon: '📦', badgeClass: 'badge-info' },
    'STOCK_FAILED': { label: 'Stock Fallido', icon: '❌', badgeClass: 'badge-danger' },
    'DISPATCHING': { label: 'Despachando', icon: '🚛', badgeClass: 'badge-info' },
    'DISPATCH_FAILED': { label: 'Despacho Fallido', icon: '⚠️', badgeClass: 'badge-danger' },
    'COMPLETED': { label: 'Completada', icon: '✅', badgeClass: 'badge-success' },
    'CANCELLED': { label: 'Cancelada', icon: '🚫', badgeClass: 'badge-danger' },
  };

  getConfig(status: OrderStatus): StatusConfig {
    return this.statusMap[status] || { label: status, icon: '📋', badgeClass: 'badge-warning' };
  }

  getLabel(status: OrderStatus): string {
    return this.getConfig(status).label;
  }

  getIcon(status: OrderStatus): string {
    return this.getConfig(status).icon;
  }

  getBadgeClass(status: OrderStatus): string {
    return this.getConfig(status).badgeClass;
  }

  getAllStatuses(): OrderStatus[] {
    return Object.keys(this.statusMap) as OrderStatus[];
  }
}
