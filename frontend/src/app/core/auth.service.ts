import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, map, switchMap, tap } from 'rxjs';
import { ApiService } from './api.service';
import { User } from './models';
import { storage } from './storage';

interface Session {
  token: string;
  expiresAt: string;
  user: User;
}

const KEY = 'payflow.session';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly session = signal<Session | null>(this.restore());

  readonly user = computed(() => this.session()?.user ?? null);
  readonly isLoggedIn = computed(() => this.session() !== null);

  token(): string | null {
    const current = this.session();
    if (!current) {
      return null;
    }
    if (new Date(current.expiresAt).getTime() <= Date.now()) {
      this.clear();
      return null;
    }
    return current.token;
  }

  login(email: string, password: string): Observable<User> {
    return this.api.login({ email, password }).pipe(
      tap((token) => this.session.set({ token: token.accessToken, expiresAt: token.expiresAt, user: placeholder(email) })),
      switchMap((token) =>
        this.api.me().pipe(
          map((user) => {
            const session = { token: token.accessToken, expiresAt: token.expiresAt, user };
            this.session.set(session);
            storage.write(KEY, session);
            return user;
          }),
        ),
      ),
    );
  }

  register(data: { name: string; email: string; password: string; document: string }): Observable<User> {
    return this.api.register(data).pipe(switchMap(() => this.login(data.email, data.password)));
  }

  logout(redirect = true): void {
    this.clear();
    if (redirect) {
      void this.router.navigate(['/entrar']);
    }
  }

  private clear(): void {
    this.session.set(null);
    storage.remove(KEY);
  }

  private restore(): Session | null {
    const saved = storage.read<Session>(KEY);
    if (!saved || new Date(saved.expiresAt).getTime() <= Date.now()) {
      return null;
    }
    return saved;
  }
}

function placeholder(email: string): User {
  return { id: '', name: email, email, role: 'CUSTOMER' };
}
