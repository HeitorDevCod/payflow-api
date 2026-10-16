import { CurrencyPipe } from '@angular/common';
import {
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  computed,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';
import { ConfigService } from '../core/config.service';
import { CardData } from '../core/models';
import { BrickController, createCardBrick } from './mercado-pago';

const MIN_INSTALLMENT = 5;
let sequence = 0;

/**
 * Formulário de cartão. Com o backend real, usa o Card Payment Brick do Mercado Pago:
 * os dados do cartão vão direto para o Mercado Pago e só o token chega à API.
 * No modo demonstração, mostra um formulário próprio que segue os cartões de teste do Mercado Pago.
 */
@Component({
  selector: 'pf-card-form',
  imports: [FormsModule, CurrencyPipe],
  template: `
    @if (demo) {
      <form class="card-form" (ngSubmit)="submitDemo()" #form="ngForm">
        <div class="field">
          <label for="cc-number">Número do cartão</label>
          <input id="cc-number" name="number" inputmode="numeric" autocomplete="cc-number" required
                 [ngModel]="number()" (ngModelChange)="number.set(formatNumber($event))" maxlength="19" />
        </div>
        <div class="field">
          <label for="cc-name">Nome do titular</label>
          <input id="cc-name" name="holder" autocomplete="cc-name" required
                 [ngModel]="holder()" (ngModelChange)="holder.set($event.toUpperCase())" />
        </div>
        <div class="field-row">
          <div class="field">
            <label for="cc-exp">Validade</label>
            <input id="cc-exp" name="expiry" placeholder="MM/AA" inputmode="numeric" autocomplete="cc-exp" required
                   [ngModel]="expiry()" (ngModelChange)="expiry.set(formatExpiry($event))" maxlength="5" />
          </div>
          <div class="field">
            <label for="cc-cvv">CVV</label>
            <input id="cc-cvv" name="cvv" inputmode="numeric" autocomplete="cc-csc" required
                   [(ngModel)]="cvv" maxlength="4" />
          </div>
        </div>
        @if (allowInstallments()) {
          <div class="field">
            <label for="cc-installments">Parcelas</label>
            <select id="cc-installments" name="installments" [(ngModel)]="installments">
              @for (option of installmentOptions(); track option.count) {
                <option [ngValue]="option.count">{{ option.count }}x de {{ option.value | currency }} sem juros</option>
              }
            </select>
          </div>
        }

        <details class="test-cards">
          <summary>Cartões de teste</summary>
          <p>Use o número já preenchido. O nome do titular decide o resultado, como no sandbox do Mercado Pago:</p>
          <ul>
            <li><button type="button" (click)="holder.set('APRO')">APRO</button> aprovado</li>
            <li><button type="button" (click)="holder.set('OTHE')">OTHE</button> recusado pelo emissor</li>
            <li><button type="button" (click)="holder.set('FUND')">FUND</button> saldo insuficiente</li>
            <li><button type="button" (click)="holder.set('CONT')">CONT</button> em análise</li>
          </ul>
        </details>

        @if (localError()) {
          <p class="form-error" role="alert">{{ localError() }}</p>
        }
        <button type="submit" class="btn btn--primary btn--block" [disabled]="busy()">
          {{ busy() ? 'Processando…' : submitLabel() }}
        </button>
      </form>
    } @else {
      @if (missingKey()) {
        <p class="form-error">Pagamento com cartão indisponível: configure MP_PUBLIC_KEY no backend.</p>
      }
      <div [id]="containerId" #brick class="brick"></div>
    }
  `,
})
export class CardForm {
  readonly amount = input.required<number>();
  readonly allowInstallments = input(true);
  readonly submitLabel = input('Pagar');
  readonly busy = input(false);
  readonly submitted = output<CardData>();

  protected readonly demo = environment.demo;
  protected readonly containerId = `card-brick-${++sequence}`;
  protected readonly number = signal('5031 4332 1540 6351');
  protected readonly holder = signal('APRO');
  protected readonly expiry = signal('11/30');
  protected cvv = '123';
  protected installments = 1;
  protected readonly localError = signal<string | null>(null);
  protected readonly missingKey = signal(false);

  protected readonly installmentOptions = computed(() => {
    const total = this.amount();
    const max = Math.max(1, Math.min(12, Math.floor(total / MIN_INSTALLMENT)));
    return Array.from({ length: max }, (_, i) => ({
      count: i + 1,
      value: Math.floor((total / (i + 1)) * 100) / 100,
    }));
  });

  private readonly brickHost = viewChild<ElementRef<HTMLElement>>('brick');
  private readonly config = inject(ConfigService);
  private brick: BrickController | null = null;

  constructor() {
    afterNextRender(() => {
      if (!this.demo) {
        void this.mountBrick();
      }
    });
    inject(DestroyRef).onDestroy(() => this.brick?.unmount());
  }

  protected formatNumber(value: string): string {
    return value.replace(/\D/g, '').slice(0, 16).replace(/(\d{4})(?=\d)/g, '$1 ');
  }

  protected formatExpiry(value: string): string {
    const digits = value.replace(/\D/g, '').slice(0, 4);
    return digits.length > 2 ? `${digits.slice(0, 2)}/${digits.slice(2)}` : digits;
  }

  protected submitDemo(): void {
    const digits = this.number().replace(/\D/g, '');
    const [month, year] = this.expiry().split('/').map(Number);
    if (digits.length < 13) {
      this.localError.set('Número do cartão incompleto.');
      return;
    }
    if (!month || month > 12 || !year) {
      this.localError.set('Validade inválida. Use o formato MM/AA.');
      return;
    }
    if (!/^\d{3,4}$/.test(this.cvv)) {
      this.localError.set('CVV deve ter 3 ou 4 dígitos.');
      return;
    }
    if (!this.holder().trim()) {
      this.localError.set('Informe o nome do titular.');
      return;
    }
    this.localError.set(null);
    const random = Math.random().toString(36).slice(2, 12);
    this.submitted.emit({
      token: `demo${random}:${this.holder().trim()}`,
      paymentMethodId: brandOf(digits),
      issuerId: null,
      installments: this.allowInstallments() ? this.installments : 1,
    });
  }

  private async mountBrick(): Promise<void> {
    const config = await firstValueFrom(this.config.config$);
    if (!config.mercadoPagoPublicKey) {
      this.missingKey.set(true);
      return;
    }
    if (!this.brickHost()) {
      return;
    }
    this.brick = await createCardBrick({
      publicKey: config.mercadoPagoPublicKey,
      containerId: this.containerId,
      amount: this.amount(),
      maxInstallments: this.allowInstallments() ? 12 : 1,
      onSubmit: (data) =>
        this.submitted.emit({
          token: data.token,
          paymentMethodId: data.payment_method_id,
          issuerId: data.issuer_id == null ? null : Number(data.issuer_id),
          installments: data.installments,
        }),
      onError: () => this.localError.set('Não foi possível validar o cartão.'),
    });
  }
}

function brandOf(digits: string): string {
  if (/^(4011|4312|4389|4514|4576|5041|5066|5067|509|6277|6362|6363|650|6516|6550)/.test(digits)) return 'elo';
  if (/^(606282|3841)/.test(digits)) return 'hipercard';
  if (/^3[47]/.test(digits)) return 'amex';
  if (digits.startsWith('4')) return 'visa';
  return 'master';
}
