import { Component, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DespachoService } from '../../services/despacho.service';
import { Dispatch, DispatchStatus } from '../../models/models';

@Component({
  selector: 'app-despachos',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="container">
      <div class="page-header">
        <h2>🚚 Gestión de Despachos</h2>
        <button class="btn btn-secondary" (click)="loadDispatches()">
          <span class="material-icons" style="font-size:18px">refresh</span> Actualizar
        </button>
      </div>

      @if (message()) {
        <div class="alert" [class.alert-success]="!isError()" [class.alert-error]="isError()">{{ message() }}</div>
      }

      <!-- Stats -->
      <div class="stats-grid">
        <div class="stat-card" (click)="filterByStatus(null)" [class.stat-active]="activeFilter() === null">
          <span class="stat-icon">📋</span>
          <div class="stat-info">
            <span class="stat-value">{{ dispatches().length }}</span>
            <span class="stat-label">Total</span>
          </div>
        </div>
        <div class="stat-card" (click)="filterByStatus('PREPARANDO')" [class.stat-active]="activeFilter() === 'PREPARANDO'">
          <span class="stat-icon">⏳</span>
          <div class="stat-info">
            <span class="stat-value">{{ countByStatus('PREPARANDO') }}</span>
            <span class="stat-label">Preparando</span>
          </div>
        </div>
        <div class="stat-card" (click)="filterByStatus('ENVIADO')" [class.stat-active]="activeFilter() === 'ENVIADO'">
          <span class="stat-icon">📦</span>
          <div class="stat-info">
            <span class="stat-value">{{ countByStatus('ENVIADO') }}</span>
            <span class="stat-label">Enviados</span>
          </div>
        </div>
        <div class="stat-card" (click)="filterByStatus('EN_CAMINO')" [class.stat-active]="activeFilter() === 'EN_CAMINO'">
          <span class="stat-icon">🚛</span>
          <div class="stat-info">
            <span class="stat-value">{{ countByStatus('EN_CAMINO') }}</span>
            <span class="stat-label">En Camino</span>
          </div>
        </div>
        <div class="stat-card" (click)="filterByStatus('ENTREGADO')" [class.stat-active]="activeFilter() === 'ENTREGADO'">
          <span class="stat-icon">✅</span>
          <div class="stat-info">
            <span class="stat-value">{{ countByStatus('ENTREGADO') }}</span>
            <span class="stat-label">Entregados</span>
          </div>
        </div>
      </div>

      <!-- Buscar por tracking -->
      <div class="card">
        <h4 style="color:var(--text); font-weight:700; margin-bottom:14px">🔍 Buscar por Tracking</h4>
        <div style="display:flex; gap:12px; align-items:center">
          <div class="form-group" style="flex:1; margin-bottom:0">
            <input [(ngModel)]="searchTracking" placeholder="TRK-XXXXXXXX" (keyup.enter)="searchByTracking()">
          </div>
          <button class="btn btn-primary" (click)="searchByTracking()">Buscar</button>
          <button class="btn btn-secondary" (click)="clearSearch()">Limpiar</button>
        </div>
      </div>

      @if (searchResult()) {
        <div class="card" style="border-left: 3px solid var(--primary)">
          <h4 style="color:var(--text); margin-bottom:14px">📍 Resultado de Búsqueda</h4>
          <div class="detail-grid">
            <div class="detail-item">
              <span class="detail-label">TRACKING</span>
              <code class="detail-value">{{ searchResult()!.trackingNumber }}</code>
            </div>
            <div class="detail-item">
              <span class="detail-label">ORDEN</span>
              <code class="detail-value">{{ searchResult()!.orderId }}</code>
            </div>
            <div class="detail-item">
              <span class="detail-label">ESTADO</span>
              <span class="badge" [class]="getStatusClass(searchResult()!.status!)">{{ getStatusLabel(searchResult()!.status!) }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">CANTIDAD</span>
              <span class="detail-value">{{ searchResult()!.quantity }} unidades</span>
            </div>
          </div>
          <div style="margin-top:16px; display:flex; gap:8px; align-items:center">
            <label style="color:var(--text-muted); font-size:13px; font-weight:600">Cambiar estado:</label>
            <select [(ngModel)]="newStatusForSearch" style="flex:0 0 auto; width:auto">
              @for (s of statuses; track s) {
                <option [value]="s">{{ getStatusLabel(s) }}</option>
              }
            </select>
            <button class="btn btn-primary btn-sm" (click)="updateSearchResultStatus()">Aplicar</button>
          </div>
        </div>
      }

      <!-- Tabla de despachos -->
      <div class="card">
        <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px">
          <h4 style="color:var(--text); font-weight:700; margin:0">
            {{ activeFilter() ? 'Despachos: ' + getStatusLabel(activeFilter()!) : 'Todos los Despachos' }}
          </h4>
          @if (activeFilter()) {
            <button class="btn btn-secondary btn-sm" (click)="filterByStatus(null)">Ver todos</button>
          }
        </div>
        <table>
          <thead>
            <tr>
              <th>Tracking</th>
              <th>Orden</th>
              <th>Cliente</th>
              <th>Producto</th>
              <th>Cant.</th>
              <th>Estado</th>
              <th>Fecha</th>
              <th>Acciones</th>
            </tr>
          </thead>
          <tbody>
            @for (d of filteredDispatches(); track d.id) {
              <tr>
                <td><code>{{ d.trackingNumber }}</code></td>
                <td><code>{{ d.orderId?.substring(0, 8) }}...</code></td>
                <td>{{ d.customerId }}</td>
                <td><code>{{ d.productId?.substring(0, 8) }}...</code></td>
                <td>{{ d.quantity }}</td>
                <td><span class="badge" [class]="getStatusClass(d.status!)">{{ getStatusLabel(d.status!) }}</span></td>
                <td>{{ d.createdAt | date:'dd/MM/yy HH:mm' }}</td>
                <td>
                  <div class="action-cell">
                    <select [(ngModel)]="statusSelections[d.id!]" class="status-select">
                      @for (s of statuses; track s) {
                        <option [value]="s">{{ getStatusLabel(s) }}</option>
                      }
                    </select>
                    <button class="btn btn-primary btn-xs" (click)="updateStatus(d)" title="Aplicar cambio de estado">
                      <span class="material-icons" style="font-size:14px">check</span>
                    </button>
                    <button class="btn btn-secondary btn-xs" (click)="showDetail(d)" title="Ver detalle">
                      <span class="material-icons" style="font-size:14px">visibility</span>
                    </button>
                  </div>
                </td>
              </tr>
            } @empty {
              <tr>
                <td colspan="8" style="text-align:center; padding:60px; color:var(--text-muted)">
                  {{ activeFilter() ? 'No hay despachos con estado ' + getStatusLabel(activeFilter()!) : 'No hay despachos. Crea una venta para generar uno.' }}
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>

      <!-- Modal Detalle -->
      @if (selectedDispatch()) {
        <div class="modal-overlay" (click)="closeDetail()">
          <div class="modal" (click)="$event.stopPropagation()">
            <div class="modal-header">
              <h3>📦 Detalle del Despacho</h3>
              <button class="modal-close" (click)="closeDetail()">✕</button>
            </div>
            <div class="modal-body">
              <div class="detail-grid-modal">
                <div class="detail-item">
                  <span class="detail-label">ID</span>
                  <code class="detail-value">{{ selectedDispatch()!.id }}</code>
                </div>
                <div class="detail-item">
                  <span class="detail-label">TRACKING</span>
                  <code class="detail-value highlight">{{ selectedDispatch()!.trackingNumber }}</code>
                </div>
                <div class="detail-item">
                  <span class="detail-label">ORDEN ID</span>
                  <code class="detail-value">{{ selectedDispatch()!.orderId }}</code>
                </div>
                <div class="detail-item">
                  <span class="detail-label">PRODUCTO ID</span>
                  <code class="detail-value">{{ selectedDispatch()!.productId }}</code>
                </div>
                <div class="detail-item">
                  <span class="detail-label">CLIENTE</span>
                  <span class="detail-value">{{ selectedDispatch()!.customerId }}</span>
                </div>
                <div class="detail-item">
                  <span class="detail-label">CANTIDAD</span>
                  <span class="detail-value">{{ selectedDispatch()!.quantity }} unidades</span>
                </div>
                <div class="detail-item">
                  <span class="detail-label">ESTADO ACTUAL</span>
                  <span class="badge" [class]="getStatusClass(selectedDispatch()!.status!)">{{ getStatusLabel(selectedDispatch()!.status!) }}</span>
                </div>
                <div class="detail-item">
                  <span class="detail-label">CREADO</span>
                  <span class="detail-value">{{ selectedDispatch()!.createdAt | date:'dd/MM/yyyy HH:mm:ss' }}</span>
                </div>
                <div class="detail-item">
                  <span class="detail-label">ACTUALIZADO</span>
                  <span class="detail-value">{{ selectedDispatch()!.updatedAt | date:'dd/MM/yyyy HH:mm:ss' }}</span>
                </div>
              </div>

              <div class="status-timeline">
                <h4 style="color:var(--text); font-weight:700; margin-bottom:12px">Cambiar Estado</h4>
                <div class="status-buttons">
                  @for (s of statuses; track s) {
                    <button
                      class="status-btn"
                      [class]="getStatusBtnClass(s, selectedDispatch()!.status!)"
                      [disabled]="s === selectedDispatch()!.status"
                      (click)="updateDetailStatus(s)">
                      {{ getStatusIcon(s) }} {{ getStatusLabel(s) }}
                    </button>
                  }
                </div>
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
    .detail-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
      gap: 16px;
    }
    .detail-grid-modal {
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
      font-size: 15px;
    }
    .action-cell {
      display: flex;
      align-items: center;
      gap: 6px;
    }
    .status-select {
      width: auto;
      min-width: 120px;
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
    .status-timeline {
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
export class DespachosComponent implements OnInit {
  dispatches = signal<Dispatch[]>([]);
  filteredDispatches = signal<Dispatch[]>([]);
  searchResult = signal<Dispatch | null>(null);
  selectedDispatch = signal<Dispatch | null>(null);
  message = signal('');
  isError = signal(false);
  activeFilter = signal<DispatchStatus | null>(null);

  searchTracking = '';
  newStatusForSearch: DispatchStatus = 'ENVIADO';
  statusSelections: { [id: string]: DispatchStatus } = {};

  statuses: DispatchStatus[] = ['PREPARANDO', 'ENVIADO', 'EN_CAMINO', 'ENTREGADO', 'FALLIDO', 'CANCELADO'];

  constructor(private despachoService: DespachoService) {}

  ngOnInit() {
    this.loadDispatches();
  }

  loadDispatches() {
    this.despachoService.getAll().subscribe({
      next: (d) => {
        this.dispatches.set(d);
        this.applyFilter();
        d.forEach(dispatch => {
          if (dispatch.id && !this.statusSelections[dispatch.id]) {
            this.statusSelections[dispatch.id] = dispatch.status || 'PREPARANDO';
          }
        });
      },
      error: () => this.showMessage('Error al cargar despachos', true)
    });
  }

  filterByStatus(status: DispatchStatus | null) {
    this.activeFilter.set(status);
    this.applyFilter();
  }

  private applyFilter() {
    const filter = this.activeFilter();
    if (filter) {
      this.filteredDispatches.set(this.dispatches().filter(d => d.status === filter));
    } else {
      this.filteredDispatches.set(this.dispatches());
    }
  }

  countByStatus(status: DispatchStatus): number {
    return this.dispatches().filter(d => d.status === status).length;
  }

  searchByTracking() {
    if (!this.searchTracking.trim()) return;
    this.despachoService.getByTracking(this.searchTracking.trim()).subscribe({
      next: (r) => {
        this.searchResult.set(r);
        this.newStatusForSearch = r.status || 'PREPARANDO';
        this.showMessage('Despacho encontrado', false);
      },
      error: () => {
        this.searchResult.set(null);
        this.showMessage('No se encontró despacho con ese tracking', true);
      }
    });
  }

  clearSearch() {
    this.searchTracking = '';
    this.searchResult.set(null);
  }

  updateStatus(dispatch: Dispatch) {
    const newStatus = this.statusSelections[dispatch.id!];
    if (!newStatus || newStatus === dispatch.status) return;

    this.despachoService.updateStatus(dispatch.id!, newStatus).subscribe({
      next: (updated) => {
        this.showMessage(`Despacho ${updated.trackingNumber} actualizado a ${this.getStatusLabel(newStatus)}`, false);
        this.loadDispatches();
      },
      error: () => this.showMessage('Error al actualizar estado', true)
    });
  }

  updateSearchResultStatus() {
    const dispatch = this.searchResult();
    if (!dispatch || !dispatch.id) return;

    this.despachoService.updateStatus(dispatch.id, this.newStatusForSearch).subscribe({
      next: (updated) => {
        this.searchResult.set(updated);
        this.showMessage(`Estado actualizado a ${this.getStatusLabel(this.newStatusForSearch)}`, false);
        this.loadDispatches();
      },
      error: () => this.showMessage('Error al actualizar estado', true)
    });
  }

  showDetail(dispatch: Dispatch) {
    this.selectedDispatch.set(dispatch);
  }

  closeDetail() {
    this.selectedDispatch.set(null);
  }

  updateDetailStatus(status: DispatchStatus) {
    const dispatch = this.selectedDispatch();
    if (!dispatch || !dispatch.id || status === dispatch.status) return;

    this.despachoService.updateStatus(dispatch.id, status).subscribe({
      next: (updated) => {
        this.selectedDispatch.set(updated);
        this.showMessage(`Estado actualizado a ${this.getStatusLabel(status)}`, false);
        this.loadDispatches();
      },
      error: () => this.showMessage('Error al actualizar estado', true)
    });
  }

  getStatusClass(status: string): string {
    switch (status) {
      case 'ENTREGADO': return 'badge-success';
      case 'ENVIADO': case 'EN_CAMINO': return 'badge-info';
      case 'PREPARANDO': return 'badge-warning';
      case 'FALLIDO': case 'CANCELADO': return 'badge-danger';
      default: return 'badge-pending';
    }
  }

  getStatusLabel(status: string): string {
    switch (status) {
      case 'PREPARANDO': return 'Preparando';
      case 'ENVIADO': return 'Enviado';
      case 'EN_CAMINO': return 'En Camino';
      case 'ENTREGADO': return 'Entregado';
      case 'FALLIDO': return 'Fallido';
      case 'CANCELADO': return 'Cancelado';
      default: return status;
    }
  }

  getStatusIcon(status: string): string {
    switch (status) {
      case 'PREPARANDO': return '⏳';
      case 'ENVIADO': return '📦';
      case 'EN_CAMINO': return '🚛';
      case 'ENTREGADO': return '✅';
      case 'FALLIDO': return '❌';
      case 'CANCELADO': return '🚫';
      default: return '📋';
    }
  }

  getStatusBtnClass(status: string, currentStatus: string): string {
    return status === currentStatus ? 'status-btn status-btn-active' : 'status-btn';
  }

  showMessage(msg: string, error: boolean) {
    this.message.set(msg);
    this.isError.set(error);
    setTimeout(() => this.message.set(''), 4000);
  }
}
