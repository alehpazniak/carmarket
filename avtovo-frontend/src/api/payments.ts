import api from './client';
import type {CreatePaymentResponse, Payment, PaymentProduct} from '../types';

/** Price list (public). */
export async function getPaymentProducts(): Promise<PaymentProduct[]> {
    const res = await api.get('/api/payments/products');
    return res.data;
}

/** Registers a Przelewy24 transaction. The caller must send the browser to redirectUrl. */
export async function createPayment(productCode: string, referenceId?: string): Promise<CreatePaymentResponse> {
    const res = await api.post('/api/payments', {productCode, referenceId});
    return res.data;
}

/** Creates the payment and leaves the app for the Przelewy24 payment page. */
export async function startPayment(productCode: string, referenceId?: string): Promise<void> {
    const {redirectUrl} = await createPayment(productCode, referenceId);
    window.location.assign(redirectUrl);
}

export async function getPayment(id: string): Promise<Payment> {
    const res = await api.get(`/api/payments/${id}`);
    return res.data;
}

/** Asks the backend to check the transaction at Przelewy24 (used right after returning from P24). */
export async function syncPayment(id: string): Promise<Payment> {
    const res = await api.post(`/api/payments/${id}/sync`);
    return res.data;
}

export async function getMyPayments(): Promise<Payment[]> {
    const res = await api.get('/api/payments/my');
    return res.data;
}

export function formatAmount(amount: number, currency: string): string {
    return new Intl.NumberFormat('pl-PL', {style: 'currency', currency}).format(amount / 100);
}
