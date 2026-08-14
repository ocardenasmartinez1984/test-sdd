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
          <h1><span>🛒</span> Punto de Venta</h1>
          <div class="search-box">
            <span class="material-icons">search</span>
            <input
              [(ngModel)]="searchTerm"
              placeholder="Buscar producto por nombre o SKU..."
              (input)="filterProducts()">
          </div>
          <div class="user-bar">
            <span class="user-name">{{ authService.currentUser()?.fullName }}</span>
            <button class="btn-logout" (click)="logout()">Salir</button>
          </div>
        </div>

        <div class="product-grid">
          @for (product of filteredProducts(); track product.id) {
            <div class="product-card" (click)="addToCart(product)">
              <div class="product-icon">📦</div>
              <div class="product-name">{{ product.name }}</div>
              <div class="product-sku">{{ product.sku }}</div>
              <div class="product-price">{{ product.price | currency:'USD' }}</div>
              <div class="product-stock" [class.low]="getAvailable(product) <= 3">
                <span class="material-icons" style="font-size:14px">inventory</span>
                {{ getAvailable(product) }} disponibles
              </div>
              @if (getAvailable(product) <= 0) {
                <div class="out-of-stock">SIN STOCK</div>
              }
            </div>
          } @empty {
            <div style="grid-column: 1/-1; text-align:center; padding:60px; color:var(--text-light)">
              No se encontraron productos
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
                  <div class="cart-item-name">{{ item.product.name }}</div>
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
                placeholder="ID del cliente (ej: cliente-001)">
            </div>
            <button
              class="btn-checkout"
              (click)="checkout()"
              [disabled]="processing() || !customerId.trim()">
              @if (processing()) {
                ⏳ Procesando...
              } @else {
                💰 Cobrar {{ cartTotal() | currency:'USD' }}
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
  `
})
export class PosComponent implements OnInit {

  // --- Dependencies ---
  readonly authService = inject(AuthService);
  private readonly stockService = inject(StockService);
  private readonly ventaService = inject(VentaService);
  private readonly router = inject(Router);

  // --- State ---
  readonly products = signal<Product[]>([]);
  readonly filteredProducts = signal<Product[]>([]);
  readonly cartItems = signal<CartItem[]>([]);
  readonly toast = signal('');
  readonly toastError = signal(false);
  readonly processing = signal(false);

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

  // --- Data ---

  loadProducts(): void {
    this.stockService.getAll().subscribe({
      next: (products) => {
        this.products.set(products);
        this.filterProducts();
      },
      error: () => this.showToast('Error al cargar productos', true)
    });
  }

  filterProducts(): void {
    const term = this.searchTerm.toLowerCase().trim();
    if (!term) {
      this.filteredProducts.set(this.products());
    } else {
      this.filteredProducts.set(
        this.products().filter(p =>
          p.name.toLowerCase().includes(term) ||
          p.sku.toLowerCase().includes(term)
        )
      );
    }
  }

  getAvailable(product: Product): number {
    return product.quantity - (product.reservedQuantity || 0);
  }

  // --- Cart Operations ---

  addToCart(product: Product): void {
    if (this.getAvailable(product) <= 0) {
      this.showToast('Producto sin stock', true);
      return;
    }

    const items = [...this.cartItems()];
    const existing = items.find(i => i.product.id === product.id);

    if (existing) {
      if (existing.quantity >= this.getAvailable(product)) {
        this.showToast('No hay más stock disponible', true);
        return;
      }
      existing.quantity++;
    } else {
      items.push({ product, quantity: 1 });
    }

    this.cartItems.set(items);
  }

  increaseQty(item: CartItem): void {
    if (item.quantity >= this.getAvailable(item.product)) {
      this.showToast('Stock máximo alcanzado', true);
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
          if (completed === total) {
            this.onCheckoutComplete();
          }
        },
        error: () => {
          completed++;
          this.showToast(`Error al procesar ${item.product.name}`, true);
          if (completed === total) {
            this.processing.set(false);
          }
        }
      });
    }
  }

  private onCheckoutComplete(): void {
    this.showToast(`✅ Venta completada! ${this.cartTotalItems()} productos procesados`, false);
    this.clearCart();
    this.processing.set(false);
    this.loadProducts(); // Refresh stock
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
    setTimeout(() => this.toast.set(''), 3500);
  }
}
