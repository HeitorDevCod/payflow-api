/** Tipos mínimos do SDK JS do Mercado Pago usados pelo Card Payment Brick. */

export interface CardBrickFormData {
  token: string;
  issuer_id: string | number | null;
  payment_method_id: string;
  transaction_amount: number;
  installments: number;
}

export interface BrickController {
  unmount(): void;
}

interface MercadoPagoInstance {
  bricks(): {
    create(
      type: 'cardPayment',
      containerId: string,
      settings: {
        initialization: { amount: number };
        customization?: { paymentMethods?: { maxInstallments?: number; minInstallments?: number } };
        callbacks: {
          onReady?: () => void;
          onSubmit: (data: CardBrickFormData) => Promise<void>;
          onError?: (error: unknown) => void;
        };
      },
    ): Promise<BrickController>;
  };
}

declare global {
  interface Window {
    MercadoPago?: new (publicKey: string, options?: { locale?: string }) => MercadoPagoInstance;
  }
}

const SDK_URL = 'https://sdk.mercadopago.com/js/v2';
let loading: Promise<void> | null = null;

export function loadMercadoPago(): Promise<void> {
  if (window.MercadoPago) {
    return Promise.resolve();
  }
  loading ??= new Promise<void>((resolve, reject) => {
    const script = document.createElement('script');
    script.src = SDK_URL;
    script.async = true;
    script.onload = () => resolve();
    script.onerror = () => {
      loading = null;
      reject(new Error('Não foi possível carregar o SDK do Mercado Pago'));
    };
    document.head.appendChild(script);
  });
  return loading;
}

export async function createCardBrick(options: {
  publicKey: string;
  containerId: string;
  amount: number;
  maxInstallments: number;
  onSubmit: (data: CardBrickFormData) => void;
  onError: (error: unknown) => void;
}): Promise<BrickController> {
  await loadMercadoPago();
  const MercadoPago = window.MercadoPago;
  if (!MercadoPago) {
    throw new Error('SDK do Mercado Pago indisponível');
  }
  const mp = new MercadoPago(options.publicKey, { locale: 'pt-BR' });
  return mp.bricks().create('cardPayment', options.containerId, {
    initialization: { amount: options.amount },
    customization: { paymentMethods: { maxInstallments: options.maxInstallments } },
    callbacks: {
      onSubmit: async (data) => options.onSubmit(data),
      onError: options.onError,
    },
  });
}
