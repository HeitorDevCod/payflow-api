import { Component, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { environment } from '../../../environments/environment';
import { AuthService } from '../../core/auth.service';
import { messageOf } from '../../core/errors';

@Component({
  selector: 'pf-login',
  imports: [FormsModule, RouterLink],
  template: `
    <section class="narrow">
      <h1 class="title">Entrar</h1>
      <p class="lead">Acesse sua conta para pagar pedidos e gerenciar assinaturas.</p>

      <form class="stack" (ngSubmit)="submit()">
        <div class="field">
          <label for="email">E-mail</label>
          <input id="email" name="email" type="email" autocomplete="email" required [(ngModel)]="email" />
        </div>
        <div class="field">
          <label for="password">Senha</label>
          <input id="password" name="password" type="password" autocomplete="current-password" required [(ngModel)]="password" />
        </div>
        @if (error()) {
          <p class="form-error" role="alert">{{ error() }}</p>
        }
        <button type="submit" class="btn btn--primary btn--block" [disabled]="busy()">
          {{ busy() ? 'Entrando…' : 'Entrar' }}
        </button>
      </form>

      <p class="aside">Ainda não tem conta? <a routerLink="/cadastro" [queryParams]="{ voltar: voltar() }">Criar conta</a></p>
      @if (demo) {
        <p class="aside muted">Na demonstração, crie uma conta com qualquer e-mail. Ela fica salva só neste navegador.</p>
      }
    </section>
  `,
})
export class LoginPage {
  readonly voltar = input<string>();

  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly demo = environment.demo;
  protected email = '';
  protected password = '';
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected submit(): void {
    if (!this.email || !this.password) {
      this.error.set('Preencha e-mail e senha.');
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.auth.login(this.email, this.password).subscribe({
      next: () => void this.router.navigateByUrl(this.voltar() || '/'),
      error: (err: unknown) => {
        this.busy.set(false);
        this.error.set(messageOf(err));
      },
    });
  }
}
