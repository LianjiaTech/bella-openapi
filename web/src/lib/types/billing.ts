import { Page } from './openapi';

export type BillingGranularity = 'month' | 'day';
export type BillingScope = 'all' | 'parent' | 'current' | `child:${string}`;

export interface BillingAkOption {
  code: string;
  name?: string;
  akDisplay?: string;
  ownerType: string;
  ownerName?: string;
  parentCode?: string;
  children?: BillingAkOption[];
  hasChildren: boolean;
  directPermission: boolean;
}

export interface BillingRecordCondition {
  granularity: BillingGranularity;
  akCodes: string[];
  startPt: string;
  endPt: string;
  endpoints?: string[];
  model?: string;
  page: number;
  size: number;
}

export interface BillingRecord {
  pt: string;
  endpoint: string;
  model: string;
  accountType: string;
  accountCode: string;
  akCode: string;
  amount: number;
}

export interface BillingRecordPage extends Page<BillingRecord> {
  pageSize: number;
  totalAmount: number;
}
