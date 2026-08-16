import { Component, OnInit, signal, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { StockService } from '../../services/stock.service';
import { VentaService } from '../../services/venta.service';
import { Product, CartItem } from '../../models/models';
import { ChatbotComponent } from '../../components/chatbot/chatbot.component';

@Component({
  selector: 'app-pos',
  standalone: true,
  imports: [CommonModule, FormsModule, ChatbotComponent],
  template: `
    <!-- Toast -->
    @if (toast()) {
      <div class="toast" [class.toast-success]="!toastError()" [class.toast-error]="toastError()">
        {{ toast() }}
      </div>
    }

    <div class="pos-layout">
      <!-- Catálogo -->
      <div class="pos-catalog">
        <div class="pos-header">
          <h1><span>⚡</span> POS Terminal</h1>
          <div class="search-box">
            <span class="material-icons">search</span>
            <input
              [(ngModel)]="searchTerm"
              placeholder="Buscar producto por nombre o SKU..."
              (input)="filterProducts()">
          </div>
          <div class="user-bar">
            <span class="user-name">{{ authService.currentUser()?.fullName }}</span>
            <button class="btn-logout" (click)="logout()">⏻ Salir</button>
          </div>
        </div>

        <!-- Category Filter -->
        <div class="category-bar">
          <button
            class="category-chip"
            [class.active]="selectedCategory() === 'all'"
            (click)="selectCategory('all')">
            🏪 Todos
          </button>
          @for (cat of categories; track cat.id) {
            <button
              class="category-chip"
              [class.active]="selectedCategory() === cat.id"
              (click)="selectCategory(cat.id)">
              {{ cat.icon }} {{ cat.label }}
            </button>
          }
        </div>

        <div class="product-grid">
          @for (product of filteredProducts(); track product.id) {
            <div
              class="product-card"
              [class.added]="lastAdded() === product.id"
              (click)="addToCart(product)">
              <div class="product-icon" [ngClass]="'cat-' + getCategory(product)">
                {{ getCategoryIcon(product) }}
              </div>
              <span class="product-category-tag">{{ getCategoryLabel(product) }}</span>
              <div class="product-name">{{ product.name }}</div>
              <div class="product-sku">{{ product.sku }}</div>
              <div class="product-price">\{{ product.price | currency:'USD' }}</div>
              <div class="product-stock" [class.low]="getAvailable(product) <= 3">
                <span style="font-size:13px">📦</span>
                {{ getAvailable(product) }} disponibles
              </div>
              @if (getAvailable(product) <= 0) {
                <div class="out-of-stock">⛔ SIN STOCK</div>
              }
            </div>
          } @empty {
            <div style="grid-column: 1/-1; text-align:center; padding:80px; color:var(--text-light)">
              <div style="font-size:48px; margin-bottom:16px; opacity:0.3">🔍</div>
              <p>No se encontraron productos</p>
            </div>
          }
        </div>
      </div>

      <!-- Carrito -->
      <div class="pos-sidebar">
        <div class="cart-header">
          <h2>
            <span class="material-icons">shopping_cart</span>
            Carrito
            @if (cartItems().length > 0) {
              <span class="cart-badge">{{ cartTotalItems() }}</span>
            }
          </h2>
        </div>

        @if (cartItems().length > 0) {
          <div class="cart-items">
            @for (item of cartItems(); track item.product.id) {
              <div class="cart-item">
                <div class="cart-item-info">
                  <div class="cart-item-name">{{ getCategoryIcon(item.product) }} {{ item.product.name }}</div>
                  <div class="cart-item-price">{{ item.product.price | currency:'USD' }} c/u</div>
                </div>
                <div class="cart-item-qty">
                  <button (click)="decreaseQty(item)">−</button>
                  <span>{{ item.quantity }}</span>
                  <button (click)="increaseQty(item)">+</button>
                </div>
                <div class="cart-item-total">{{ item.product.price * item.quantity | currency:'USD' }}</div>
                <button class="cart-item-remove" (click)="removeFromCart(item)">✕</button>
              </div>
            }
          </div>

          <div class="cart-footer">
            <div class="cart-totals">
              <div class="cart-total-row">
                <span>Subtotal ({{ cartTotalItems() }} items)</span>
                <span>{{ cartTotal() | currency:'USD' }}</span>
              </div>
              <div class="cart-total-row total">
                <span>TOTAL</span>
                <span>{{ cartTotal() | currency:'USD' }}</span>
              </div>
            </div>
            <div class="cart-customer">
              <input
                [(ngModel)]="customerId"
                placeholder="🧑 ID del cliente (ej: cliente-001)">
            </div>
            <button
              class="btn-checkout"
              (click)="checkout()"
              [disabled]="processing() || !customerId.trim()">
              @if (processing()) {
                ⏳ Procesando...
              } @else {
                💳 Cobrar {{ cartTotal() | currency:'USD' }}
              }
            </button>
            <button class="btn-clear" (click)="clearCart()">🗑️ Vaciar carrito</button>
          </div>
        } @else {
          <div class="cart-empty">
            <span class="material-icons">add_shopping_cart</span>
            <p>Selecciona productos del catálogo</p>
          </div>
        }
      </div>
    </div>

    <!-- Chatbot -->
    <app-chatbot />
  `,
  styles: [`
    .category-bar {
      display: flex;
      gap: 8px;
      padding: 14px 24px;
      overflow-x: auto;
      border-bottom: 1px solid var(--border);
      background: var(--bg-card);
    }
    .category-bar::-webkit-scrollbar {
      height: 0;
    }
    .category-chip {
      padding: 8px 16px;
      border-radius: 20px;
      border: 1px solid var(--border);
      background: transparent;
      color: var(--text-muted);
      font-size: 12px;
      font-weight: 600;
      cursor: pointer;
      white-space: nowrap;
      transition: all 0.2s;
    }
    .category-chip:hover {
      border-color: var(--primary);
      color: var(--primary);
      background: var(--primary-light);
    }
    .category-chip.active {
      background: linear-gradient(135deg, var(--primary), var(--primary-dark));
      color: white;
      border-color: transparent;
      box-shadow: 0 2px 10px var(--primary-glow);
    }
  `]
})
export class PosComponent implements OnInit {

  // --- Dependencies ---
  readonly authService = inject(AuthService);
  private readonly stockService = inject(StockService);
  private readonly ventaService = inject(VentaService);
  private readonly router = inject(Router);

  // --- Category Mapping ---
  readonly categories = [
    { id: 'laptop', icon: '💻', label: 'Laptops' },
    { id: 'monitor', icon: '🖥️', label: 'Monitores' },
    { id: 'mouse', icon: '🖱️', label: 'Mouses' },
    { id: 'teclado', icon: '⌨️', label: 'Teclados' },
    { id: 'auricular', icon: '🎧', label: 'Audio' },
    { id: 'gpu', icon: '🎮', label: 'GPUs' },
    { id: 'ssd', icon: '💾', label: 'Almacenamiento' },
    { id: 'ram', icon: '🧠', label: 'RAM' },
    { id: 'tablet', icon: '📱', label: 'Tablets' },
    { id: 'cable', icon: '🔌', label: 'Cables' },
    { id: 'silla', icon: '🪑', label: 'Mobiliario' },
    { id: 'camara', icon: '📷', label: 'Cámaras' },
  ];

  private readonly categoryMap: Record<string, { icon: string; label: string }> = {
    'laptop': { icon: '💻', label: 'Laptop' },
    'monitor': { icon: '🖥️', label: 'Monitor' },
    'mouse': { icon: '🖱️', label: 'Mouse' },
    'teclado': { icon: '⌨️', label: 'Teclado' },
    'auricular': { icon: '🎧', label: 'Audio' },
    'webcam': { icon: '📹', label: 'Webcam' },
    'ssd': { icon: '💾', label: 'SSD' },
    'ram': { icon: '🧠', label: 'RAM' },
    'gpu': { icon: '🎮', label: 'GPU' },
    'tablet': { icon: '📱', label: 'Tablet' },
    'impresora': { icon: '🖨️', label: 'Impresora' },
    'cable': { icon: '🔌', label: 'Cable' },
    'dock': { icon: '🔗', label: 'Dock' },
    'silla': { icon: '🪑', label: 'Silla' },
    'escritorio': { icon: '🪵', label: 'Escritorio' },
    'ups': { icon: '🔋', label: 'UPS' },
    'switch': { icon: '🌐', label: 'Switch' },
    'router': { icon: '📡', label: 'Router' },
    'nas': { icon: '🗄️', label: 'NAS' },
    'micro': { icon: '🎙️', label: 'Micrófono' },
    'camara': { icon: '📷', label: 'Cámara' },
    'pendrive': { icon: '🔑', label: 'Pendrive' },
    'cargador': { icon: '⚡', label: 'Cargador' },
  };

  // --- State ---
  readonly products = signal<Product[]>([]);
  readonly filteredProducts = signal<Product[]>([]);
  readonly cartItems = signal<CartItem[]>([]);
  readonly toast = signal('');
  readonly toastError = signal(false);
  readonly processing = signal(false);
  readonly lastAdded = signal<string>('');
  readonly selectedCategory = signal<string>('all');

  searchTerm = '';
  customerId = '';

  // --- Computed ---
  readonly cartTotal = computed(() =>
    this.cartItems().reduce((sum, item) => sum + (item.product.price * item.quantity), 0)
  );

  readonly cartTotalItems = computed(() =>
    this.cartItems().reduce((sum, item) => sum + item.quantity, 0)
  );

  // --- Lifecycle ---

  ngOnInit(): void {
    this.loadProducts();
  }

  // --- Category helpers ---

  getCategory(product: Product): string {
    const sku = product.sku.toLowerCase();
    const prefix = sku.split('-')[0];
    return this.categoryMap[prefix] ? prefix : 'default';
  }

  getCategoryIcon(product: Product): string {
    const cat = this.getCategory(product);
    return this.categoryMap[cat]?.icon ?? '📦';
  }

  getCategoryLabel(product: Product): string {
    const cat = this.getCategory(product);
    return this.categoryMap[cat]?.label ?? 'Producto';
  }

  selectCategory(categoryId: string): void {
    this.selectedCategory.set(categoryId);
    this.filterProducts();
  }

  // --- Data ---

  loadProducts(): void {
    this.stockService.getAll().subscribe({
      next: (products) => {
        this.products.set(products);
        this.filterProducts();
      },
      error: () => this.showToast('❌ Error al cargar productos', true)
    });
  }

  filterProducts(): void {
    let result = this.products();
    const term = this.searchTerm.toLowerCase().trim();
    const category = this.selectedCategory();

    if (category !== 'all') {
      result = result.filter(p => this.getCategory(p) === category);
    }

    if (term) {
      result = result.filter(p =>
        p.name.toLowerCase().includes(term) ||
        p.sku.toLowerCase().includes(term)
      );
    }

    this.filteredProducts.set(result);
  }

  getAvailable(product: Product): number {
    return product.quantity - (product.reservedQuantity || 0);
  }

  // --- Cart Operations ---

  addToCart(product: Product): void {
    if (this.getAvailable(product) <= 0) {
      this.showToast('⛔ Producto sin stock', true);
      return;
    }

    const items = [...this.cartItems()];
    const existing = items.find(i => i.product.id === product.id);

    if (existing) {
      if (existing.quantity >= this.getAvailable(product)) {
        this.showToast('⚠️ No hay más stock disponible', true);
        return;
      }
      existing.quantity++;
    } else {
      items.push({ product, quantity: 1 });
    }

    this.cartItems.set(items);

    // Trigger card animation
    this.lastAdded.set(product.id);
    setTimeout(() => this.lastAdded.set(''), 400);

    this.showToast(`✅ ${product.name} agregado`, false);
  }

  increaseQty(item: CartItem): void {
    if (item.quantity >= this.getAvailable(item.product)) {
      this.showToast('⚠️ Stock máximo alcanzado', true);
      return;
    }
    const items = [...this.cartItems()];
    const target = items.find(i => i.product.id === item.product.id);
    if (target) {
      target.quantity++;
      this.cartItems.set(items);
    }
  }

  decreaseQty(item: CartItem): void {
    const items = [...this.cartItems()];
    const target = items.find(i => i.product.id === item.product.id);
    if (target) {
      target.quantity--;
      if (target.quantity <= 0) {
        this.cartItems.set(items.filter(i => i.product.id !== item.product.id));
      } else {
        this.cartItems.set(items);
      }
    }
  }

  removeFromCart(item: CartItem): void {
    this.cartItems.set(this.cartItems().filter(i => i.product.id !== item.product.id));
  }

  clearCart(): void {
    this.cartItems.set([]);
    this.customerId = '';
  }

  // --- Checkout ---

  checkout(): void {
    if (this.cartItems().length === 0 || !this.customerId.trim()) {
      return;
    }

    this.processing.set(true);
    const items = this.cartItems();
    let completed = 0;
    let errors = 0;
    const total = items.length;

    for (const item of items) {
      const order = {
        customerId: this.customerId.trim(),
        productId: item.product.id,
        quantity: item.quantity,
        totalAmount: item.product.price * item.quantity
      };

      this.ventaService.create(order).subscribe({
        next: () => {
          completed++;
          if (completed + errors === total) {
            this.onCheckoutComplete(completed, errors);
          }
        },
        error: () => {
          errors++;
          if (completed + errors === total) {
            this.onCheckoutComplete(completed, errors);
          }
        }
      });
    }
  }

  private onCheckoutComplete(success: number, errors: number): void {
    if (errors === 0) {
      this.showToast(`🎉 ¡Venta completada! ${this.cartTotalItems()} productos procesados`, false);
    } else {
      this.showToast(`⚠️ ${success} procesados, ${errors} con error`, true);
    }
    this.clearCart();
    this.processing.set(false);
    this.loadProducts();
  }

  // --- Auth ---

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  // --- Toast ---

  private showToast(message: string, isError: boolean): void {
    this.toast.set(message);
    this.toastError.set(isError);
    setTimeout(() => this.toast.set(''), 3000);
  }
}
