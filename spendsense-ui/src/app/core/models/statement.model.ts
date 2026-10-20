export interface Transaction {
  id: number;
  statementId: number;
  date: string;
  description: string;
  amount: number;
  type: 'DEBIT' | 'CREDIT';
  aiCategory: string | null;
  userCategory: string | null;
  isOverridden: boolean;
  effectiveCategory: string | null;
}

export interface Statement {
  id: number;
  filename: string;
  bankType: string;
  status: 'PENDING' | 'PARSED' | 'CATEGORIZED' | 'FAILED';
  uploadedAt: string;
  userId: number;
}

export interface SummaryResponse {
  totalSpend: number;
  transactionCount: number;
  topCategory: string;
  spendByCategory: Record<string, number>;
}

export interface MonthlySpend {
  /** ISO month, e.g. "2024-01" */
  month: string;
  totalSpend: number;
}

/** Extracts a human-readable message from a Spring error response */
export function apiErrorMessage(err: unknown, fallback: string): string {
  const body = (err as { error?: { message?: string } })?.error;
  return body?.message && body.message !== 'No message available' ? body.message : fallback;
}

export const CATEGORIES = [
  'Groceries',
  'Dining',
  'Utilities',
  'Transport',
  'Entertainment',
  'Health',
  'Shopping',
  'Income',
  'Other',
  'Miscellaneous',
] as const;

export type Category = typeof CATEGORIES[number];
