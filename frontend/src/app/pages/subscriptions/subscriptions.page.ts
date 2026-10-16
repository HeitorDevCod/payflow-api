import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { ApiService } from '../../core/api.service';
import { messageOf } from '../../core/errors';
import { subscriptionStatus } from '../../core/labels';
import { CardData, Subscription } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { CardForm } from '../../shared/card-form';
import { StatusBadge } from '../../shared/status-badge';

interface Plan {
  id: string;
  name: string;
  detail: string;
  amount: number;
  frequency: number;
  frequencyType: 'MONTHS';
  period: string;
}

@Component({
  selector: 'pf-subscriptions',
  imports: [CurrencyPipe, DatePipe, CardForm, StatusBadge],
  template: `
    <div class="subscriptions">
      <section>
        <h1 class="title">Assinaturas</h1>
        <p class="lead">Acesso contínuo a todos os cursos, cobrado no cartão de forma recorrente. Pause ou cancele quando quiser.</p>

        <ul class="rows" [attr.aria-busy]="loading()">
          @for (s of subscriptions(); track s.id) {
            <li class="rows__item rows__item--static">
              <div>
                <strong>{{ s.reason }}</strong>
                <span class="muted">
                  {{ s.amount | currency }} {{ s.frequency === 12 ? 'por ano' : 'por mês' }}
                  @if (s.status === 'AUTHORIZED' && s.nextPaymentDate) {, próxima cobrança em {{ s.nextPaymentDate | date: 'dd/MM/yyyy' }} }
                </span>
              </div>
              <pf-status [label]="label(s).label" [tone]="label(s).tone" />
              <div class="button-row">
                @if (s.status === 'AUTHORIZED') {
                  <button type="button" class="link" [disabled]="busyId() === s.id" (click)="change(s, 'pause')">Pausar</button>
                }
                @if (s.status === 'PAUSED') {
                  <button type="button" class="link" [disabled]="busyId() === s.id" (click)="change(s, 'resume')">Retomar</button>
                }
                @if (s.status !== 'CANCELED') {
                  <button type="button" class="link link--danger" [disabled]="busyId() === s.id" (click)="change(s, 'cancel')">Cancelar</button>
                }
              </div>
            </li>
          } @empty {
            @if (!loading()) { <li class="empty-row">Você ainda não tem assinaturas. Escolha um plano ao lado.</li> }
          }
        </ul>
      </section>

      <aside class="summary" aria-label="Nova assinatura">
        <h2 class="summary__title">Assinar um plano</h2>
        <div class="plans" role="radiogroup" aria-label="Plano">
          @for (plan of plans; track plan.id) {
            <button type="button" role="radio" class="plan" [class.is-active]="selected().id === plan.id"
                    [attr.aria-checked]="selected().id === plan.id" (click)="selected.set(plan)">
              <span class="plan__name">{{ plan.name }}</span>
              <span class="plan__price">{{ plan.amount | currency }}<small>/{{ plan.period }}</small></span>
              <span class="plan__detail">{{ plan.detail }}</span>
            </button>
          }
        </div>
        <pf-card-form [amount]="selected().amount" [allowInstallments]="false" [busy]="creating()"
                      [submitLabel]="'Assinar por ' + (selected().amount | currency) + '/' + selected().period"
                      (submitted)="subscribe($event)" />
        @if (error()) { <p class="form-error" role="alert">{{ error() }}</p> }
      </aside>
    </div>
  `,
})
export class SubscriptionsPage {
  private readonly api = inject(ApiService);
  private readonly toasts = inject(ToastService);

  protected readonly plans: Plan[] = [
    { id: 'monthly', name: 'Mensal', detail: 'Todos os cursos, renovação mensal', amount: 39.9, frequency: 1, frequencyType: 'MONTHS', period: 'mês' },
    { id: 'yearly', name: 'Anual', detail: 'Todos os cursos e duas mentorias por ano', amount: 399, frequency: 12, frequencyType: 'MONTHS', period: 'ano' },
  ];
  protected readonly selected = signal<Plan>(this.plans[0]);
  protected readonly subscriptions = signal<Subscription[]>([]);
  protected readonly loading = signal(true);
  protected readonly creating = signal(false);
  protected readonly busyId = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  constructor() {
    this.reload();
  }

  protected label(s: Subscription) {
    return subscriptionStatus[s.status];
  }

  protected subscribe(card: CardData): void {
    const plan = this.selected();
    this.creating.set(true);
    this.error.set(null);
    this.api
      .createSubscription({
        reason: `Plano ${plan.name}`,
        amount: plan.amount,
        frequency: plan.frequency,
        frequencyType: plan.frequencyType,
        cardToken: card.token,
      })
      .subscribe({
        next: (created) => {
          this.creating.set(false);
          this.subscriptions.update((list) => [created, ...list]);
          this.toasts.ok(`Plano ${plan.name} assinado.`);
        },
        error: (err: unknown) => {
          this.creating.set(false);
          this.error.set(messageOf(err));
        },
      });
  }

  protected change(s: Subscription, action: 'pause' | 'resume' | 'cancel'): void {
    this.busyId.set(s.id);
    this.api.changeSubscription(s.id, action).subscribe({
      next: (updated) => {
        this.busyId.set(null);
        this.subscriptions.update((list) => list.map((item) => (item.id === updated.id ? updated : item)));
        const done = { pause: 'Assinatura pausada.', resume: 'Assinatura retomada.', cancel: 'Assinatura cancelada.' };
        this.toasts.ok(done[action]);
      },
      error: (err: unknown) => {
        this.busyId.set(null);
        this.toasts.error(messageOf(err));
      },
    });
  }

  private reload(): void {
    this.api.subscriptions().subscribe({
      next: (list) => {
        this.subscriptions.set(list);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.error.set(messageOf(err));
      },
    });
  }
}
