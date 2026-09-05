import { Component, OnInit, signal, computed, inject, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { StockService } from '../../services/stock.service';
import { VentaService } from '../../services/venta.service';
import { CartService } from '../../services/cart.service';
import { Product, CartItem } from '../../models/models';
import { CategoryIconComponent } from '../../components/category-icon/category-icon.component';

@Component({
  selector: 'app-pos',
  standalone: true,
  imports: [CommonModule, FormsModule, CategoryIconComponent],
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
          <h1>
            <span class="logo-icon">
              <app-category-icon category="cargador" [size]="32"></app-category-icon>
            </span>
            POS Terminal
          </h1>
          <div class="search-box">
            <span class="material-icons">search</span>
            <input
              [(ngModel)]="searchTerm"
              placeholder="Buscar producto por nombre o SKU..."
              (input)="filterProducts()">
          </div>
          <div class="user-bar">
            <span class="user-name">{{ authService.currentUser()?.fullName }}</span>
            <button class="btn-logout" (click)="logout()">
              <span class="material-icons" style="font-size:16px;vertical-align:middle">power_settings_new</span>
              Salir
            </button>
          </div>
        </div>

        <!-- Category Filter -->
        <div class="category-bar">
          <button
            class="category-chip"
            [class.active]="selectedCategory() === 'all'"
            (click)="selectCategory('all')">
            <span class="chip-icon"><app-category-icon category="default" [size]="15"></app-category-icon></span>
            <span>Todos</span>
          </button>
          @for (cat of categories; track cat.id) {
            <button
              class="category-chip"
              [class.active]="selectedCategory() === cat.id"
              (click)="selectCategory(cat.id)">
              <span class="chip-icon"><app-category-icon [category]="cat.id" [size]="15"></app-category-icon></span>
              <span>{{ cat.label }}</span>
            </button>
          }
        </div>

        <div class="product-grid">
          @for (product of filteredProducts(); track product.id) {
            <div
              class="product-card"
              [class.added]="lastAdded() === product.id"
              (click)="addToCart(product)">
              <div class="product-card-top">
                <div class="product-icon" [ngClass]="'cat-' + getCategory(product)">
                  <app-category-icon [category]="getCategory(product)" [size]="32"></app-category-icon>
                </div>
                <span class="product-category-tag">{{ getCategoryLabel(product) }}</span>
              </div>
              <div class="product-name">{{ product.name }}</div>
              <div class="product-sku">SKU: {{ product.sku }}</div>
              <div class="product-card-bottom">
                <div class="product-price">{{ product.price | currency:'USD' }}</div>
                <div class="product-stock" [class.low]="getAvailable(product) <= 3">
                  <span class="material-icons" style="font-size:14px">inventory_2</span>
                  {{ getAvailable(product) }} disp.
                </div>
              </div>
              @if (getAvailable(product) <= 0) {
                <div class="out-of-stock">
                  <span class="material-icons" style="font-size:32px;display:block;margin-bottom:8px">block</span>
                  SIN STOCK
                </div>
              }
              <div class="product-add-hint">
                <span class="material-icons">add_shopping_cart</span>
                Agregar
              </div>
            </div>
          } @empty {
            @if (loading()) {
              <div class="empty-state">
                <span class="catalog-spinner"></span>
                <p>Cargando productos...</p>
              </div>
            } @else {
              <div class="empty-state">
                <div class="empty-icon">
                  <app-category-icon category="default" [size]="64"></app-category-icon>
                </div>
                <p>No se encontraron productos</p>
              </div>
            }
          }
        </div>
      </div>

      <!-- Floating Cart Toggle Button -->
      <button class="cart-toggle-btn" (click)="toggleCart()" [class.has-items]="cartItems().length > 0">
        <span class="material-icons">{{ cartOpen() ? 'close' : 'shopping_cart' }}</span>
        @if (cartItems().length > 0 && !cartOpen()) {
          <span class="cart-toggle-badge">{{ cartTotalItems() }}</span>
        }
      </button>

      <!-- Carrito Flotante -->
      <div class="pos-sidebar" [class.cart-open]="cartOpen()">
        <div class="cart-header">
          <h2>
            <span class="material-icons">shopping_cart</span>
            Carrito
            @if (cartItems().length > 0) {
              <span class="cart-badge">{{ cartTotalItems() }}</span>
            }
          </h2>
          <button class="cart-close-btn" (click)="toggleCart()">
            <span class="material-icons">close</span>
          </button>
        </div>

        @if (cartItems().length > 0) {
          <div class="cart-items">
            @for (item of cartItems(); track item.product.id) {
              <div class="cart-item">
                <div class="cart-item-main">
                  <div class="cart-item-thumb" [ngClass]="'cat-' + getCategory(item.product)">
                    <app-category-icon [category]="getCategory(item.product)" [size]="24"></app-category-icon>
                  </div>
                  <div class="cart-item-info">
                    <div class="cart-item-name">{{ item.product.name }}</div>
                    <div class="cart-item-price">{{ item.product.price | currency:'USD' }} c/u</div>
                  </div>
                  <button class="cart-item-remove" (click)="removeFromCart(item)">
                    <span class="material-icons" style="font-size:16px">close</span>
                  </button>
                </div>
                <div class="cart-item-actions">
                  <div class="cart-item-qty">
                    <button (click)="decreaseQty(item)">−</button>
                    <span>{{ item.quantity }}</span>
                    <button (click)="increaseQty(item)">+</button>
                  </div>
                  <div class="cart-item-total">{{ item.product.price * item.quantity | currency:'USD' }}</div>
                </div>
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
              <span class="material-icons customer-icon">person</span>
              <input
                [(ngModel)]="customerId"
                placeholder="ID del cliente (ej: cliente-001)">
            </div>
            <button
              class="btn-checkout"
              (click)="checkout()"
              [disabled]="processing() || !customerId.trim()">
              @if (processing()) {
                <span class="spinner"></span>
                Procesando...
              } @else {
                <span class="material-icons">credit_card</span>
                Cobrar {{ cartTotal() | currency:'USD' }}
              }
            </button>
            <button class="btn-clear" (click)="clearCart()">
              <span class="material-icons" style="font-size:16px;vertical-align:middle">delete_sweep</span>
              Vaciar carrito
            </button>
          </div>
        } @else {
          <div class="cart-empty">
            <div class="cart-empty-icon">
              <span class="material-icons">add_shopping_cart</span>
            </div>
            <p>Selecciona productos del catálogo</p>
          </div>
        }
      </div>
    </div>
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
  private readonly cartService = inject(CartService);
  private readonly router = inject(Router);

  // --- Session ---
  private sessionId = this.getOrCreateSessionId();

  private getOrCreateSessionId(): string {
    let id = localStorage.getItem('pos_session_id');
    if (!id) {
      id = 'cart-' + Date.now() + '-' + Math.random().toString(36).substring(7);
      localStorage.setItem('pos_session_id', id);
    }
    return id;
  }

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
  readonly cartOpen = signal(false);
  readonly loading = signal(true);

  searchTerm = '';

  // Customer id is persisted so it survives page reloads / cart sync.
  private _customerId = localStorage.getItem('pos_customer_id') ?? '';
  get customerId(): string { return this._customerId; }
  set customerId(value: string) {
    this._customerId = value;
    if (value) {
      localStorage.setItem('pos_customer_id', value);
    } else {
      localStorage.removeItem('pos_customer_id');
    }
  }

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
        this.syncCartFromServer();
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.showToast('❌ Error al cargar productos', true);
      }
    });
  }

  syncCartFromServer(): void {
    const savedSessionId = localStorage.getItem('pos_session_id');
    if (!savedSessionId) return;

    this.cartService.getCart(savedSessionId).subscribe({
      next: (serverItems) => {
        const catalog = this.products();
        const localCartItems: CartItem[] = [];

        serverItems.forEach(item => {
          const product = catalog.find(p => p.id === item.productId);
          if (product) {
            localCartItems.push({
              product,
              quantity: item.quantity
            });
            // NOTE: product.reservedQuantity already comes from the stock
            // service and includes this session's reservations, so we must
            // NOT add item.quantity again here (that double-counted the
            // available stock).
          }
        });

        if (localCartItems.length > 0) {
          this.cartItems.set(localCartItems);
          this.filterProducts();
          this.cartOpen.set(true);
        }
      },
      error: (err) => console.warn('No se pudo sincronizar el carrito:', err)
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

    // Update local reserved quantity so UI reflects immediately
    const updatedProducts = this.products().map(p =>
      p.id === product.id ? { ...p, reservedQuantity: (p.reservedQuantity || 0) + 1 } : p
    );
    this.products.set(updatedProducts);
    this.filterProducts();

    // Open cart and trigger card animation
    this.cartOpen.set(true);
    this.lastAdded.set(product.id);
    setTimeout(() => this.lastAdded.set(''), 400);

    // Reserve stock via cart API
    this.cartService.addToCart({
      sessionId: this.sessionId,
      productId: product.id,
      quantity: 1,
      unitPrice: product.price
    }).subscribe({
      next: () => this.showToast(`✅ ${product.name} reservado`, false),
      error: () => {
        // Revert local reservation on error
        const revertProducts = this.products().map(p =>
          p.id === product.id ? { ...p, reservedQuantity: (p.reservedQuantity || 1) - 1 } : p
        );
        this.products.set(revertProducts);
        this.filterProducts();
        this.showToast('⚠️ Error al reservar stock', true);
      }
    });
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

      // Update local reservation
      const updatedProducts = this.products().map(p =>
        p.id === item.product.id ? { ...p, reservedQuantity: (p.reservedQuantity || 0) + 1 } : p
      );
      this.products.set(updatedProducts);
      this.filterProducts();

      // Reserve stock for the new total via cart API (set absolute quantity)
      this.cartService.setQuantity({
        sessionId: this.sessionId,
        productId: item.product.id,
        quantity: target.quantity,
        unitPrice: item.product.price
      }).subscribe({
        next: () => {},
        error: () => this.showToast('⚠️ Error al reservar stock', true)
      });
    }
  }

  decreaseQty(item: CartItem): void {
    const items = [...this.cartItems()];
    const target = items.find(i => i.product.id === item.product.id);
    if (target) {
      target.quantity--;

      // Release local reservation
      const updatedProducts = this.products().map(p =>
        p.id === item.product.id ? { ...p, reservedQuantity: Math.max(0, (p.reservedQuantity || 1) - 1) } : p
      );
      this.products.set(updatedProducts);
      this.filterProducts();

      if (target.quantity <= 0) {
        this.cartItems.set(items.filter(i => i.product.id !== item.product.id));
        this.cartService.removeFromCart(this.sessionId, item.product.id).subscribe();
      } else {
        this.cartItems.set(items);
        // Persist the reduced quantity so the stock reservation reflects it.
        this.cartService.setQuantity({
          sessionId: this.sessionId,
          productId: item.product.id,
          quantity: target.quantity,
          unitPrice: item.product.price
        }).subscribe({
          next: () => {},
          error: () => this.showToast('⚠️ Error al actualizar stock', true)
        });
      }
    }
  }

  removeFromCart(item: CartItem): void {
    // Release all reserved quantity for this item
    const updatedProducts = this.products().map(p =>
      p.id === item.product.id ? { ...p, reservedQuantity: Math.max(0, (p.reservedQuantity || 0) - item.quantity) } : p
    );
    this.products.set(updatedProducts);
    this.filterProducts();

    this.cartItems.set(this.cartItems().filter(i => i.product.id !== item.product.id));
    this.cartService.removeFromCart(this.sessionId, item.product.id).subscribe();
  }

  clearCart(): void {
    const items = this.cartItems();
    const updatedProducts = this.products().map(p => {
      const cartItem = items.find(i => i.product.id === p.id);
      if (cartItem) {
        return { ...p, reservedQuantity: Math.max(0, (p.reservedQuantity || 0) - cartItem.quantity) };
      }
      return p;
    });
    this.products.set(updatedProducts);
    this.filterProducts();

    this.cartItems.set([]);
    this.customerId = '';
    this.cartService.clearCart(this.sessionId).subscribe();
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

    // Release the cart's own stock reservations. When the cart is closed, the
    // items were reserved twice: once by the cart (cart-item id) and again by
    // the sales order created at checkout. If we don't release the cart-side
    // reservations, reservedQuantity stays inflated and the stock becomes
    // inconsistent. clearCart emits a compensate event per cart item, subtracting
    // the products that were not actually sold through the order flow.
    const closingSessionId = this.sessionId;
    this.cartService.clearCart(closingSessionId).subscribe({
      next: () => this.loadProducts(),
      error: (err) => {
        console.warn('No se pudo liberar el carrito al cerrar:', err);
        this.loadProducts();
      }
    });

    this.cartItems.set([]);
    this.customerId = '';
    // Generate new sessionId for the next transaction
    this.sessionId = 'cart-' + Date.now() + '-' + Math.random().toString(36).substring(7);
    localStorage.setItem('pos_session_id', this.sessionId);
    this.processing.set(false);
  }

  // --- Keyboard Shortcuts ---
  @HostListener('document:keydown', ['$event'])
  handleKeyboardEvent(event: KeyboardEvent) {
    // F2 to focus search box
    if (event.key === 'F2') {
      event.preventDefault();
      const searchInput = document.querySelector('.search-box input') as HTMLInputElement;
      if (searchInput) {
        searchInput.focus();
        searchInput.select();
      }
    }
    // Ctrl + Enter to process checkout (Cobrar)
    if (event.ctrlKey && event.key === 'Enter') {
      event.preventDefault();
      if (this.cartItems().length > 0 && this.customerId.trim() && !this.processing()) {
        this.checkout();
      }
    }
    // Escape to clear cart or unfocus
    if (event.key === 'Escape') {
      const activeEl = document.activeElement as HTMLElement;
      if (activeEl && (activeEl.tagName === 'INPUT' || activeEl.tagName === 'BUTTON')) {
        activeEl.blur();
      } else if (this.cartItems().length > 0) {
        if (confirm('¿Vaciar el carrito de compra?')) {
          this.clearCart();
        }
      }
    }
  }

  // --- Cart Toggle ---

  toggleCart(): void {
    const willClose = this.cartOpen();
    this.cartOpen.set(!willClose);
    // When closing the cart, refresh the catalog so stock/availability
    // reflects the current reservations on the server (without reopening it).
    if (willClose) {
      this.refreshStock();
    }
  }

  /** Reloads the catalog stock without re-syncing/reopening the cart. */
  private refreshStock(): void {
    this.stockService.getAll().subscribe({
      next: (products) => {
        this.products.set(products);
        this.filterProducts();
      },
      error: () => this.showToast('⚠️ No se pudo actualizar el stock', true)
    });
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
