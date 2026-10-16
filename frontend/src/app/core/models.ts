export interface Product {
  id: string;
  name: string;
  description: string | null;
  price: number;
  active: boolean;
}

export interface Page<T> {
  content: T[];
  page: { size: number; number: number; totalElements: number; totalPages: number };
}

export interface User {
  id: string;
  name: string;
  email: string;
  role: 'CUSTOMER' | 'ADMIN';
}

export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresAt: string;
}

export type OrderStatus = 'AWAITING_PAYMENT' | 'PAYMENT_REVIEW' | 'PAID' | 'REFUNDED' | 'CANCELED';

export interface OrderItem {
  productId: string;
  name: string;
  unitPrice: number;
  quantity: number;
  subtotal: number;
}

export interface Order {
  id: string;
  status: OrderStatus;
  total: number;
  items: OrderItem[];
  createdAt: string;
  updatedAt: string;
}

export type PaymentMethod = 'PIX' | 'CREDIT_CARD' | 'BOLETO';

export type PaymentStatus =
  | 'PENDING'
  | 'IN_PROCESS'
  | 'APPROVED'
  | 'REJECTED'
  | 'CANCELED'
  | 'REFUNDED'
  | 'CHARGED_BACK'
  | 'FAILED';

export interface Payment {
  id: string;
  orderId: string;
  method: PaymentMethod;
  status: PaymentStatus;
  statusDetail?: string;
  amount: number;
  installments: number;
  providerId?: string;
  pix?: { qrCode: string; qrCodeBase64?: string; ticketUrl?: string };
  boleto?: { url: string; digitableLine?: string };
  expiresAt?: string;
  createdAt: string;
  updatedAt: string;
}

export type SubscriptionStatus = 'PENDING' | 'AUTHORIZED' | 'PAUSED' | 'CANCELED';

export interface Subscription {
  id: string;
  reason: string;
  amount: number;
  frequency: number;
  frequencyType: 'DAYS' | 'MONTHS';
  status: SubscriptionStatus;
  providerId?: string;
  nextPaymentDate?: string;
  createdAt: string;
}

export interface PublicConfig {
  mercadoPagoPublicKey: string | null;
  demo: boolean;
}

export interface CardData {
  token: string;
  paymentMethodId: string;
  issuerId: number | null;
  installments: number;
}

export interface Address {
  zipCode: string;
  street: string;
  number: string;
  neighborhood: string;
  city: string;
  state: string;
}

export interface Problem {
  status?: number;
  detail?: string;
  code?: string;
  errors?: Record<string, string>;
}
