import {
  Address,
  Order,
  OrderStatus,
  Payment,
  PaymentMethod,
  PaymentStatus,
  Product,
  Subscription,
  User,
} from '../core/models';
import { storage } from '../core/storage';

/**
 * Versão em memória da Payflow API usada no modo demonstração (GitHub Pages).
 * Reproduz as mesmas regras do backend: total calculado no servidor, idempotência,
 * um pagamento ativo por pedido, parcelas mínimas e transições de status válidas.
 */

export interface DemoRequest {
  method: string;
  path: string;
  body: unknown;
  headers: { get(name: string): string | null };
}

export interface DemoResponse {
  status: number;
  body: unknown;
}

interface StoredUser extends User {
  password: string;
  document: string;
}

interface State {
  users: StoredUser[];
  orders: (Order & { customerId: string })[];
  payments: (Payment & { customerId: string; idempotencyKey: string })[];
  subscriptions: (Subscription & { customerId: string })[];
}

const KEY = 'payflow.demo.v1';
const MIN_INSTALLMENT = 5;
const MAX_INSTALLMENTS = 12;

export const PRODUCTS: Product[] = [
  {
    id: '168a8e6b-8de6-4744-89dd-3e275a46ca2b',
    name: 'Curso de Java Avançado',
    description: 'Acesso vitalício com certificado',
    price: 297,
    active: true,
  },
  {
    id: '749990f6-6ccd-40b3-ab75-e8061a3fee9f',
    name: 'Mentoria individual',
    description: 'Sessão de 1 hora por videochamada',
    price: 150,
    active: true,
  },
  {
    id: 'f4a4e760-5f5c-48ee-9ae0-4eb74b043929',
    name: 'E-book Spring Boot na prática',
    description: 'PDF com 240 páginas',
    price: 49.9,
    active: true,
  },
  {
    id: '9e4a3053-2c23-4579-9374-d74ef7b7745f',
    name: 'Kit de adesivos dev',
    description: 'Cinco adesivos de vinil',
    price: 19.9,
    active: true,
  },
];

class HttpError {
  constructor(
    readonly status: number,
    readonly code: string,
    readonly detail: string,
    readonly errors?: Record<string, string>,
  ) {}
}

export class DemoBackend {
  private state: State = storage.read<State>(KEY) ?? { users: [], orders: [], payments: [], subscriptions: [] };

  handle(req: DemoRequest): DemoResponse {
    try {
      const body = this.route(req);
      this.save();
      return { status: req.method === 'POST' && isCreation(req.path) ? 201 : 200, body };
    } catch (error) {
      if (error instanceof HttpError) {
        return {
          status: error.status,
          body: { status: error.status, detail: error.detail, code: error.code, errors: error.errors },
        };
      }
      throw error;
    }
  }

  private route(req: DemoRequest): unknown {
    const { method, path } = req;
    const segments = path.split('/').filter(Boolean);
    const body = (req.body ?? {}) as Record<string, unknown>;

    if (method === 'GET' && path === '/config') {
      return { mercadoPagoPublicKey: null, demo: true };
    }
    if (method === 'POST' && path === '/auth/register') {
      return this.register(body);
    }
    if (method === 'POST' && path === '/auth/login') {
      return this.login(body);
    }
    if (method === 'GET' && path === '/products') {
      return { content: PRODUCTS, page: { size: 50, number: 0, totalElements: PRODUCTS.length, totalPages: 1 } };
    }

    const user = this.authenticate(req);

    if (method === 'GET' && path === '/me') {
      return publicUser(user);
    }
    if (segments[0] === 'orders') {
      return this.orders(method, segments, body, user);
    }
    if (segments[0] === 'payments') {
      return this.payments(method, segments, body, req, user);
    }
    if (segments[0] === 'demo' && segments[1] === 'payments' && segments[3] === 'pay') {
      return this.simulatePayment(segments[2], user);
    }
    if (segments[0] === 'subscriptions') {
      return this.subscriptions(method, segments, body, user);
    }
    throw new HttpError(404, 'not_found', 'Recurso não encontrado');
  }

  private register(body: Record<string, unknown>): User {
    const name = str(body['name']).trim();
    const email = str(body['email']).trim().toLowerCase();
    const password = str(body['password']);
    const document = str(body['document']).replace(/\D/g, '');
    const errors: Record<string, string> = {};
    if (name.length < 3) errors['name'] = 'informe o nome completo';
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) errors['email'] = 'e-mail inválido';
    if (password.length < 8) errors['password'] = 'a senha deve ter entre 8 e 72 caracteres';
    if (!isValidCpf(document)) errors['document'] = 'CPF inválido';
    if (Object.keys(errors).length) {
      throw new HttpError(400, 'validation_error', 'Dados inválidos', errors);
    }
    if (this.state.users.some((u) => u.email === email)) {
      throw new HttpError(409, 'email_in_use', 'E-mail já cadastrado');
    }
    const user: StoredUser = { id: uuid(), name, email, role: 'CUSTOMER', password, document };
    this.state.users.push(user);
    return publicUser(user);
  }

  private login(body: Record<string, unknown>): unknown {
    const email = str(body['email']).trim().toLowerCase();
    const user = this.state.users.find((u) => u.email === email && u.password === str(body['password']));
    if (!user) {
      throw new HttpError(401, 'invalid_credentials', 'E-mail ou senha inválidos');
    }
    return {
      accessToken: `demo.${user.id}`,
      tokenType: 'Bearer',
      expiresAt: new Date(Date.now() + 2 * 3600_000).toISOString(),
    };
  }

  private authenticate(req: DemoRequest): StoredUser {
    const header = req.headers.get('Authorization') ?? '';
    const id = header.startsWith('Bearer demo.') ? header.slice('Bearer demo.'.length) : '';
    const user = this.state.users.find((u) => u.id === id);
    if (!user) {
      throw new HttpError(401, 'unauthorized', 'Autenticação necessária');
    }
    return user;
  }

  private orders(method: string, segments: string[], body: Record<string, unknown>, user: StoredUser): unknown {
    const [, id, action] = segments;
    if (method === 'POST' && !id) {
      return strip(this.createOrder(body, user));
    }
    if (method === 'GET' && !id) {
      const mine = this.state.orders
        .filter((o) => o.customerId === user.id)
        .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
        .map(strip);
      return { content: mine, page: { size: 50, number: 0, totalElements: mine.length, totalPages: 1 } };
    }
    const order = this.findOrder(id, user);
    if (method === 'GET' && !action) {
      return strip(order);
    }
    if (method === 'GET' && action === 'payments') {
      return this.state.payments
        .filter((p) => p.orderId === order.id)
        .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
        .map(strip);
    }
    if (method === 'POST' && action === 'cancel') {
      if (this.hasActivePayment(order.id)) {
        throw new HttpError(409, 'payment_in_progress', 'Cancele o pagamento pendente antes de cancelar o pedido');
      }
      if (order.status !== 'AWAITING_PAYMENT') {
        throw new HttpError(422, 'order_not_cancelable', 'Somente pedidos aguardando pagamento podem ser cancelados');
      }
      this.setOrderStatus(order, 'CANCELED');
      return strip(order);
    }
    throw new HttpError(404, 'not_found', 'Recurso não encontrado');
  }

  private createOrder(body: Record<string, unknown>, user: StoredUser): State['orders'][number] {
    const items = Array.isArray(body['items']) ? (body['items'] as { productId: string; quantity: number }[]) : [];
    if (!items.length) {
      throw new HttpError(400, 'validation_error', 'Dados inválidos', { items: 'o pedido precisa de ao menos um item' });
    }
    const seen = new Set<string>();
    const now = new Date().toISOString();
    const order: State['orders'][number] = {
      id: uuid(),
      customerId: user.id,
      status: 'AWAITING_PAYMENT',
      total: 0,
      items: [],
      createdAt: now,
      updatedAt: now,
    };
    for (const item of items) {
      if (seen.has(item.productId)) {
        throw new HttpError(422, 'duplicated_item', 'Produto repetido no pedido');
      }
      seen.add(item.productId);
      const product = PRODUCTS.find((p) => p.id === item.productId && p.active);
      if (!product) {
        throw new HttpError(422, 'product_unavailable', 'Produto indisponível');
      }
      const quantity = Math.floor(Number(item.quantity));
      if (!(quantity >= 1 && quantity <= 100)) {
        throw new HttpError(400, 'validation_error', 'Dados inválidos', { quantity: 'quantidade entre 1 e 100' });
      }
      order.items.push({
        productId: product.id,
        name: product.name,
        unitPrice: product.price,
        quantity,
        subtotal: money(product.price * quantity),
      });
    }
    order.total = money(order.items.reduce((sum, i) => sum + i.subtotal, 0));
    this.state.orders.push(order);
    return order;
  }

  private payments(
    method: string,
    segments: string[],
    body: Record<string, unknown>,
    req: DemoRequest,
    user: StoredUser,
  ): unknown {
    const [, idOrMethod, action] = segments;
    if (method === 'POST' && ['pix', 'card', 'boleto'].includes(idOrMethod)) {
      return strip(this.createPayment(idOrMethod as 'pix' | 'card' | 'boleto', body, req, user));
    }
    const payment = this.state.payments.find((p) => p.id === idOrMethod && p.customerId === user.id);
    if (!payment) {
      throw new HttpError(404, 'not_found', 'Pagamento não encontrado');
    }
    if (method === 'GET' && !action) {
      return strip(this.expireIfNeeded(payment));
    }
    if (method === 'POST' && action === 'refresh') {
      return strip(this.expireIfNeeded(payment));
    }
    if (method === 'POST' && action === 'cancel') {
      if (payment.status !== 'PENDING' && payment.status !== 'IN_PROCESS') {
        throw new HttpError(422, 'payment_not_cancelable', 'Somente pagamentos pendentes podem ser cancelados');
      }
      this.transition(payment, 'CANCELED', 'by_collector');
      return strip(payment);
    }
    throw new HttpError(404, 'not_found', 'Recurso não encontrado');
  }

  private createPayment(
    kind: 'pix' | 'card' | 'boleto',
    body: Record<string, unknown>,
    req: DemoRequest,
    user: StoredUser,
  ): State['payments'][number] {
    const key = req.headers.get('Idempotency-Key') ?? '';
    if (!/^[A-Za-z0-9_-]{8,64}$/.test(key)) {
      throw new HttpError(422, 'invalid_idempotency_key', 'Idempotency-Key deve ter de 8 a 64 caracteres alfanuméricos');
    }
    const methodName: PaymentMethod = kind === 'pix' ? 'PIX' : kind === 'card' ? 'CREDIT_CARD' : 'BOLETO';
    const orderId = str(body['orderId']);

    const replay = this.state.payments.find((p) => p.customerId === user.id && p.idempotencyKey === key);
    if (replay) {
      if (replay.orderId !== orderId || replay.method !== methodName) {
        throw new HttpError(409, 'idempotency_key_reused', 'Chave de idempotência já utilizada em outra requisição');
      }
      return replay;
    }

    const order = this.findOrder(orderId, user);
    if (order.status !== 'AWAITING_PAYMENT') {
      throw new HttpError(422, 'order_not_payable', 'Pedido não está aguardando pagamento');
    }
    if (this.hasActivePayment(order.id)) {
      throw new HttpError(409, 'payment_in_progress', 'Já existe um pagamento em andamento para este pedido');
    }

    let installments = 1;
    if (kind === 'card') {
      installments = Math.floor(Number(body['installments']));
      if (!(installments >= 1 && installments <= MAX_INSTALLMENTS)) {
        throw new HttpError(422, 'invalid_installments', `Número de parcelas deve estar entre 1 e ${MAX_INSTALLMENTS}`);
      }
      if (Math.floor((order.total / installments) * 100) / 100 < MIN_INSTALLMENT) {
        throw new HttpError(422, 'installment_too_small', 'Valor mínimo por parcela é R$ 5,00');
      }
    }
    if (kind === 'boleto') {
      validateAddress(body['address']);
    }

    const now = new Date();
    const providerId = String(1325467000 + this.state.payments.length + 1);
    const payment: State['payments'][number] = {
      id: uuid(),
      orderId: order.id,
      customerId: user.id,
      idempotencyKey: key,
      method: methodName,
      status: 'PENDING',
      amount: order.total,
      installments,
      providerId,
      createdAt: now.toISOString(),
      updatedAt: now.toISOString(),
    };

    if (kind === 'pix') {
      payment.statusDetail = 'pending_waiting_transfer';
      payment.expiresAt = new Date(now.getTime() + 30 * 60_000).toISOString();
      payment.pix = { qrCode: pixCode(order.total, payment.id) };
    } else if (kind === 'boleto') {
      payment.statusDetail = 'pending_waiting_payment';
      payment.expiresAt = new Date(now.getTime() + 3 * 86_400_000).toISOString();
      payment.boleto = { url: '', digitableLine: boletoLine(order.total) };
    } else {
      const outcome = cardOutcome(str(body['token']));
      payment.status = outcome.status;
      payment.statusDetail = outcome.detail;
    }

    this.state.payments.push(payment);
    this.applyToOrder(payment);
    return payment;
  }

  private simulatePayment(id: string, user: StoredUser): unknown {
    const payment = this.state.payments.find((p) => p.id === id && p.customerId === user.id);
    if (!payment) {
      throw new HttpError(404, 'not_found', 'Pagamento não encontrado');
    }
    if (payment.status !== 'PENDING') {
      throw new HttpError(422, 'payment_not_pending', 'Este pagamento não está aguardando pagamento');
    }
    this.transition(payment, 'APPROVED', 'accredited');
    return strip(payment);
  }

  private subscriptions(method: string, segments: string[], body: Record<string, unknown>, user: StoredUser): unknown {
    const [, id, action] = segments;
    if (method === 'GET' && !id) {
      return this.state.subscriptions
        .filter((s) => s.customerId === user.id)
        .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
        .map(strip);
    }
    if (method === 'POST' && !id) {
      const amount = Number(body['amount']);
      const frequency = Math.floor(Number(body['frequency']));
      const type = body['frequencyType'] === 'DAYS' ? 'DAYS' : 'MONTHS';
      const outcome = cardOutcome(str(body['cardToken']));
      if (outcome.status !== 'APPROVED') {
        throw new HttpError(422, 'payment_provider_error', 'O cartão foi recusado ao criar a assinatura');
      }
      const now = new Date();
      const next = new Date(now);
      if (type === 'MONTHS') next.setMonth(next.getMonth() + frequency);
      else next.setDate(next.getDate() + frequency);
      const subscription: State['subscriptions'][number] = {
        id: uuid(),
        customerId: user.id,
        reason: str(body['reason']),
        amount: money(amount),
        frequency,
        frequencyType: type,
        status: 'AUTHORIZED',
        providerId: `2c9380848${Math.floor(Math.random() * 1e6)}`,
        nextPaymentDate: next.toISOString(),
        createdAt: now.toISOString(),
      };
      this.state.subscriptions.push(subscription);
      return strip(subscription);
    }
    const subscription = this.state.subscriptions.find((s) => s.id === id && s.customerId === user.id);
    if (!subscription) {
      throw new HttpError(404, 'not_found', 'Assinatura não encontrada');
    }
    if (method === 'GET' && !action) {
      return strip(subscription);
    }
    const allowed: Record<string, Subscription['status'][]> = {
      pause: ['AUTHORIZED'],
      resume: ['PAUSED'],
      cancel: ['PENDING', 'AUTHORIZED', 'PAUSED'],
    };
    const target: Record<string, Subscription['status']> = { pause: 'PAUSED', resume: 'AUTHORIZED', cancel: 'CANCELED' };
    if (method === 'POST' && action && allowed[action]) {
      if (!allowed[action].includes(subscription.status)) {
        throw new HttpError(422, 'invalid_subscription_transition', 'Ação indisponível para o status atual da assinatura');
      }
      subscription.status = target[action];
      return strip(subscription);
    }
    throw new HttpError(404, 'not_found', 'Recurso não encontrado');
  }

  private transition(payment: State['payments'][number], status: PaymentStatus, detail: string): void {
    payment.status = status;
    payment.statusDetail = detail;
    payment.updatedAt = new Date().toISOString();
    this.applyToOrder(payment);
  }

  private applyToOrder(payment: Payment): void {
    const order = this.state.orders.find((o) => o.id === payment.orderId);
    if (!order) return;
    if (payment.status === 'APPROVED') this.setOrderStatus(order, 'PAID');
  }

  private expireIfNeeded(payment: State['payments'][number]): State['payments'][number] {
    if (payment.status === 'PENDING' && payment.expiresAt && new Date(payment.expiresAt).getTime() < Date.now()) {
      this.transition(payment, 'CANCELED', 'expired');
    }
    return payment;
  }

  private setOrderStatus(order: Order, status: OrderStatus): void {
    order.status = status;
    order.updatedAt = new Date().toISOString();
  }

  private hasActivePayment(orderId: string): boolean {
    return this.state.payments.some(
      (p) => p.orderId === orderId && (p.status === 'PENDING' || p.status === 'IN_PROCESS' || p.status === 'APPROVED'),
    );
  }

  private findOrder(id: string, user: StoredUser): State['orders'][number] {
    const order = this.state.orders.find((o) => o.id === id && o.customerId === user.id);
    if (!order) {
      throw new HttpError(404, 'not_found', 'Pedido não encontrado');
    }
    return order;
  }

  private save(): void {
    storage.write(KEY, this.state);
  }
}

/**
 * Segue a convenção dos cartões de teste do Mercado Pago: o nome do titular define o resultado
 * (APRO aprovado, OTHE recusado, CONT pendente, FUND saldo insuficiente, SECU código inválido).
 */
function cardOutcome(token: string): { status: PaymentStatus; detail: string } {
  const holder = token.split(':')[1] ?? 'APRO';
  switch (holder) {
    case 'OTHE':
      return { status: 'REJECTED', detail: 'cc_rejected_other_reason' };
    case 'CONT':
      return { status: 'IN_PROCESS', detail: 'pending_contingency' };
    case 'FUND':
      return { status: 'REJECTED', detail: 'cc_rejected_insufficient_amount' };
    case 'SECU':
      return { status: 'REJECTED', detail: 'cc_rejected_bad_filled_security_code' };
    default:
      return { status: 'APPROVED', detail: 'accredited' };
  }
}

function validateAddress(raw: unknown): void {
  const address = (raw ?? {}) as Partial<Address>;
  const errors: Record<string, string> = {};
  if (!/^\d{5}-?\d{3}$/.test(address.zipCode ?? '')) errors['address.zipCode'] = 'CEP inválido';
  if (!address.street?.trim()) errors['address.street'] = 'informe a rua';
  if (!address.number?.trim()) errors['address.number'] = 'informe o número';
  if (!address.neighborhood?.trim()) errors['address.neighborhood'] = 'informe o bairro';
  if (!address.city?.trim()) errors['address.city'] = 'informe a cidade';
  if (!/^[A-Z]{2}$/.test(address.state ?? '')) errors['address.state'] = 'UF inválida';
  if (Object.keys(errors).length) {
    throw new HttpError(400, 'validation_error', 'Dados inválidos', errors);
  }
}

function pixCode(amount: number, id: string): string {
  const value = amount.toFixed(2);
  const txid = id.replace(/-/g, '').slice(0, 25).toUpperCase();
  return (
    '00020126580014br.gov.bcb.pix0136' +
    id +
    '520400005303986' +
    `54${String(value.length).padStart(2, '0')}${value}` +
    '5802BR5913PAYFLOW LTDA6009SAO PAULO' +
    `62${String(txid.length + 4).padStart(2, '0')}05${String(txid.length).padStart(2, '0')}${txid}` +
    '6304'
  );
}

function boletoLine(amount: number): string {
  const cents = Math.round(amount * 100).toString().padStart(10, '0');
  return `23793.38128 60000.000003 00000.000400 1 9999${cents}`;
}

function isValidCpf(cpf: string): boolean {
  if (!/^\d{11}$/.test(cpf) || /^(\d)\1{10}$/.test(cpf)) return false;
  const digit = (length: number) => {
    let sum = 0;
    for (let i = 0; i < length; i++) sum += Number(cpf[i]) * (length + 1 - i);
    const rest = (sum * 10) % 11;
    return rest === 10 ? 0 : rest;
  };
  return digit(9) === Number(cpf[9]) && digit(10) === Number(cpf[10]);
}

function publicUser(user: StoredUser): User {
  return { id: user.id, name: user.name, email: user.email, role: user.role };
}

function strip<T extends object>(value: T): Omit<T, 'customerId' | 'idempotencyKey'> {
  const { customerId: _c, idempotencyKey: _k, ...rest } = value as T & { customerId?: string; idempotencyKey?: string };
  return rest;
}

function isCreation(path: string): boolean {
  return ['/auth/register', '/orders', '/payments/pix', '/payments/card', '/payments/boleto', '/subscriptions'].includes(
    path,
  );
}

function money(value: number): number {
  return Math.round(value * 100) / 100;
}

function str(value: unknown): string {
  return typeof value === 'string' ? value : value == null ? '' : String(value);
}

function uuid(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) return crypto.randomUUID();
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
  });
}
