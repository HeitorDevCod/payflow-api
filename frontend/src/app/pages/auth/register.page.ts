import { Component, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { environment } from '../../../environments/environment';
import { AuthService } from '../../core/auth.service';
import { messageOf, problemOf } from '../../core/errors';

@Component({
  selector: 'pf-register',
  imports: [FormsModule, RouterLink],
  template: `
    <section class="narrow">
      <h1 class="title">Criar conta</h1>
      <p class="lead">O CPF é exigido pelo Mercado Pago para emitir Pix e boleto.</p>

      <form class="stack" (ngSubmit)="submit()">
        <div class="field" [class.has-error]="fieldErrors()['name']">
          <label for="name">Nome completo</label>
          <input id="name" name="name" autocomplete="name" required [(ngModel)]="name" />
          @if (fieldErrors()['name']; as message) { <small>{{ message }}</small> }
        </div>
        <div class="field" [class.has-error]="fieldErrors()['email']">
          <label for="email">E-mail</label>
          <input id="email" name="email" type="email" autocomplete="email" required [(ngModel)]="email" />
          @if (fieldErrors()['email']; as message) { <small>{{ message }}</small> }
        </div>
        <div class="field" [class.has-error]="fieldErrors()['document']">
          <label for="document">CPF</label>
          <input id="document" name="document" inputmode="numeric" required maxlength="14"
                 [ngModel]="document" (ngModelChange)="document = formatCpf($event)" />
          @if (fieldErrors()['document']; as message) { <small>{{ message }}</small> }
          @else if (demo) { <small class="hint">Para testar, use 529.982.247-25</small> }
        </div>
        <div class="field" [class.has-error]="fieldErrors()['password']">
          <label for="password">Senha</label>
          <input id="password" name="password" type="password" autocomplete="new-password" minlength="8" required [(ngModel)]="password" />
          @if (fieldErrors()['password']; as message) { <small>{{ message }}</small> }
          @else { <small class="hint">Mínimo de 8 caracteres</small> }
        </div>
        @if (error()) {
          <p class="form-error" role="alert">{{ error() }}</p>
        }
        <button type="submit" class="btn btn--primary btn--block" [disabled]="busy()">
          {{ busy() ? 'Criando conta…' : 'Criar conta' }}
        </button>
      </form>

      <p class="aside">Já tem conta? <a routerLink="/entrar" [queryParams]="{ voltar: voltar() }">Entrar</a></p>
    </section>
  `,
})
export class RegisterPage {
  readonly voltar = input<string>();

  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly demo = environment.demo;
  protected name = '';
  protected email = '';
  protected document = '';
  protected password = '';
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly fieldErrors = signal<Record<string, string>>({});

  protected formatCpf(value: string): string {
    const d = value.replace(/\D/g, '').slice(0, 11);
    return d
      .replace(/^(\d{3})(\d)/, '$1.$2')
      .replace(/^(\d{3})\.(\d{3})(\d)/, '$1.$2.$3')
      .replace(/\.(\d{3})(\d{1,2})$/, '.$1-$2');
  }

  protected submit(): void {
    this.busy.set(true);
    this.error.set(null);
    this.fieldErrors.set({});
    this.auth
      .register({ name: this.name, email: this.email, password: this.password, document: this.document })
      .subscribe({
        next: () => void this.router.navigateByUrl(this.voltar() || '/'),
        error: (err: unknown) => {
          this.busy.set(false);
          const problem = problemOf(err);
          if (problem.errors) {
            this.fieldErrors.set(problem.errors);
          } else {
            this.error.set(messageOf(err));
          }
        },
      });
  }
}
