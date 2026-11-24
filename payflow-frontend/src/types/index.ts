export type PaymentStatus =
  | 'PENDING'
  | 'PROCESSING'
  | 'SUCCESS'
  | 'FAILED'
  | 'REFUND_PENDING'
  | 'PARTIALLY_REFUNDED'
  | 'REFUNDED';

export type RefundStatus = 'PENDING' | 'SUCCESS' | 'FAILED';

export type WebhookStatus = 'PENDING' | 'DELIVERED' | 'FAILED';

export interface User {
  email: string;
  fullName: string;
}

export interface AuthResponse {
  token: string;
  tokenType: string;
  expiresIn: number;
  email: string;
  fullName: string;
}

export interface Payment {
  paymentId: string;
  amount: number;
  currency: string;
  status: PaymentStatus;
  description: string | null;
  paymentMethod: string;
  providerTransactionId: string | null;
  failureReason: string | null;
  createdAt: string;
  updatedAt: string;
  metadata?: Record<string, unknown>;
}

export interface CreatePaymentRequest {
  amount: number;
  currency: string;
  paymentMethod: string;
  description?: string;
  metadata?: Record<string, unknown>;
}

export interface Refund {
  refundId: string;
  paymentId: string;
  amount: number;
  reason: string | null;
  status: RefundStatus;
  providerRefundId: string | null;
  createdAt: string;
}

export interface CreateRefundRequest {
  amount: number;
  reason?: string;
}

export interface WebhookEvent {
  eventId: string;
  eventType: string;
  paymentId: string | null;
  payload: string;
  status: WebhookStatus;
  attempts: number;
  nextRetryAt: string | null;
  lastAttemptedAt: string | null;
  createdAt: string;
}

export interface PagedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

export interface ApiError {
  status: number;
  error: string;
  message: string;
  path: string;
  timestamp: string;
  validationErrors?: Record<string, string>;
}
