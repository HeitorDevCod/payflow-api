import { Injectable, computed, signal } from '@angular/core';
import { Product } from './models';
import { storage } from './storage';

export interface CartLine {
  product: Product;
  quantity: number;
}

const KEY = 'payflow.cart';
const MAX_QUANTITY = 100;

@Injectable({ providedIn: 'root' })
export class CartService {
  private readonly lines = signal<CartLine[]>(storage.read<CartLine[]>(KEY) ?? []);

  readonly items = this.lines.asReadonly();
  readonly count = computed(() => this.lines().reduce((sum, line) => sum + line.quantity, 0));
  readonly total = computed(() =>
    round(this.lines().reduce((sum, line) => sum + line.product.price * line.quantity, 0)),
  );

  quantityOf(productId: string): number {
    return this.lines().find((line) => line.product.id === productId)?.quantity ?? 0;
  }

  add(product: Product): void {
    this.setQuantity(product, this.quantityOf(product.id) + 1);
  }

  setQuantity(product: Product, quantity: number): void {
    const clamped = Math.max(0, Math.min(MAX_QUANTITY, Math.floor(quantity)));
    const others = this.lines().filter((line) => line.product.id !== product.id);
    const next = clamped === 0 ? others : this.upsert(product, clamped);
    this.lines.set(next);
    storage.write(KEY, next);
  }

  clear(): void {
    this.lines.set([]);
    storage.remove(KEY);
  }

  private upsert(product: Product, quantity: number): CartLine[] {
    const exists = this.lines().some((line) => line.product.id === product.id);
    return exists
      ? this.lines().map((line) => (line.product.id === product.id ? { product, quantity } : line))
      : [...this.lines(), { product, quantity }];
  }
}

function round(value: number): number {
  return Math.round(value * 100) / 100;
}
