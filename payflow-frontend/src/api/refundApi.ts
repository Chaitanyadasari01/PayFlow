import { apiClient } from './client';
import type { Refund } from '../types';

export const refundApi = {
  getRefundById: async (refundId: string): Promise<Refund> => {
    const res = await apiClient.get<Refund>(`/api/refunds/${refundId}`);
    return res.data;
  },
};
