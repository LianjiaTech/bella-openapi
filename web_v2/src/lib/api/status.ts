import { Model } from '@/lib/types/openapi';
import { apiClient } from '@/lib/api/client';

/**
 * 获取模型列表(用于状态页面筛选)
 * @param endpoint 端点类型,例如 '/v1/embeddings'
 * @param status 模型状态,默认为 'active'
 * @returns 模型列表
 */
export async function getModelsForSelection(
  endpoint?: string,
  status: string = 'active'
): Promise<Model[]> {
  const response = await apiClient.get<Model[], Model[]>(
    '/v1/meta/model/list/for-selection',
    {
      params: {
        endpoint,
        status
      },
    }
  );
  return response || [];
}


