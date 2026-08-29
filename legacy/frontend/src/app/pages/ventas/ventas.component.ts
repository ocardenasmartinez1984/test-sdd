import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { VentaService } from '../../services/venta.service';
import { StockService } from '../../services/stock.service';
import { NotificationService } from '../../services/notification.service';
import { OrderStatusService } from '../../services/order-status.service';
import { Order, OrderCreateRequest, OrderStatus, Product } from '../../models/models';

@Component({
  selector: 'app-ventas',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="container">
      <div class="page-header">
        <h2>🛒 Gestión de Ventas</h2>
        <button class="btn btn-primary" (click)="openCreateModal()">
          <span class="material-icons" style="font-size:18px">add</span> Nueva Venta
        </button>
      </div>

      @if (notification.message()) {
        <div class="alert"
             [class.alert-success]="!notification.isError()"
             [class.alert-error]="notification.isError()">
          {{ notification.message() }}
        </div>
      }

      <!-- Stats -->
      <div class="stats-grid">
        <div class="stat-card"
             (click)="filterByStatus(null)"
             [class.stat-active]="activeFilter() === null">
          <span class="stat-icon">📋</span>
          <div class="stat-info">
            <span class="stat-value">{{ orders().length }}</span>
            <span class="stat-label">Total</span>
          </div>
        </div>
        <div class="stat-card"
             (click)="filterByStatus('PENDING')"
             [class.stat-active]="activeFilter() === 'PENDING'">
          <span class="stat-icon">⏳</span>
          <div class="stat-info">
            <span class="stat-value">{{ countByStatus('PENDING') }}</span>
            <span class="stat-label">Pendientes</span>
          </div>
        </div>
        <div class="stat-card"
             (click)="filterByStatus('STOCK_RESERVED')"
             [class.stat-active]="activeFilter() === 'STOCK_RESERVED'">
          <span class="stat-icon">📦</span>
          <div class="stat-info">
            <span class="stat-value">{{ countByStatus('STOCK_RESERVED') }}</span>
            <span class="stat-label">Stock Reservado</span>
          </div>
        </div>
        <div class="stat-card"
             (click)="filterByStatus('COMPLETED')"
             [class.stat-active]="activeFilter() === 'COMPLETED'">
          <span class="stat-icon">✅</span>
          <div class="stat-info">
            <span class="stat-value">{{ countByStatus('COMPLETED') }}</span>
            <span class="stat-label">Completadas</span>
          </div>
        </div>
        <div class="stat-card"
             (click)="filterByStatus('CANCELLED')"
             [class.stat-active]="activeFilter() === 'CANCELLED'">
          <span class="stat-icon">🚫</span>
          <div class="stat-info">
            <span class="stat-value">{{ countByStatus('CANCELLED') }}</span>
            <span class="stat-label">Canceladas</span>
          </div>
        </div>
      </div>

      <!-- Tabla de órdenes -->
      <div class="card">
        <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px">
          <h4 style="color:var(--text); font-weight:700; margin:0">
            {{ activeFilter() ? 'Órdenes: ' + statusService.getLabel(activeFilter()!) : 'Todas las Órdenes' }}
          </h4>
          <div style="display:flex; gap:8px">
            @if (activeFilter()) {
              <button class="btn btn-secondary btn-sm" (click)="filterByStatus(null)">Ver todas</button>
            }
            <button class="btn btn-secondary btn-sm" (click)="loadOrders()">
              <span class="material-icons" style="font-size:16px">refresh</span> Actualizar
            </button>
          </div>
        </div>
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>Cliente</th>
              <th>Producto</th>
              <th>Cant.</th>
              <th>Total</th>
              <th>Estado SAGA</th>
              <th>Fecha</th>
              <th>Acciones</th>
            </tr>
          </thead>
          <tbody>
            @for (order of filteredOrders(); track order.id) {
              <tr>
                <td><code>{{ order.id?.substring(0, 8) }}</code></td>
                <td>{{ order.customerId }}</td>
                <td><code>{{ order.productId?.substring(0, 8) }}...</code></td>
                <td>{{ order.quantity }}</td>
                <td>{{ order.totalAmount | currency:'USD' }}</td>
                <td>
                  <span class="badge" [class]="statusService.getBadgeClass(order.status!)">
                    {{ statusService.getLabel(order.status!) }}
                  </span>
                </td>
                <td>{{ order.createdAt | date:'dd/MM/yy HH:mm' }}</td>
                <td>
                  <div class="action-cell">
                    <select [(ngModel)]="statusSelections[order.id!]" class="status-select">
                      @for (s of allStatuses; track s) {
                        <option [value]="s">{{ statusService.getLabel(s) }}</option>
                      }
                    </select>
                    <button class="btn btn-primary btn-xs"
                            (click)="updateStatus(order)"
                            title="Aplicar cambio de estado">
                      <span class="material-icons" style="font-size:14px">check</span>
                    </button>
                    <button class="btn btn-secondary btn-xs"
                            (click)="openDetailModal(order)"
                            title="Ver detalle">
                      <span class="material-icons" style="font-size:14px">visibility</span>
                    </button>
                    @if (isCancellable(order)) {
                      <button class="btn btn-xs btn-cancel"
                              (click)="cancelOrder(order.id!)"
                              title="Cancelar">
                        <span class="material-icons" style="font-size:14px">close</span>
                      </button>
                    }
                  </div>
                </td>
              </tr>
            } @empty {
              <tr>
                <td colspan="8" style="text-align:center; padding:60px; color:var(--text-muted)">
                  {{ getEmptyMessage() }}
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>

      <!-- Modal Detalle -->
      @if (selectedOrder()) {
        <div class="modal-overlay" (click)="closeDetailModal()">
          <div class="modal" (click)="$event.stopPropagation()">
            <div class="modal-header">
              <h3>🛒 Detalle de la Orden</h3>
              <button class="modal-close" (click)="closeDetailModal()">✕</button>
            </div>
            <div class="modal-body">
              <div class="detail-grid">
                <div class="detail-item">
                  <span class="detail-label">ID</span>
                  <code class="detail-value">{{ selectedOrder()!.id }}</code>
                </div>
                <div class="detail-item">
                  <span class="detail-label">CLIENTE</span>
                  <span class="detail-value">{{ selectedOrder()!.customerId }}</span>
                </div>
                <div class="detail-item">
                  <span class="detail-label">PRODUCTO ID</span>
                  <code class="detail-value">{{ selectedOrder()!.productId }}</code>
                </div>
                <div class="detail-item">
                  <span class="detail-label">CANTIDAD</span>
                  <span class="detail-value">{{ selectedOrder()!.quantity }} unidades</span>
                </div>
                <div class="detail-item">
                  <span class="detail-label">TOTAL</span>
                  <span class="detail-value highlight">{{ selectedOrder()!.totalAmount | currency:'USD' }}</span>
                </div>
                <div class="detail-item">
                  <span class="detail-label">ESTADO</span>
                  <span class="badge" [class]="statusService.getBadgeClass(selectedOrder()!.status!)">
                    {{ statusService.getLabel(selectedOrder()!.status!) }}
                  </span>
                </div>
                <div class="detail-item">
                  <span class="detail-label">CREADA</span>
                  <span class="detail-value">{{ selectedOrder()!.createdAt | date:'dd/MM/yyyy HH:mm:ss' }}</span>
                </div>
                <div class="detail-item">
                  <span class="detail-label">ACTUALIZADA</span>
                  <span class="detail-value">{{ selectedOrder()!.updatedAt | date:'dd/MM/yyyy HH:mm:ss' }}</span>
                </div>
                @if (selectedOrder()!.failureReason) {
                  <div class="detail-item" style="grid-column: 1 / -1">
                    <span class="detail-label">MOTIVO DE FALLO</span>
                    <span class="detail-value" style="color:#f87171">{{ selectedOrder()!.failureReason }}</span>
                  </div>
                }
              </div>

              <div class="status-section">
                <h4 style="color:var(--text); font-weight:700; margin-bottom:12px">Cambiar Estado</h4>
                <div class="status-buttons">
                  @for (s of allStatuses; track s) {
                    <button
                      class="status-btn"
                      [class.status-btn-active]="s === selectedOrder()!.status"
                      [disabled]="s === selectedOrder()!.status"
                      (click)="updateDetailStatus(s)">
                      {{ statusService.getIcon(s) }} {{ statusService.getLabel(s) }}
                    </button>
                  }
                </div>
              </div>
            </div>
          </div>
        </div>
      }

      <!-- Modal Nueva Venta -->
      @if (showCreateModal()) {
        <div class="modal-overlay" (click)="closeCreateModal()">
          <div class="modal" (click)="$event.stopPropagation()">
            <div class="modal-header">
              <h3>🛒 Nueva Venta (Inicia SAGA)</h3>
              <button class="modal-close" (click)="closeCreateModal()">✕</button>
            </div>
            <div class="modal-body">
              <div class="form-group">
                <label>Cliente ID</label>
                <input [(ngModel)]="createForm.customerId" placeholder="cliente-001">
              </div>
              <div class="form-group">
                <label>Producto</label>
                <select [(ngModel)]="createForm.productId" (ngModelChange)="onProductChange()">
                  <option value="">-- Seleccionar --</option>
                  @for (p of availableProducts(); track p.id) {
                    <option [value]="p.id">
                      {{ p.name }} (Disp: {{ getAvailableQuantity(p) }})
                    </option>
                  }
                </select>
              </div>
              <div class="form-group">
                <label>Cantidad</label>
                <input type="number"
                       [(ngModel)]="createForm.quantity"
                       (ngModelChange)="recalculateTotal()"
                       min="1">
              </div>
              <div class="form-group">
                <label>Total (auto)</label>
                <input type="number" [(ngModel)]="createForm.totalAmount" readonly>
              </div>
              <div class="modal-actions">
                <button class="btn btn-secondary" (click)="closeCreateModal()">Cancelar</button>
                <button class="btn btn-success" (click)="submitCreateOrder()">🚀 Crear Venta</button>
              </div>
            </div>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .stats-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
      gap: 16px;
      margin-bottom: 24px;
    }
    .stat-card {
      cursor: pointer;
      transition: all 0.3s;
    }
    .stat-card:hover {
      transform: translateY(-2px);
      border-color: rgba(99, 102, 241, 0.3);
    }
    .stat-active {
      border-color: var(--primary) !important;
      background: linear-gradient(145deg, rgba(99, 102, 241, 0.12), rgba(30, 27, 75, 0.8)) !important;
      box-shadow: 0 4px 20px rgba(99, 102, 241, 0.2) !important;
    }
    .action-cell {
      display: flex;
      align-items: center;
      gap: 6px;
    }
    .status-select {
      width: auto;
      min-width: 140px;
      padding: 6px 10px;
      font-size: 12px;
      border-radius: 8px;
      background: rgba(255,255,255,0.04);
      border: 1px solid rgba(255,255,255,0.1);
      color: var(--text);
    }
    .btn-xs {
      padding: 5px 8px;
      font-size: 12px;
      border-radius: 6px;
      min-width: unset;
    }
    .btn-sm {
      padding: 8px 14px;
      font-size: 13px;
    }
    .btn-cancel {
      background: rgba(239, 68, 68, 0.1);
      border: 1px solid rgba(239, 68, 68, 0.3);
      color: #f87171;
    }
    .btn-cancel:hover {
      background: rgba(239, 68, 68, 0.2);
    }
    .modal-overlay {
      position: fixed;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background: rgba(0,0,0,0.6);
      backdrop-filter: blur(4px);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 1000;
      animation: fadeIn 0.2s ease;
    }
    .modal {
      background: linear-gradient(145deg, rgba(30, 27, 75, 0.95), rgba(15, 15, 35, 0.98));
      border: 1px solid rgba(255,255,255,0.1);
      border-radius: 20px;
      padding: 32px;
      width: 90%;
      max-width: 700px;
      max-height: 85vh;
      overflow-y: auto;
      box-shadow: 0 30px 80px rgba(0,0,0,0.5);
      animation: slideUp 0.3s ease;
    }
    .modal-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 24px;
    }
    .modal-header h3 {
      color: var(--text);
      font-size: 20px;
      font-weight: 700;
    }
    .modal-close {
      width: 32px;
      height: 32px;
      border-radius: 8px;
      border: 1px solid rgba(255,255,255,0.1);
      background: rgba(255,255,255,0.05);
      color: var(--text-muted);
      font-size: 16px;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all 0.2s;
    }
    .modal-close:hover {
      background: rgba(239, 68, 68, 0.1);
      border-color: rgba(239, 68, 68, 0.3);
      color: #f87171;
    }
    .modal-actions {
      display: flex;
      gap: 12px;
      justify-content: flex-end;
      margin-top: 24px;
    }
    .detail-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: 20px;
      margin-bottom: 24px;
    }
    .detail-item {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }
    .detail-label {
      color: var(--text-muted);
      font-size: 11px;
      font-weight: 700;
      letter-spacing: 0.5px;
      text-transform: uppercase;
    }
    .detail-value {
      color: var(--text);
      font-size: 14px;
    }
    .detail-value.highlight {
      color: var(--primary);
      font-weight: 700;
      font-size: 16px;
    }
    .status-section {
      padding-top: 20px;
      border-top: 1px solid rgba(255,255,255,0.06);
    }
    .status-buttons {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }
    .status-btn {
      padding: 10px 16px;
      border-radius: 10px;
      border: 1px solid rgba(255,255,255,0.1);
      background: rgba(255,255,255,0.04);
      color: var(--text);
      font-size: 13px;
      font-weight: 500;
      cursor: pointer;
      transition: all 0.2s;
    }
    .status-btn:hover:not(:disabled) {
      transform: translateY(-1px);
      border-color: var(--primary);
      background: rgba(99, 102, 241, 0.1);
    }
    .status-btn:disabled {
      opacity: 0.4;
      cursor: not-allowed;
      border-color: var(--primary);
      background: rgba(99, 102, 241, 0.15);
    }
    .status-btn-active {
      border-color: var(--primary);
      background: rgba(99, 102, 241, 0.2);
      box-shadow: 0 0 10px rgba(99, 102, 241, 0.2);
    }
    @keyframes fadeIn {
      from { opacity: 0; }
      to { opacity: 1; }
    }
    @keyframes slideUp {
      from { opacity: 0; transform: translateY(20px); }
      to { opacity: 1; transform: translateY(0); }
    }
  `]
})
export class VentasComponent implements OnInit {

  // --- Dependencies (Dependency Inversion) ---
  private readonly ventaService = inject(VentaService);
  private readonly stockService = inject(StockService);
  readonly notification = inject(NotificationService);
  readonly statusService = inject(OrderStatusService);

  // --- State ---
  readonly orders = signal<Order[]>([]);
  readonly filteredOrders = signal<Order[]>([]);
  readonly availableProducts = signal<Product[]>([]);
  readonly selectedOrder = signal<Order | null>(null);
  readonly showCreateModal = signal(false);
  readonly activeFilter = signal<OrderStatus | null>(null);

  // --- Form State ---
  createForm: OrderCreateRequest = this.getEmptyForm();
  statusSelections: Record<string, OrderStatus> = {};
  readonly allStatuses: OrderStatus[];

  private selectedProduct: Product | null = null;

  constructor() {
    this.allStatuses = this.statusService.getAllStatuses();
  }

  // --- Lifecycle ---

  ngOnInit(): void {
    this.loadOrders();
  }

  // --- Data Loading (Single Responsibility: only fetches and sets state) ---

  loadOrders(): void {
    this.ventaService.getAll().subscribe({
      next: (orders) => {
        this.orders.set(orders);
        this.applyFilter();
        this.syncStatusSelections(orders);
      },
      error: () => this.notification.error('Error al cargar órdenes')
    });
  }

  private syncStatusSelections(orders: Order[]): void {
    for (const order of orders) {
      if (order.id) {
        this.statusSelections[order.id] = order.status || 'PENDING';
      }
    }
  }

  // --- Filtering ---

  filterByStatus(status: OrderStatus | null): void {
    this.activeFilter.set(status);
    this.applyFilter();
  }

  countByStatus(status: OrderStatus): number {
    return this.orders().filter(o => o.status === status).length;
  }

  private applyFilter(): void {
    const filter = this.activeFilter();
    if (filter) {
      this.filteredOrders.set(this.orders().filter(o => o.status === filter));
    } else {
      this.filteredOrders.set(this.orders());
    }
  }

  // --- Status Update Actions ---

  updateStatus(order: Order): void {
    const newStatus = this.statusSelections[order.id!];
    if (!newStatus || newStatus === order.status) {
      return;
    }

    this.ventaService.updateStatus(order.id!, newStatus).subscribe({
      next: () => {
        this.notification.success(
          `Orden ${order.id?.substring(0, 8)} → ${this.statusService.getLabel(newStatus)}`
        );
        this.loadOrders();
      },
      error: () => this.notification.error('Error al actualizar estado')
    });
  }

  updateDetailStatus(status: OrderStatus): void {
    const order = this.selectedOrder();
    if (!order?.id || status === order.status) {
      return;
    }

    this.ventaService.updateStatus(order.id, status).subscribe({
      next: (updated) => {
        this.selectedOrder.set(updated);
        this.notification.success(`Estado → ${this.statusService.getLabel(status)}`);
        this.loadOrders();
      },
      error: () => this.notification.error('Error al actualizar estado')
    });
  }

  cancelOrder(id: string): void {
    this.ventaService.cancel(id).subscribe({
      next: () => {
        this.notification.success('Venta cancelada');
        this.loadOrders();
      },
      error: (e) => this.notification.error(e.error?.error || 'Error al cancelar')
    });
  }

  // --- Detail Modal ---

  openDetailModal(order: Order): void {
    this.selectedOrder.set(order);
  }

  closeDetailModal(): void {
    this.selectedOrder.set(null);
  }

  // --- Create Modal ---

  openCreateModal(): void {
    this.createForm = this.getEmptyForm();
    this.selectedProduct = null;
    this.stockService.getAll().subscribe({
      next: (products) => this.availableProducts.set(products)
    });
    this.showCreateModal.set(true);
  }

  closeCreateModal(): void {
    this.showCreateModal.set(false);
  }

  onProductChange(): void {
    this.selectedProduct = this.availableProducts().find(
      p => p.id === this.createForm.productId
    ) || null;
    this.recalculateTotal();
  }

  recalculateTotal(): void {
    if (this.selectedProduct) {
      this.createForm.totalAmount = this.selectedProduct.price * this.createForm.quantity;
    }
  }

  submitCreateOrder(): void {
    this.ventaService.create(this.createForm).subscribe({
      next: () => {
        this.notification.success('Venta creada! SAGA en proceso...');
        this.closeCreateModal();
        this.loadOrders();
        setTimeout(() => this.loadOrders(), 3000);
      },
      error: () => this.notification.error('Error al crear venta')
    });
  }

  // --- Pure UI Helpers (no side effects) ---

  isCancellable(order: Order): boolean {
    return order.status === 'PENDING' || order.status === 'STOCK_RESERVED';
  }

  getAvailableQuantity(product: Product): number {
    return product.quantity - (product.reservedQuantity || 0);
  }

  getEmptyMessage(): string {
    const filter = this.activeFilter();
    if (filter) {
      return `No hay órdenes con estado ${this.statusService.getLabel(filter)}`;
    }
    return 'No hay ventas. Crea una para iniciar la SAGA.';
  }

  private getEmptyForm(): OrderCreateRequest {
    return { customerId: '', productId: '', quantity: 1, totalAmount: 0 };
  }
}
