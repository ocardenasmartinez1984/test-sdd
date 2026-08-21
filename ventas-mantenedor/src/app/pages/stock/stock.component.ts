import { Component, OnInit, OnDestroy, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { StockService } from '../../services/stock.service';
import { Product } from '../../models/models';

@Component({
  selector: 'app-stock',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="container">
      <div class="page-header">
        <h2>📦 Gestión de Stock</h2>
        <button class="btn btn-primary" (click)="openModal()">
          <span class="material-icons" style="font-size:18px">add</span> Nuevo Producto
        </button>
      </div>

      @if (message()) {
        <div class="alert" [class.alert-success]="!isError()" [class.alert-error]="isError()">{{ message() }}</div>
      }

      <!-- Stats -->
      <div class="stats-grid">
        <div class="stat-card">
          <span class="stat-icon">📋</span>
          <div class="stat-info">
            <span class="stat-value">{{ products().length }}</span>
            <span class="stat-label">Productos</span>
          </div>
        </div>
        <div class="stat-card">
          <span class="stat-icon">📦</span>
          <div class="stat-info">
            <span class="stat-value">{{ totalStock() }}</span>
            <span class="stat-label">Stock Total</span>
          </div>
        </div>
        <div class="stat-card">
          <span class="stat-icon">🔒</span>
          <div class="stat-info">
            <span class="stat-value">{{ totalReserved() }}</span>
            <span class="stat-label">Reservado</span>
          </div>
        </div>
      </div>

      <div class="card">
        <table>
          <thead>
            <tr>
              <th>SKU</th>
              <th>Nombre</th>
              <th>Stock</th>
              <th>Reservado</th>
              <th>Disponible</th>
              <th>Precio</th>
              <th>Acciones</th>
            </tr>
          </thead>
          <tbody>
            @for (product of products(); track product.id) {
              <tr>
                <td><code>{{ product.sku }}</code></td>
                <td>{{ product.name }}</td>
                <td>{{ product.quantity }}</td>
                <td><span class="badge badge-warning">{{ product.reservedQuantity || 0 }}</span></td>
                <td><span class="badge badge-success">{{ product.quantity - (product.reservedQuantity || 0) }}</span></td>
                <td>{{ product.price | currency:'USD' }}</td>
                <td><button class="btn btn-secondary" (click)="editProduct(product)">✏️</button></td>
              </tr>
            } @empty {
              <tr><td colspan="7" style="text-align:center; padding:60px; color:var(--text-muted)">No hay productos</td></tr>
            }
          </tbody>
        </table>
      </div>

      @if (showModal()) {
        <div class="modal-overlay" (click)="closeModal()">
          <div class="modal-content" (click)="$event.stopPropagation()">
            <h3>{{ editing() ? '✏️ Editar Producto' : '➕ Nuevo Producto' }}</h3>
            <div class="form-group"><label>SKU</label><input [(ngModel)]="form.sku" placeholder="PROD-001"></div>
            <div class="form-group"><label>Nombre</label><input [(ngModel)]="form.name" placeholder="Nombre del producto"></div>
            <div class="form-group"><label>Cantidad</label><input type="number" [(ngModel)]="form.quantity"></div>
            <div class="form-group"><label>Precio</label><input type="number" step="0.01" [(ngModel)]="form.price"></div>
            <div class="modal-actions">
              <button class="btn btn-secondary" (click)="closeModal()">Cancelar</button>
              <button class="btn btn-primary" (click)="save()">{{ editing() ? 'Actualizar' : 'Crear' }}</button>
            </div>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .stats-grid {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 16px;
      margin-bottom: 24px;
    }
    .stat-card {
      background: var(--bg-card);
      border: 1px solid var(--border);
      border-radius: var(--radius);
      padding: 20px 24px;
      display: flex;
      align-items: center;
      gap: 16px;
      transition: all 0.3s;
      animation: fadeInUp 0.5s ease;
    }
    .stat-card:nth-child(2) { animation-delay: 0.1s; }
    .stat-card:nth-child(3) { animation-delay: 0.2s; }
    .stat-card:hover {
      border-color: var(--primary);
      transform: translateY(-3px);
      box-shadow: 0 8px 30px rgba(99,102,241,0.15);
    }
    .stat-icon { font-size: 32px; }
    .stat-value { display: block; font-size: 24px; font-weight: 800; color: var(--text); }
    .stat-label { font-size: 12px; color: var(--text-muted); text-transform: uppercase; letter-spacing: 1px; }
  `]
})
export class StockComponent implements OnInit, OnDestroy {
  products = signal<Product[]>([]);
  showModal = signal(false);
  editing = signal(false);
  message = signal('');
  isError = signal(false);
  editingId = '';
  form: Product = { sku: '', name: '', quantity: 0, price: 0 };
  private pollInterval: any;

  constructor(private stockService: StockService) {}

  ngOnInit() {
    this.loadProducts();
    this.pollInterval = setInterval(() => this.loadProducts(), 5000);
  }

  ngOnDestroy() {
    if (this.pollInterval) clearInterval(this.pollInterval);
  }

  totalStock() { return this.products().reduce((sum, p) => sum + p.quantity, 0); }
  totalReserved() { return this.products().reduce((sum, p) => sum + (p.reservedQuantity || 0), 0); }

  loadProducts() {
    this.stockService.getAll().subscribe({ next: (d) => this.products.set(d), error: () => this.showMessage('Error al cargar', true) });
  }
  openModal() { this.form = { sku: '', name: '', quantity: 0, price: 0 }; this.editing.set(false); this.showModal.set(true); }
  editProduct(p: Product) { this.form = { ...p }; this.editingId = p.id!; this.editing.set(true); this.showModal.set(true); }
  closeModal() { this.showModal.set(false); }
  save() {
    const obs = this.editing() ? this.stockService.update(this.editingId, this.form) : this.stockService.create(this.form);
    obs.subscribe({ next: () => { this.showMessage(this.editing() ? 'Actualizado' : 'Creado', false); this.closeModal(); this.loadProducts(); }, error: () => this.showMessage('Error', true) });
  }
  showMessage(msg: string, error: boolean) { this.message.set(msg); this.isError.set(error); setTimeout(() => this.message.set(''), 4000); }
}
