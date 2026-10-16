import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, DestroyRef, computed, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { toDataURL } from 'qrcode';
import { environment } from '../../../environments/environment';
import { ApiService } from '../../core/api.service';
import { messageOf } from '../../core/errors';
import { paymentMethod, paymentStatus, rejectionReason } from '../../core/labels';
import { Payment } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { StatusBadge } from '../../shared/status-badge';

const POLL_MS = 4000;

@Component({
  selector: 'pf-payment',
  imports: [CurrencyPipe, DatePipe, RouterLink, StatusBadge],
  template: `
    @if (payment(); as p) {
      <section class="ticket" [attr.data-state]="state()">
        <header class="ticket__head">
          <div>
            <span class="ticket__method">{{ methodLabel(p) }}</span>
            <strong class="ticket__amount">{{ p.amount | currency }}</strong>
            @if (p.installments > 1) { <span class="ticket__installments">em {{ p.installments }}x de {{ p.amount / p.installments | currency }}</span> }
          </div>
          <pf-status [label]="status(p).label" [tone]="status(p).tone" />
        </header>

        <div class="ticket__perforation" aria-hidden="true"></div>

        <div class="ticket__body">
          @switch (state()) {
            @case ('approved') {
              <div class="outcome outcome--ok">
                <svg viewBox="0 0 48 48" aria-hidden="true"><circle cx="24" cy="24" r="22" /><path d="M14 25l7 7 13-15" /></svg>
                <h1>Pagamento aprovado</h1>
                <p>Recebemos {{ p.amount | currency }} e o pedido já está liberado.</p>
              </div>
            }
            @case ('pix') {
              <h1 class="ticket__title">Pague com Pix</h1>
              <ol class="ticket__steps">
                <li>Abra o app do seu banco e escolha pagar com Pix.</li>
                <li>Escaneie o QR Code ou cole o código.</li>
                <li>A confirmação aparece aqui sozinha.</li>
              </ol>
              <div class="pix">
                @if (qr()) { <img class="pix__qr" [src]="qr()" alt="QR Code do Pix" width="220" height="220" /> }
                <div class="pix__code">
                  <label for="pix-code">Pix copia e cola</label>
                  <textarea id="pix-code" readonly rows="3">{{ p.pix?.qrCode }}</textarea>
                  <button type="button" class="btn btn--primary btn--block" (click)="copy(p.pix?.qrCode)">
                    {{ copied() ? 'Código copiado' : 'Copiar código' }}
                  </button>
                </div>
              </div>
              @if (remaining(); as left) {
                <p class="countdown">Expira em <strong>{{ left }}</strong></p>
              }
            }
            @case ('boleto') {
              <h1 class="ticket__title">Boleto emitido</h1>
              <p>Pague até {{ p.expiresAt | date: "dd 'de' MMMM" }} em qualquer banco ou lotérica. A compensação leva até 2 dias úteis.</p>
              <div class="pix__code">
                <label for="line">Linha digitável</label>
                <textarea id="line" readonly rows="2">{{ p.boleto?.digitableLine }}</textarea>
                <div class="button-row">
                  <button type="button" class="btn btn--primary" (click)="copy(p.boleto?.digitableLine)">
                    {{ copied() ? 'Linha copiada' : 'Copiar linha digitável' }}
                  </button>
                  @if (p.boleto?.url) {
                    <a class="btn btn--ghost" [href]="p.boleto?.url" target="_blank" rel="noopener">Abrir boleto em PDF</a>
                  }
                </div>
              </div>
            }
            @case ('processing') {
              <div class="outcome">
                <span class="spinner" aria-hidden="true"></span>
                <h1>Pagamento em análise</h1>
                <p>O emissor ainda está avaliando. Esta página atualiza sozinha.</p>
              </div>
            }
            @case ('failed') {
              <div class="outcome outcome--bad">
                <svg viewBox="0 0 48 48" aria-hidden="true"><circle cx="24" cy="24" r="22" /><path d="M17 17l14 14M31 17L17 31" /></svg>
                <h1>{{ p.status === 'CANCELED' ? 'Pagamento cancelado' : 'Pagamento não aprovado' }}</h1>
                <p>{{ p.status === 'CANCELED' ? (p.statusDetail === 'expired' ? 'O prazo para pagar terminou.' : 'Nenhum valor foi cobrado.') : reason(p) }}</p>
                <a class="btn btn--primary" routerLink="/checkout" [queryParams]="{ pedido: p.orderId }">Tentar outra forma de pagamento</a>
              </div>
            }
          }

          @if (demo && (state() === 'pix' || state() === 'boleto')) {
            <div class="demo-action">
              <p>Na demonstração não há banco do outro lado. Simule o pagamento para ver a confirmação chegar.</p>
              <button type="button" class="btn btn--ghost" [disabled]="busy()" (click)="simulate()">
                Simular pagamento do {{ p.method === 'PIX' ? 'Pix' : 'boleto' }}
              </button>
            </div>
          }
        </div>

        <footer class="ticket__foot">
          <dl>
            <div><dt>Pagamento</dt><dd class="mono">{{ p.id.slice(0, 8) }}</dd></div>
            @if (p.providerId) { <div><dt>Mercado Pago</dt><dd class="mono">{{ p.providerId }}</dd></div> }
            <div><dt>Criado em</dt><dd>{{ p.createdAt | date: 'dd/MM/yyyy HH:mm' }}</dd></div>
          </dl>
          <div class="button-row">
            <a routerLink="/pedidos/{{ p.orderId }}" class="link">Ver pedido</a>
            @if (state() === 'pix' || state() === 'boleto') {
              <button type="button" class="link link--danger" [disabled]="busy()" (click)="cancel()">Cancelar pagamento</button>
            }
          </div>
        </footer>
      </section>
    } @else if (error()) {
      <section class="narrow">
        <p class="form-error" role="alert">{{ error() }}</p>
        <a routerLink="/pedidos" class="btn btn--ghost">Ver meus pedidos</a>
      </section>
    } @else {
      <section class="ticket ticket--loading" aria-busy="true"></section>
    }
  `,
})
export class PaymentPage {
  readonly id = input.required<string>();

  private readonly api = inject(ApiService);
  private readonly toasts = inject(ToastService);

  protected readonly demo = environment.demo;
  protected readonly payment = signal<Payment | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly copied = signal(false);
  protected readonly qr = signal<string | null>(null);
  private readonly now = signal(Date.now());

  protected readonly state = computed(() => {
    const p = this.payment();
    if (!p) return 'loading';
    if (p.status === 'APPROVED') return 'approved';
    if (p.status === 'PENDING' && p.method === 'PIX') return 'pix';
    if (p.status === 'PENDING' && p.method === 'BOLETO') return 'boleto';
    if (p.status === 'PENDING' || p.status === 'IN_PROCESS') return 'processing';
    return 'failed';
  });

  protected readonly remaining = computed(() => {
    const expires = this.payment()?.expiresAt;
    if (!expires) return null;
    const ms = new Date(expires).getTime() - this.now();
    if (ms <= 0) return null;
    const minutes = Math.floor(ms / 60_000);
    const seconds = Math.floor((ms % 60_000) / 1000);
    return `${minutes}:${seconds.toString().padStart(2, '0')}`;
  });

  constructor() {
    effect(() => this.load(this.id()));

    effect(() => {
      const code = this.payment()?.pix?.qrCode;
      if (code) {
        toDataURL(code, { margin: 1, width: 440, color: { dark: '#0F2A33', light: '#FFFFFF' } })
          .then((url) => this.qr.set(url))
          .catch(() => this.qr.set(null));
      }
    });

    const clock = setInterval(() => this.now.set(Date.now()), 1000);
    const poll = setInterval(() => this.poll(), POLL_MS);
    inject(DestroyRef).onDestroy(() => {
      clearInterval(clock);
      clearInterval(poll);
    });
  }

  protected status(p: Payment) {
    return paymentStatus[p.status];
  }

  protected methodLabel(p: Payment): string {
    return paymentMethod[p.method];
  }

  protected reason(p: Payment): string {
    return p.status === 'FAILED'
      ? 'O pagamento não chegou ao Mercado Pago. Nenhum valor foi cobrado.'
      : rejectionReason(p.statusDetail);
  }

  protected copy(text: string | undefined): void {
    if (!text) return;
    navigator.clipboard
      .writeText(text)
      .then(() => {
        this.copied.set(true);
        setTimeout(() => this.copied.set(false), 2500);
      })
      .catch(() => this.toasts.error('Não foi possível copiar. Selecione o texto e copie manualmente.'));
  }

  protected simulate(): void {
    this.busy.set(true);
    this.api.simulatePayment(this.id()).subscribe({
      next: (p) => {
        this.busy.set(false);
        this.payment.set(p);
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.toasts.error(messageOf(err));
      },
    });
  }

  protected cancel(): void {
    this.busy.set(true);
    this.api.cancelPayment(this.id()).subscribe({
      next: (p) => {
        this.busy.set(false);
        this.payment.set(p);
        this.toasts.ok('Pagamento cancelado.');
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.toasts.error(messageOf(err));
      },
    });
  }

  private load(id: string): void {
    this.api.payment(id).subscribe({
      next: (p) => this.payment.set(p),
      error: (err: unknown) => this.error.set(messageOf(err)),
    });
  }

  /** Enquanto aguarda, consulta o status direto no provedor (rede de segurança caso o webhook atrase). */
  private poll(): void {
    const p = this.payment();
    if (!p || this.busy() || (p.status !== 'PENDING' && p.status !== 'IN_PROCESS')) {
      return;
    }
    this.api.refreshPayment(p.id).subscribe({
      next: (fresh) => {
        if (fresh.status === 'APPROVED' && p.status !== 'APPROVED') {
          this.toasts.ok('Pagamento confirmado.');
        }
        this.payment.set(fresh);
      },
      error: () => undefined,
    });
  }
}
