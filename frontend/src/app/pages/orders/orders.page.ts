import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { messageOf } from '../../core/errors';
import { orderStatus } from '../../core/labels';
import { Order } from '../../core/models';
import { StatusBadge } from '../../shared/status-badge';

@Component({
  selector: 'pf-orders',
  imports: [CurrencyPipe, DatePipe, RouterLink, StatusBadge],
  template: `
    <section class="narrow narrow--wide">
      <h1 class="title">Meus pedidos</h1>
      @if (error()) {
        <p class="form-error" role="alert">{{ error() }}</p>
      }
      <ul class="rows" [attr.aria-busy]="loading()">
        @for (order of orders(); track order.id) {
          <li>
            <a class="rows__item" [routerLink]="['/pedidos', order.id]">
              <div>
                <strong>{{ describe(order) }}</strong>
                <span class="muted">{{ order.createdAt | date: 'dd/MM/yyyy HH:mm' }}</span>
              </div>
              <span class="rows__amount">{{ order.total | currency }}</span>
              <pf-status [label]="label(order).label" [tone]="label(order).tone" />
            </a>
          </li>
        } @empty {
          @if (!loading()) {
            <li class="empty-row">
              Você ainda não fez nenhum pedido. <a routerLink="/">Ver a loja</a>
            </li>
          }
        }
      </ul>
    </section>
  `,
})
export class OrdersPage {
  protected readonly orders = signal<Order[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  constructor() {
    inject(ApiService)
      .orders()
      .subscribe({
        next: (page) => {
          this.orders.set(page.content);
          this.loading.set(false);
        },
        error: (err: unknown) => {
          this.error.set(messageOf(err));
          this.loading.set(false);
        },
      });
  }

  protected label(order: Order) {
    return orderStatus[order.status];
  }

  protected describe(order: Order): string {
    const [first, ...rest] = order.items;
    if (!first) return 'Pedido';
    return rest.length ? `${first.name} e mais ${rest.length}` : first.name;
  }
}
