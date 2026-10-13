import { get } from './client';

export async function fetchLogDetail(requestId: string, shardPath: string): Promise<Record<string, unknown>> {
  return get<Record<string, unknown>>('/v1/log/detail', { requestId, shardPath });
}
