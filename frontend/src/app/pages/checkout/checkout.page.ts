import { CurrencyPipe } from '@angular/common';
import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Observable, of, switchMap, tap } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { CartService } from '../../core/cart.service';
import { messageOf, problemOf } from '../../core/errors';
import { newIdempotencyKey } from '../../core/labels';
import { Address, CardData, Order, Payment } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { CardForm } from '../../shared/card-form';

type Method = 'pix' | 'card' | 'boleto';

interface Line {
  name: string;
  quantity: number;
  subtotal: number;
}

@Component({
  selector: 'pf-checkout',
  imports: [CurrencyPipe, FormsModule, RouterLink, CardForm],
  template: `
    @if (lines().length === 0 && !loadingOrder()) {
      <section class="narrow empty">
        <h1 class="title">Seu carrinho está vazio</h1>
        <p class="lead">Escolha um curso ou mentoria na loja para continuar.</p>
        <a routerLink="/" class="btn btn--primary">Ver a loja</a>
      </section>
    } @else {
      <div class="checkout">
        <section class="checkout__main">
          <h1 class="title">Pagamento</h1>

          <div class="methods" role="tablist" aria-label="Forma de pagamento">
            @for (option of methods; track option.id) {
              <button type="button" role="tab" class="methods__tab"
                      [class.is-active]="method() === option.id" [attr.aria-selected]="method() === option.id"
                      (click)="method.set(option.id)">
                <strong>{{ option.label }}</strong>
                <span>{{ option.note }}</span>
              </button>
            }
          </div>

          <div class="method-panel" role="tabpanel">
            @switch (method()) {
              @case ('pix') {
                <p>O QR Code vale por 30 minutos. A confirmação chega em segundos depois que você paga no app do banco.</p>
                <button type="button" class="btn btn--primary btn--block" [disabled]="busy()" (click)="payPix()">
                  {{ busy() ? 'Gerando Pix…' : 'Gerar Pix de ' + (total() | currency) }}
                </button>
              }
              @case ('card') {
                <pf-card-form [amount]="total()" [busy]="busy()" [submitLabel]="'Pagar ' + (total() | currency)"
                              (submitted)="payCard($event)" />
              }
              @case ('boleto') {
                <p>O boleto vence em 3 dias úteis. O endereço é obrigatório para a emissão.</p>
                <form class="stack" (ngSubmit)="payBoleto()">
                  <div class="field-row">
                    <div class="field" [class.has-error]="fieldErrors()['address.zipCode']">
                      <label for="cep">CEP</label>
                      <input id="cep" name="zipCode" inputmode="numeric" autocomplete="postal-code" maxlength="9" required [(ngModel)]="address.zipCode" />
                    </div>
                    <div class="field field--narrow" [class.has-error]="fieldErrors()['address.state']">
                      <label for="uf">UF</label>
                      <input id="uf" name="state" maxlength="2" required [ngModel]="address.state"
                             (ngModelChange)="address.state = $event.toUpperCase()" />
                    </div>
                  </div>
                  <div class="field-row">
                    <div class="field" [class.has-error]="fieldErrors()['address.street']">
                      <label for="street">Rua</label>
                      <input id="street" name="street" autocomplete="address-line1" required [(ngModel)]="address.street" />
                    </div>
                    <div class="field field--narrow" [class.has-error]="fieldErrors()['address.number']">
                      <label for="number">Número</label>
                      <input id="number" name="number" required [(ngModel)]="address.number" />
                    </div>
                  </div>
                  <div class="field-row">
                    <div class="field" [class.has-error]="fieldErrors()['address.neighborhood']">
                      <label for="district">Bairro</label>
                      <input id="district" name="neighborhood" required [(ngModel)]="address.neighborhood" />
                    </div>
                    <div class="field" [class.has-error]="fieldErrors()['address.city']">
                      <label for="city">Cidade</label>
                      <input id="city" name="city" autocomplete="address-level2" required [(ngModel)]="address.city" />
                    </div>
                  </div>
                  <button type="submit" class="btn btn--primary btn--block" [disabled]="busy()">
                    {{ busy() ? 'Emitindo boleto…' : 'Emitir boleto de ' + (total() | currency) }}
                  </button>
                </form>
              }
            }
            @if (error()) {
              <p class="form-error" role="alert">{{ error() }}</p>
            }
          </div>
        </section>

        <aside class="summary" aria-label="Resumo do pedido">
          <h2 class="summary__title">Resumo</h2>
          <ul class="summary__lines">
            @for (line of lines(); track line.name) {
              <li><span>{{ line.quantity }}× {{ line.name }}</span><span>{{ line.subtotal | currency }}</span></li>
            }
          </ul>
          <div class="summary__total"><span>Total</span><strong>{{ total() | currency }}</strong></div>
          @if (!order()) {
            <a routerLink="/" class="summary__edit">Alterar itens</a>
          }
        </aside>
      </div>
    }
  `,
})
export class CheckoutPage {
  /** Pedido existente a pagar (vindo da tela de pedidos). Sem ele, o pedido é criado a partir do carrinho. */
  readonly pedido = input<string>();

  private readonly api = inject(ApiService);
  private readonly cart = inject(CartService);
  private readonly router = inject(Router);
  private readonly toasts = inject(ToastService);

  protected readonly methods: { id: Method; label: string; note: string }[] = [
    { id: 'pix', label: 'Pix', note: 'Aprovação imediata' },
    { id: 'card', label: 'Cartão', note: 'Até 12x' },
    { id: 'boleto', label: 'Boleto', note: 'Vence em 3 dias' },
  ];
  protected readonly method = signal<Method>('pix');
  protected readonly order = signal<Order | null>(null);
  protected readonly loadingOrder = signal(false);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly fieldErrors = signal<Record<string, string>>({});
  protected address: Address = { zipCode: '', street: '', number: '', neighborhood: '', city: '', state: '' };

  protected readonly lines = computed<Line[]>(() => {
    const order = this.order();
    if (order) {
      return order.items.map((i) => ({ name: i.name, quantity: i.quantity, subtotal: i.subtotal }));
    }
    return this.cart.items().map((l) => ({
      name: l.product.name,
      quantity: l.quantity,
      subtotal: l.product.price * l.quantity,
    }));
  });
  protected readonly total = computed(() => this.order()?.total ?? this.cart.total());

  constructor() {
    effect(() => {
      const id = this.pedido();
      if (id) {
        this.loadingOrder.set(true);
        this.api.order(id).subscribe({
          next: (order) => {
            this.order.set(order);
            this.loadingOrder.set(false);
            if (order.status !== 'AWAITING_PAYMENT') {
              void this.router.navigate(['/pedidos', order.id]);
            }
          },
          error: (err: unknown) => {
            this.loadingOrder.set(false);
            this.error.set(messageOf(err));
          },
        });
      }
    });
  }

  protected payPix(): void {
    this.pay((order, key) => this.api.payWithPix(order.id, key));
  }

  protected payCard(card: CardData): void {
    this.pay((order, key) => this.api.payWithCard(order.id, card, key));
  }

  protected payBoleto(): void {
    const address = { ...this.address, zipCode: this.address.zipCode.trim(), state: this.address.state.trim() };
    this.pay((order, key) => this.api.payWithBoleto(order.id, address, key));
  }

  /**
   * Cria o pedido (uma única vez) e envia o pagamento com uma Idempotency-Key nova por tentativa.
   * Se a resposta se perder e o usuário tentar de novo, o backend não cobra duas vezes.
   */
  private pay(send: (order: Order, key: string) => Observable<Payment>): void {
    if (this.busy()) {
      return;
    }
    const key = newIdempotencyKey();
    this.busy.set(true);
    this.error.set(null);
    this.fieldErrors.set({});

    this.ensureOrder()
      .pipe(switchMap((order) => send(order, key)))
      .subscribe({
        next: (payment) => {
          if (!this.pedido()) {
            this.cart.clear();
          }
          void this.router.navigate(['/pagamentos', payment.id]);
        },
        error: (err: unknown) => {
          this.busy.set(false);
          const problem = problemOf(err);
          if (problem.code === 'payment_in_progress' && this.order()) {
            this.toasts.error('Este pedido já tem um pagamento em andamento.');
            void this.router.navigate(['/pedidos', this.order()!.id]);
            return;
          }
          if (problem.errors) {
            this.fieldErrors.set(problem.errors);
          }
          this.error.set(messageOf(err));
        },
      });
  }

  private ensureOrder(): Observable<Order> {
    const existing = this.order();
    if (existing) {
      return of(existing);
    }
    const items = this.cart.items().map((l) => ({ productId: l.product.id, quantity: l.quantity }));
    return this.api.createOrder(items).pipe(tap((order) => this.order.set(order)));
  }
}
