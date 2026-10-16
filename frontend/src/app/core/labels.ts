import { OrderStatus, PaymentMethod, PaymentStatus, SubscriptionStatus } from './models';

export type Tone = 'ok' | 'wait' | 'bad' | 'muted';

export const orderStatus: Record<OrderStatus, { label: string; tone: Tone }> = {
  AWAITING_PAYMENT: { label: 'Aguardando pagamento', tone: 'wait' },
  PAYMENT_REVIEW: { label: 'Em análise', tone: 'wait' },
  PAID: { label: 'Pago', tone: 'ok' },
  REFUNDED: { label: 'Estornado', tone: 'muted' },
  CANCELED: { label: 'Cancelado', tone: 'muted' },
};

export const paymentStatus: Record<PaymentStatus, { label: string; tone: Tone }> = {
  PENDING: { label: 'Aguardando pagamento', tone: 'wait' },
  IN_PROCESS: { label: 'Em processamento', tone: 'wait' },
  APPROVED: { label: 'Aprovado', tone: 'ok' },
  REJECTED: { label: 'Recusado', tone: 'bad' },
  CANCELED: { label: 'Cancelado', tone: 'muted' },
  REFUNDED: { label: 'Estornado', tone: 'muted' },
  CHARGED_BACK: { label: 'Contestado', tone: 'bad' },
  FAILED: { label: 'Não enviado', tone: 'bad' },
};

export const subscriptionStatus: Record<SubscriptionStatus, { label: string; tone: Tone }> = {
  PENDING: { label: 'Pendente', tone: 'wait' },
  AUTHORIZED: { label: 'Ativa', tone: 'ok' },
  PAUSED: { label: 'Pausada', tone: 'muted' },
  CANCELED: { label: 'Cancelada', tone: 'muted' },
};

export const paymentMethod: Record<PaymentMethod, string> = {
  PIX: 'Pix',
  CREDIT_CARD: 'Cartão de crédito',
  BOLETO: 'Boleto',
};

/** Explicações dos motivos de recusa mais comuns do Mercado Pago. */
const rejectionReasons: Record<string, string> = {
  cc_rejected_insufficient_amount: 'Saldo ou limite insuficiente.',
  cc_rejected_bad_filled_security_code: 'Código de segurança incorreto.',
  cc_rejected_bad_filled_date: 'Data de validade incorreta.',
  cc_rejected_bad_filled_other: 'Algum dado do cartão está incorreto.',
  cc_rejected_call_for_authorize: 'O banco pediu autorização. Fale com o emissor do cartão.',
  cc_rejected_card_disabled: 'Cartão desativado. Fale com o emissor.',
  cc_rejected_duplicated_payment: 'Pagamento duplicado com o mesmo valor.',
  cc_rejected_high_risk: 'Pagamento recusado pela análise de risco.',
  cc_rejected_other_reason: 'O emissor do cartão recusou o pagamento.',
};

export function rejectionReason(detail: string | undefined): string {
  if (!detail) {
    return 'O pagamento não foi aprovado.';
  }
  return rejectionReasons[detail] ?? 'O pagamento não foi aprovado.';
}

export function newIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID();
  }
  return `k-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`;
}
