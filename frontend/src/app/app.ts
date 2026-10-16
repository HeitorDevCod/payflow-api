import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { environment } from '../environments/environment';
import { AuthService } from './core/auth.service';
import { CartService } from './core/cart.service';
import { ToastService } from './core/toast.service';

@Component({
  selector: 'pf-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    @if (demo) {
      <div class="demo-strip" role="note">
        Demonstração: nenhum pagamento é real e os dados ficam só neste navegador.
        <a href="https://github.com/HeitorDevCod/payflow-api" target="_blank" rel="noopener">Ver o código</a>
      </div>
    }

    <header class="bar">
      <div class="bar__inner">
        <a routerLink="/" class="brand" aria-label="Payflow, página inicial">
          <svg viewBox="0 0 32 32" aria-hidden="true"><rect width="32" height="32" rx="7" /><path d="M9 22V10h7.5a4.5 4.5 0 0 1 0 9H13" /></svg>
          payflow
        </a>
        <nav class="nav" aria-label="Principal">
          <a routerLink="/" routerLinkActive="is-active" [routerLinkActiveOptions]="{ exact: true }">Loja</a>
          @if (auth.isLoggedIn()) {
            <a routerLink="/pedidos" routerLinkActive="is-active">Pedidos</a>
            <a routerLink="/assinaturas" routerLinkActive="is-active">Assinaturas</a>
          }
        </nav>
        <div class="bar__side">
          <a routerLink="/checkout" class="cart-link" [class.has-items]="cart.count() > 0">
            Carrinho
            @if (cart.count() > 0) {
              <span class="cart-link__count" aria-label="itens no carrinho">{{ cart.count() }}</span>
            }
          </a>
          @if (auth.user(); as user) {
            <button type="button" class="link-button" (click)="auth.logout()" [title]="user.email">Sair</button>
          } @else {
            <a routerLink="/entrar" class="link-button">Entrar</a>
          }
        </div>
      </div>
    </header>

    <main class="page">
      <router-outlet />
    </main>

    <footer class="foot">
      <span>Payflow · pagamentos com Mercado Pago</span>
      <a href="https://github.com/HeitorDevCod/payflow-api" target="_blank" rel="noopener">Código no GitHub</a>
    </footer>

    <div class="toasts" aria-live="polite">
      @for (toast of toasts.toasts(); track toast.id) {
        <div class="toast" [class.toast--error]="toast.tone === 'error'" (click)="toasts.dismiss(toast.id)">{{ toast.text }}</div>
      }
    </div>
  `,
})
export class App {
  protected readonly auth = inject(AuthService);
  protected readonly cart = inject(CartService);
  protected readonly toasts = inject(ToastService);
  protected readonly demo = environment.demo;
}
