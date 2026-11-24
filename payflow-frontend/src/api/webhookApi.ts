import { apiClient } from './client';
import type { WebhookEvent } from '../types';

export interface WebhookRetryResult {
  processedEventsCount: number;
  message: string;
}

export const webhookApi = {
  getEventsForPayment: async (paymentId: string): Promise<WebhookEvent[]> => {
    const res = await apiClient.get<WebhookEvent[]>(`/api/webhooks/payments/${paymentId}`);
    return res.data;
  },

  triggerRetry: async (): Promise<WebhookRetryResult> => {
    const res = await apiClient.post<WebhookRetryResult>('/api/webhooks/retry');
    return res.data;
  },
};
