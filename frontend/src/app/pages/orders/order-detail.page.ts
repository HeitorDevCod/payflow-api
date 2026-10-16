import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { messageOf } from '../../core/errors';
import { orderStatus, paymentMethod, paymentStatus } from '../../core/labels';
import { Order, Payment } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { StatusBadge } from '../../shared/status-badge';

@Component({
  selector: 'pf-order-detail',
  imports: [CurrencyPipe, DatePipe, RouterLink, StatusBadge],
  template: `
    @if (order(); as o) {
      <section class="narrow narrow--wide">
        <a routerLink="/pedidos" class="back">Meus pedidos</a>
        <div class="heading-row">
          <h1 class="title">Pedido <span class="mono">{{ o.id.slice(0, 8) }}</span></h1>
          <pf-status [label]="orderLabel(o).label" [tone]="orderLabel(o).tone" />
        </div>
        <p class="muted">Feito em {{ o.createdAt | date: "dd/MM/yyyy 'às' HH:mm" }}</p>

        <table class="table">
          <thead><tr><th scope="col">Item</th><th scope="col">Qtd.</th><th scope="col">Valor</th></tr></thead>
          <tbody>
            @for (item of o.items; track item.productId) {
              <tr><td>{{ item.name }}</td><td>{{ item.quantity }}</td><td>{{ item.subtotal | currency }}</td></tr>
            }
          </tbody>
          <tfoot><tr><th scope="row" colspan="2">Total</th><td>{{ o.total | currency }}</td></tr></tfoot>
        </table>

        @if (o.status === 'AWAITING_PAYMENT') {
          <div class="button-row">
            @if (pending(); as active) {
              <a class="btn btn--primary" [routerLink]="['/pagamentos', active.id]">Continuar pagamento</a>
            } @else {
              <a class="btn btn--primary" routerLink="/checkout" [queryParams]="{ pedido: o.id }">Pagar pedido</a>
              <button type="button" class="btn btn--ghost" [disabled]="busy()" (click)="cancel(o)">Cancelar pedido</button>
            }
          </div>
        }
        @if (o.status === 'PAYMENT_REVIEW') {
          <p class="notice">O valor confirmado pelo provedor não bateu com o pedido. Ele ficou em análise e nada foi liberado.</p>
        }

        <h2 class="subtitle">Tentativas de pagamento</h2>
        <ul class="rows">
          @for (p of payments(); track p.id) {
            <li>
              <a class="rows__item" [routerLink]="['/pagamentos', p.id]">
                <div>
                  <strong>{{ method(p) }}@if (p.installments > 1) { em {{ p.installments }}x }</strong>
                  <span class="muted">{{ p.createdAt | date: 'dd/MM/yyyy HH:mm' }}</span>
                </div>
                <span class="rows__amount">{{ p.amount | currency }}</span>
                <pf-status [label]="paymentLabel(p).label" [tone]="paymentLabel(p).tone" />
              </a>
            </li>
          } @empty {
            <li class="empty-row">Nenhuma tentativa ainda.</li>
          }
        </ul>
      </section>
    } @else if (error()) {
      <section class="narrow"><p class="form-error" role="alert">{{ error() }}</p></section>
    }
  `,
})
export class OrderDetailPage {
  readonly id = input.required<string>();

  private readonly api = inject(ApiService);
  private readonly toasts = inject(ToastService);

  protected readonly order = signal<Order | null>(null);
  protected readonly payments = signal<Payment[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);

  constructor() {
    effect(() => {
      const id = this.id();
      forkJoin({ order: this.api.order(id), payments: this.api.orderPayments(id) }).subscribe({
        next: ({ order, payments }) => {
          this.order.set(order);
          this.payments.set(payments);
        },
        error: (err: unknown) => this.error.set(messageOf(err)),
      });
    });
  }

  protected pending(): Payment | undefined {
    return this.payments().find((p) => p.status === 'PENDING' || p.status === 'IN_PROCESS');
  }

  protected orderLabel(o: Order) {
    return orderStatus[o.status];
  }

  protected paymentLabel(p: Payment) {
    return paymentStatus[p.status];
  }

  protected method(p: Payment): string {
    return paymentMethod[p.method];
  }

  protected cancel(o: Order): void {
    this.busy.set(true);
    this.api.cancelOrder(o.id).subscribe({
      next: (order) => {
        this.busy.set(false);
        this.order.set(order);
        this.toasts.ok('Pedido cancelado.');
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.toasts.error(messageOf(err));
      },
    });
  }
}
