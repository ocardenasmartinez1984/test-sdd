import { Component, signal, ViewChild, ElementRef, AfterViewChecked } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

export interface ChatMessage {
  role: 'user' | 'bot';
  text: string;
  timestamp: Date;
}

@Component({
  selector: 'app-chatbot',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <!-- Floating Button -->
    @if (!isOpen()) {
      <button class="chat-fab" (click)="toggle()">
        <span class="chat-fab-icon">💬</span>
        @if (unread()) {
          <span class="chat-fab-badge">{{ unread() }}</span>
        }
      </button>
    }

    <!-- Chat Window -->
    @if (isOpen()) {
      <div class="chat-window">
        <div class="chat-header">
          <div class="chat-header-info">
            <div class="chat-avatar">🤖</div>
            <div>
              <div class="chat-title">Asistente de Ventas</div>
              <div class="chat-status">En línea</div>
            </div>
          </div>
          <button class="chat-close" (click)="toggle()">✕</button>
        </div>

        <div class="chat-messages" #messagesContainer>
          @for (msg of messages(); track $index) {
            <div class="chat-bubble" [class.chat-bubble-user]="msg.role === 'user'" [class.chat-bubble-bot]="msg.role === 'bot'">
              @if (msg.role === 'bot') {
                <div class="chat-bubble-avatar">🤖</div>
              }
              <div class="chat-bubble-content">
                <div class="chat-bubble-text" [innerHTML]="formatMessage(msg.text)"></div>
                <div class="chat-bubble-time">{{ msg.timestamp | date:'HH:mm' }}</div>
              </div>
            </div>
          }
          @if (typing()) {
            <div class="chat-bubble chat-bubble-bot">
              <div class="chat-bubble-avatar">🤖</div>
              <div class="chat-bubble-content">
                <div class="chat-typing">
                  <span></span><span></span><span></span>
                </div>
              </div>
            </div>
          }
        </div>

        <div class="chat-quick-actions">
          @for (action of quickActions; track action) {
            <button class="quick-action" (click)="sendQuickAction(action)">{{ action }}</button>
          }
        </div>

        <div class="chat-input-area">
          <input
            [(ngModel)]="userInput"
            placeholder="Escribe tu consulta..."
            (keyup.enter)="sendMessage()"
            [disabled]="typing()">
          <button class="chat-send" (click)="sendMessage()" [disabled]="!userInput.trim() || typing()">
            <span class="material-icons">send</span>
          </button>
        </div>
      </div>
    }
  `,
  styles: [`
    .chat-fab {
      position: fixed;
      bottom: 24px;
      right: 24px;
      width: 60px;
      height: 60px;
      border-radius: 50%;
      background: linear-gradient(135deg, #10b981, #059669);
      border: none;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      box-shadow: 0 6px 24px rgba(16, 185, 129, 0.4);
      transition: all 0.3s;
      z-index: 9999;
      animation: bounceIn 0.5s ease;
    }
    .chat-fab:hover {
      transform: scale(1.1);
      box-shadow: 0 8px 30px rgba(16, 185, 129, 0.5);
    }
    .chat-fab-icon {
      font-size: 26px;
    }
    .chat-fab-badge {
      position: absolute;
      top: -4px;
      right: -4px;
      background: #ef4444;
      color: white;
      font-size: 11px;
      font-weight: 700;
      width: 22px;
      height: 22px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .chat-window {
      position: fixed;
      bottom: 24px;
      right: 24px;
      width: 380px;
      height: 560px;
      background: white;
      border-radius: 20px;
      box-shadow: 0 20px 60px rgba(0,0,0,0.15);
      display: flex;
      flex-direction: column;
      overflow: hidden;
      z-index: 9999;
      animation: slideUp 0.3s ease;
      border: 1px solid #e2e8f0;
    }

    .chat-header {
      background: linear-gradient(135deg, #10b981, #059669);
      color: white;
      padding: 16px 20px;
      display: flex;
      align-items: center;
      justify-content: space-between;
    }
    .chat-header-info {
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .chat-avatar {
      width: 38px;
      height: 38px;
      border-radius: 50%;
      background: rgba(255,255,255,0.2);
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 20px;
    }
    .chat-title {
      font-size: 15px;
      font-weight: 700;
    }
    .chat-status {
      font-size: 11px;
      opacity: 0.8;
      display: flex;
      align-items: center;
      gap: 4px;
    }
    .chat-status::before {
      content: '';
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background: #a7f3d0;
      display: inline-block;
    }
    .chat-close {
      width: 32px;
      height: 32px;
      border-radius: 50%;
      border: none;
      background: rgba(255,255,255,0.2);
      color: white;
      font-size: 16px;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all 0.2s;
    }
    .chat-close:hover {
      background: rgba(255,255,255,0.3);
    }

    .chat-messages {
      flex: 1;
      overflow-y: auto;
      padding: 16px;
      display: flex;
      flex-direction: column;
      gap: 12px;
      background: #f8fafc;
    }

    .chat-bubble {
      display: flex;
      gap: 8px;
      max-width: 85%;
      animation: fadeIn 0.3s ease;
    }
    .chat-bubble-user {
      align-self: flex-end;
      flex-direction: row-reverse;
    }
    .chat-bubble-bot {
      align-self: flex-start;
    }
    .chat-bubble-avatar {
      width: 28px;
      height: 28px;
      border-radius: 50%;
      background: #d1fae5;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 14px;
      flex-shrink: 0;
    }
    .chat-bubble-content {
      padding: 10px 14px;
      border-radius: 16px;
      font-size: 13px;
      line-height: 1.5;
    }
    .chat-bubble-bot .chat-bubble-content {
      background: white;
      color: #1e293b;
      border: 1px solid #e2e8f0;
      border-bottom-left-radius: 4px;
    }
    .chat-bubble-user .chat-bubble-content {
      background: linear-gradient(135deg, #10b981, #059669);
      color: white;
      border-bottom-right-radius: 4px;
    }
    .chat-bubble-time {
      font-size: 10px;
      opacity: 0.6;
      margin-top: 4px;
    }

    .chat-typing {
      display: flex;
      gap: 4px;
      padding: 4px 0;
    }
    .chat-typing span {
      width: 7px;
      height: 7px;
      border-radius: 50%;
      background: #94a3b8;
      animation: typingDot 1.4s infinite;
    }
    .chat-typing span:nth-child(2) { animation-delay: 0.2s; }
    .chat-typing span:nth-child(3) { animation-delay: 0.4s; }

    .chat-quick-actions {
      padding: 8px 16px;
      display: flex;
      gap: 6px;
      flex-wrap: wrap;
      border-top: 1px solid #f1f5f9;
      background: white;
    }
    .quick-action {
      padding: 6px 12px;
      border-radius: 20px;
      border: 1px solid #e2e8f0;
      background: #f8fafc;
      color: #475569;
      font-size: 11px;
      font-weight: 500;
      cursor: pointer;
      transition: all 0.2s;
      white-space: nowrap;
    }
    .quick-action:hover {
      background: #d1fae5;
      border-color: #10b981;
      color: #059669;
    }

    .chat-input-area {
      padding: 12px 16px;
      display: flex;
      gap: 8px;
      border-top: 1px solid #e2e8f0;
      background: white;
    }
    .chat-input-area input {
      flex: 1;
      padding: 10px 16px;
      border: 1px solid #e2e8f0;
      border-radius: 24px;
      font-size: 13px;
      outline: none;
      transition: all 0.2s;
      background: #f8fafc;
    }
    .chat-input-area input:focus {
      border-color: #10b981;
      box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.1);
      background: white;
    }
    .chat-send {
      width: 40px;
      height: 40px;
      border-radius: 50%;
      border: none;
      background: linear-gradient(135deg, #10b981, #059669);
      color: white;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all 0.2s;
    }
    .chat-send:hover:not(:disabled) {
      transform: scale(1.05);
    }
    .chat-send:disabled {
      opacity: 0.4;
      cursor: not-allowed;
    }
    .chat-send .material-icons {
      font-size: 18px;
    }

    @keyframes bounceIn {
      0% { transform: scale(0); }
      50% { transform: scale(1.15); }
      100% { transform: scale(1); }
    }
    @keyframes slideUp {
      from { opacity: 0; transform: translateY(20px); }
      to { opacity: 1; transform: translateY(0); }
    }
    @keyframes fadeIn {
      from { opacity: 0; transform: translateY(8px); }
      to { opacity: 1; transform: translateY(0); }
    }
    @keyframes typingDot {
      0%, 60%, 100% { transform: translateY(0); opacity: 0.4; }
      30% { transform: translateY(-4px); opacity: 1; }
    }
  `]
})
export class ChatbotComponent implements AfterViewChecked {
  @ViewChild('messagesContainer') messagesContainer!: ElementRef;

  isOpen = signal(false);
  messages = signal<ChatMessage[]>([]);
  typing = signal(false);
  unread = signal(0);

  userInput = '';

  quickActions = ['¿Qué productos hay?', '¿Cómo vendo?', 'Problemas de stock', 'Ayuda'];

  private shouldScroll = false;

  constructor() {
    // Mensaje de bienvenida
    this.messages.set([{
      role: 'bot',
      text: '¡Hola! 👋 Soy tu asistente de ventas. Puedo ayudarte con:\n\n• Información de productos y precios\n• Cómo realizar una venta\n• Consultas de stock\n• Problemas frecuentes\n\n¿En qué te puedo ayudar?',
      timestamp: new Date()
    }]);
  }

  ngAfterViewChecked(): void {
    if (this.shouldScroll) {
      this.scrollToBottom();
      this.shouldScroll = false;
    }
  }

  toggle(): void {
    this.isOpen.set(!this.isOpen());
    if (this.isOpen()) {
      this.unread.set(0);
      this.shouldScroll = true;
    }
  }

  sendMessage(): void {
    const text = this.userInput.trim();
    if (!text) return;

    this.addMessage('user', text);
    this.userInput = '';
    this.typing.set(true);
    this.shouldScroll = true;

    // Simular respuesta del bot
    setTimeout(() => {
      const response = this.generateResponse(text);
      this.addMessage('bot', response);
      this.typing.set(false);
      this.shouldScroll = true;

      if (!this.isOpen()) {
        this.unread.set(this.unread() + 1);
      }
    }, 800 + Math.random() * 700);
  }

  sendQuickAction(action: string): void {
    this.userInput = action;
    this.sendMessage();
  }

  formatMessage(text: string): string {
    return text
      .replace(/\n/g, '<br>')
      .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
      .replace(/•/g, '&bull;');
  }

  private addMessage(role: 'user' | 'bot', text: string): void {
    const msgs = [...this.messages()];
    msgs.push({ role, text, timestamp: new Date() });
    this.messages.set(msgs);
  }

  private generateResponse(input: string): string {
    const lower = input.toLowerCase();

    if (lower.includes('producto') || lower.includes('catálogo') || lower.includes('catalogo') || lower.includes('qué hay') || lower.includes('que hay')) {
      return '📦 Los productos disponibles están en el **catálogo a la izquierda**.\n\nCada tarjeta muestra:\n• Nombre del producto\n• Precio unitario\n• Stock disponible\n\nHaz **click en una tarjeta** para agregar el producto al carrito. ¡Es así de fácil!';
    }

    if (lower.includes('vendo') || lower.includes('vender') || lower.includes('venta') || lower.includes('cómo') || lower.includes('como se')) {
      return '🛒 Para realizar una venta:\n\n**1.** Haz click en los productos del catálogo\n**2.** Ajusta cantidades con los botones + / −\n**3.** Ingresa el ID del cliente\n**4.** Presiona el botón **"💰 Cobrar"**\n\nLa venta se procesa automáticamente mediante el sistema SAGA (reserva stock → genera despacho).';
    }

    if (lower.includes('stock') || lower.includes('inventario') || lower.includes('disponible')) {
      return '📊 Sobre el stock:\n\n• El stock disponible se muestra en cada tarjeta de producto\n• Si dice **"SIN STOCK"** no se puede vender ese producto\n• Cuando se realiza una venta, el stock se reserva automáticamente\n• Si la venta falla, el stock se libera (compensación SAGA)\n\nSi un producto aparece con stock bajo (en rojo), queda poco inventario.';
    }

    if (lower.includes('error') || lower.includes('problema') || lower.includes('falla') || lower.includes('no funciona')) {
      return '🔧 Problemas comunes:\n\n• **"Sin stock"**: El producto no tiene unidades disponibles\n• **Error al cobrar**: Verifica que ingresaste el ID del cliente\n• **Producto no aparece**: Usa la barra de búsqueda para filtrar\n• **Venta en proceso**: Espera unos segundos, la SAGA está coordinando los servicios\n\nSi el problema persiste, contacta al administrador.';
    }

    if (lower.includes('precio') || lower.includes('costo') || lower.includes('vale') || lower.includes('cuánto') || lower.includes('cuanto')) {
      return '💰 Los precios se muestran en cada tarjeta del catálogo en **USD**.\n\nEl total se calcula automáticamente en el carrito según la cantidad seleccionada. No se aplican descuentos desde este punto de venta.';
    }

    if (lower.includes('cliente') || lower.includes('id')) {
      return '👤 El **ID del cliente** es un identificador que se ingresa antes de cobrar.\n\nPuede ser:\n• Un código interno (ej: "cliente-001")\n• Nombre del cliente\n• RUT o documento\n\nEste campo es obligatorio para procesar la venta.';
    }

    if (lower.includes('carrito') || lower.includes('eliminar') || lower.includes('quitar') || lower.includes('vaciar')) {
      return '🗑️ Para gestionar el carrito:\n\n• **Quitar 1 unidad**: Botón **−** en el item\n• **Agregar 1 unidad**: Botón **+** en el item\n• **Eliminar producto**: Botón **✕** al lado del item\n• **Vaciar todo**: Botón "Vaciar carrito" al final\n\nEl total se actualiza en tiempo real.';
    }

    if (lower.includes('hola') || lower.includes('hi') || lower.includes('buenas') || lower.includes('hey')) {
      return '¡Hola! 👋 ¿En qué te puedo ayudar hoy? Puedo asistirte con ventas, productos, stock o cualquier duda del sistema.';
    }

    if (lower.includes('gracias') || lower.includes('thanks')) {
      return '¡De nada! 😊 Estoy aquí para ayudarte. Si necesitas algo más, no dudes en preguntar.';
    }

    if (lower.includes('ayuda') || lower.includes('help')) {
      return '📋 Puedo ayudarte con:\n\n• **Productos**: Info del catálogo y precios\n• **Cómo vender**: Pasos para realizar una venta\n• **Stock**: Consultas de inventario\n• **Problemas**: Solución a errores comunes\n• **Carrito**: Cómo gestionar items\n• **Cliente**: Sobre el ID de cliente\n\n¡Pregúntame lo que necesites!';
    }

    return '🤔 No estoy seguro de entender tu consulta. Puedo ayudarte con:\n\n• Información de **productos** y precios\n• **Cómo realizar** una venta\n• Consultas de **stock**\n• **Problemas** frecuentes\n\nIntenta reformular tu pregunta o usa los botones rápidos de abajo.';
  }

  private scrollToBottom(): void {
    if (this.messagesContainer) {
      const el = this.messagesContainer.nativeElement;
      el.scrollTop = el.scrollHeight;
    }
  }
}
