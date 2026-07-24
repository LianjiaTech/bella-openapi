import { apiClient } from '@/lib/api/client';
import { BillingAkOption, BillingRecordCondition, BillingRecordPage } from '@/lib/types/billing';

export async function getBillingAkOptions(): Promise<BillingAkOption[]> {
  const response = await apiClient.get('/console/billing/ak-options');
  return response as unknown as BillingAkOption[];
}

export async function queryBillingRecords(condition: BillingRecordCondition): Promise<BillingRecordPage> {
  const response = await apiClient.post('/console/billing/records', condition);
  return response as unknown as BillingRecordPage;
}

export async function queryAdminBillingRecords(condition: BillingRecordCondition): Promise<BillingRecordPage> {
  const response = await apiClient.post('/console/billing/admin/records', condition);
  return response as unknown as BillingRecordPage;
}
