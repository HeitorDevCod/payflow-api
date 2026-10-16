import { CurrencyPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { CartService } from '../../core/cart.service';
import { messageOf } from '../../core/errors';
import { Product } from '../../core/models';

@Component({
  selector: 'pf-store',
  imports: [CurrencyPipe, RouterLink],
  template: `
    <div class="store">
      <section>
        <h1 class="title">Cursos e mentorias</h1>
        <p class="lead">Escolha os itens e pague com Pix, cartão em até 12x ou boleto.</p>

        @if (error()) {
          <p class="form-error" role="alert">{{ error() }}</p>
        }

        <ul class="catalog" [attr.aria-busy]="loading()">
          @for (product of products(); track product.id) {
            <li class="catalog__row">
              <div class="catalog__info">
                <h2>{{ product.name }}</h2>
                @if (product.description) { <p>{{ product.description }}</p> }
              </div>
              <span class="catalog__price">{{ product.price | currency }}</span>
              @if (cart.quantityOf(product.id); as quantity) {
                <div class="stepper" role="group" [attr.aria-label]="'Quantidade de ' + product.name">
                  <button type="button" (click)="cart.setQuantity(product, quantity - 1)" aria-label="Diminuir">−</button>
                  <span aria-live="polite">{{ quantity }}</span>
                  <button type="button" (click)="cart.setQuantity(product, quantity + 1)" aria-label="Aumentar">+</button>
                </div>
              } @else {
                <button type="button" class="btn btn--ghost" (click)="cart.add(product)">Adicionar</button>
              }
            </li>
          } @empty {
            @if (loading()) {
              @for (i of [1, 2, 3]; track i) { <li class="catalog__row catalog__row--skeleton" aria-hidden="true"></li> }
            }
          }
        </ul>
      </section>

      <aside class="summary" aria-label="Carrinho">
        <h2 class="summary__title">Seu carrinho</h2>
        @if (cart.items().length) {
          <ul class="summary__lines">
            @for (line of cart.items(); track line.product.id) {
              <li><span>{{ line.quantity }}× {{ line.product.name }}</span><span>{{ line.product.price * line.quantity | currency }}</span></li>
            }
          </ul>
          <div class="summary__total"><span>Total</span><strong>{{ cart.total() | currency }}</strong></div>
          <a routerLink="/checkout" class="btn btn--primary btn--block">Ir para o pagamento</a>
        } @else {
          <p class="muted">Adicione um item para começar.</p>
        }
      </aside>
    </div>
  `,
})
export class StorePage {
  protected readonly cart = inject(CartService);
  protected readonly products = signal<Product[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  constructor() {
    inject(ApiService)
      .products()
      .subscribe({
        next: (page) => {
          this.products.set(page.content);
          this.loading.set(false);
        },
        error: (err: unknown) => {
          this.error.set(messageOf(err));
          this.loading.set(false);
        },
      });
  }
}
