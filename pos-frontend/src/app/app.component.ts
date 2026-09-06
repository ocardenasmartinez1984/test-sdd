import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * Componente raíz de la aplicación POS Frontend.
 *
 * Actúa como shell principal (standalone) y su única responsabilidad es
 * alojar el `<router-outlet>`, delegando el renderizado de las vistas a las
 * rutas configuradas en {@link routes}. No contiene lógica de negocio.
 */
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  template: `<router-outlet />`
})
export class AppComponent {}
