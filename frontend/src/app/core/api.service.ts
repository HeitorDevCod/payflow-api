import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  Address,
  CardData,
  Order,
  Page,
  Payment,
  Product,
  PublicConfig,
  Subscription,
  TokenResponse,
  User,
} from './models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  config(): Observable<PublicConfig> {
    return this.http.get<PublicConfig>(`${this.base}/config`);
  }

  register(body: { name: string; email: string; password: string; document: string }): Observable<User> {
    return this.http.post<User>(`${this.base}/auth/register`, body);
  }

  login(body: { email: string; password: string }): Observable<TokenResponse> {
    return this.http.post<TokenResponse>(`${this.base}/auth/login`, body);
  }

  me(): Observable<User> {
    return this.http.get<User>(`${this.base}/me`);
  }

  products(): Observable<Page<Product>> {
    return this.http.get<Page<Product>>(`${this.base}/products`, { params: { size: 50 } });
  }

  createOrder(items: { productId: string; quantity: number }[]): Observable<Order> {
    return this.http.post<Order>(`${this.base}/orders`, { items });
  }

  orders(): Observable<Page<Order>> {
    return this.http.get<Page<Order>>(`${this.base}/orders`, { params: { size: 50 } });
  }

  order(id: string): Observable<Order> {
    return this.http.get<Order>(`${this.base}/orders/${id}`);
  }

  cancelOrder(id: string): Observable<Order> {
    return this.http.post<Order>(`${this.base}/orders/${id}/cancel`, {});
  }

  orderPayments(orderId: string): Observable<Payment[]> {
    return this.http.get<Payment[]>(`${this.base}/orders/${orderId}/payments`);
  }

  payWithPix(orderId: string, key: string): Observable<Payment> {
    return this.http.post<Payment>(`${this.base}/payments/pix`, { orderId }, { headers: idempotency(key) });
  }

  payWithCard(orderId: string, card: CardData, key: string): Observable<Payment> {
    return this.http.post<Payment>(`${this.base}/payments/card`, { orderId, ...card }, { headers: idempotency(key) });
  }

  payWithBoleto(orderId: string, address: Address, key: string): Observable<Payment> {
    return this.http.post<Payment>(`${this.base}/payments/boleto`, { orderId, address }, { headers: idempotency(key) });
  }

  payment(id: string): Observable<Payment> {
    return this.http.get<Payment>(`${this.base}/payments/${id}`);
  }

  refreshPayment(id: string): Observable<Payment> {
    return this.http.post<Payment>(`${this.base}/payments/${id}/refresh`, {});
  }

  cancelPayment(id: string): Observable<Payment> {
    return this.http.post<Payment>(`${this.base}/payments/${id}/cancel`, {});
  }

  /** Só existe no modo demonstração: simula o cliente pagando o Pix ou o boleto. */
  simulatePayment(id: string): Observable<Payment> {
    return this.http.post<Payment>(`${this.base}/demo/payments/${id}/pay`, {});
  }

  subscriptions(): Observable<Subscription[]> {
    return this.http.get<Subscription[]>(`${this.base}/subscriptions`);
  }

  createSubscription(body: {
    reason: string;
    amount: number;
    frequency: number;
    frequencyType: 'DAYS' | 'MONTHS';
    cardToken: string;
  }): Observable<Subscription> {
    return this.http.post<Subscription>(`${this.base}/subscriptions`, body);
  }

  changeSubscription(id: string, action: 'pause' | 'resume' | 'cancel'): Observable<Subscription> {
    return this.http.post<Subscription>(`${this.base}/subscriptions/${id}/${action}`, {});
  }
}

function idempotency(key: string): HttpHeaders {
  return new HttpHeaders({ 'Idempotency-Key': key });
}
