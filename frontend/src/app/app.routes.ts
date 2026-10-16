import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';

export const routes: Routes = [
  {
    path: '',
    title: 'Payflow · Loja',
    loadComponent: () => import('./pages/store/store.page').then((m) => m.StorePage),
  },
  {
    path: 'entrar',
    title: 'Payflow · Entrar',
    loadComponent: () => import('./pages/auth/login.page').then((m) => m.LoginPage),
  },
  {
    path: 'cadastro',
    title: 'Payflow · Criar conta',
    loadComponent: () => import('./pages/auth/register.page').then((m) => m.RegisterPage),
  },
  {
    path: 'checkout',
    title: 'Payflow · Pagamento',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/checkout/checkout.page').then((m) => m.CheckoutPage),
  },
  {
    path: 'pagamentos/:id',
    title: 'Payflow · Pagamento',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/payment/payment.page').then((m) => m.PaymentPage),
  },
  {
    path: 'pedidos',
    title: 'Payflow · Meus pedidos',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/orders/orders.page').then((m) => m.OrdersPage),
  },
  {
    path: 'pedidos/:id',
    title: 'Payflow · Pedido',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/orders/order-detail.page').then((m) => m.OrderDetailPage),
  },
  {
    path: 'assinaturas',
    title: 'Payflow · Assinaturas',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/subscriptions/subscriptions.page').then((m) => m.SubscriptionsPage),
  },
  { path: '**', redirectTo: '' },
];
