import { Injectable } from '@angular/core';
import { OrderStatus } from '../models/models';

/**
 * Configuración de presentación para un estado de orden: etiqueta legible,
 * icono y clase CSS del badge.
 */
export interface StatusConfig {
  label: string;
  icon: string;
  badgeClass: string;
}

/**
 * Servicio auxiliar de presentación para los estados de orden ({@link OrderStatus}).
 *
 * Mapea cada estado de la SAGA a su etiqueta en español, icono y clase de
 * badge, ofreciendo accesos directos para la UI del mantenedor. No realiza
 * llamadas de red.
 */
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

  /**
   * Devuelve la configuración de presentación de un estado, o una por defecto
   * si el estado no está mapeado.
   * @param status estado de la orden.
   * @returns configuración con etiqueta, icono y clase de badge.
   */
  getConfig(status: OrderStatus): StatusConfig {
    return this.statusMap[status] || { label: status, icon: '📋', badgeClass: 'badge-warning' };
  }

  /**
   * Devuelve la etiqueta legible del estado.
   * @param status estado de la orden.
   * @returns etiqueta en español.
   */
  getLabel(status: OrderStatus): string {
    return this.getConfig(status).label;
  }

  /**
   * Devuelve el icono asociado al estado.
   * @param status estado de la orden.
   * @returns emoji del estado.
   */
  getIcon(status: OrderStatus): string {
    return this.getConfig(status).icon;
  }

  /**
   * Devuelve la clase CSS del badge asociado al estado.
   * @param status estado de la orden.
   * @returns nombre de clase CSS del badge.
   */
  getBadgeClass(status: OrderStatus): string {
    return this.getConfig(status).badgeClass;
  }

  /**
   * Devuelve todos los estados de orden reconocidos.
   * @returns lista de estados disponibles.
   */
  getAllStatuses(): OrderStatus[] {
    return Object.keys(this.statusMap) as OrderStatus[];
  }
}
