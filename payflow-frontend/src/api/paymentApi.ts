import { apiClient } from './client';
import type { CreatePaymentRequest, CreateRefundRequest, PagedResponse, Payment, Refund } from '../types';

export interface ListPaymentsParams {
  page?: number;
  size?: number;
  sort?: string;
}

export const paymentApi = {
  createPayment: async (
    data: CreatePaymentRequest,
    idempotencyKey?: string
  ): Promise<Payment> => {
    const headers: Record<string, string> = {};
    if (idempotencyKey && idempotencyKey.trim().length > 0) {
      headers['Idempotency-Key'] = idempotencyKey.trim();
    }
    const res = await apiClient.post<Payment>('/api/payments', data, { headers });
    return res.data;
  },

  getPayment: async (paymentId: string): Promise<Payment> => {
    const res = await apiClient.get<Payment>(`/api/payments/${paymentId}`);
    return res.data;
  },

  listPayments: async (params?: ListPaymentsParams): Promise<PagedResponse<Payment>> => {
    const res = await apiClient.get<PagedResponse<Payment>>('/api/payments', {
      params: {
        page: params?.page ?? 0,
        size: params?.size ?? 10,
        sort: params?.sort ?? 'createdAt,desc',
      },
    });
    return res.data;
  },

  createRefund: async (
    paymentId: string,
    data: CreateRefundRequest
  ): Promise<Refund> => {
    const res = await apiClient.post<Refund>(`/api/payments/${paymentId}/refunds`, data);
    return res.data;
  },

  listRefundsForPayment: async (paymentId: string): Promise<Refund[]> => {
    const res = await apiClient.get<Refund[]>(`/api/payments/${paymentId}/refunds`);
    return res.data;
  },
};
