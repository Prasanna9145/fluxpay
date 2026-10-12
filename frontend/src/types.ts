export type PaymentStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';

export interface Payment {
  id: string;
  amount: number;
  currency: string;
  status: PaymentStatus;
  customerEmail: string;
  createdAt: string;
  idempotencyKey?: string;
}

export interface LedgerEntry {
  id: string;
  entryGroupId: string;
  paymentId: string;
  entryKey: string;
  accountCode: string;
  direction: 'DEBIT' | 'CREDIT';
  amount: number;
  currency: string;
  createdAt: string;
}

export interface WebhookEndpoint {
  id: string;
  targetUrl: string;
  active: boolean;
  createdAt: string;
}

export interface WebhookDelivery {
  id: string;
  endpointId: string;
  targetUrl: string;
  eventType: string;
  paymentId: string;
  status: 'PENDING' | 'RETRYING' | 'DELIVERED' | 'FAILED';
  attempts: number;
  responseStatus?: number | null;
  lastError?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AuditEvent {
  id: string;
  actor: string;
  action: string;
  resourceType: string;
  resourceId?: string | null;
  details?: string | null;
  createdAt: string;
}

export interface DashboardSummary {
  totalPayments: number;
  countByStatus: Record<PaymentStatus, number>;
  completedVolumeByCurrency: Record<string, number>;
}
