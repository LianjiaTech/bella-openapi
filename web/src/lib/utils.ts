import { clsx, type ClassValue } from "clsx"
import { twMerge } from "tailwind-merge"
import { getModelEndpoints } from '@/lib/api/meta';

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

export async function isChatCompletionsEndpoint(
    entityType: string,
    entityCode: string
): Promise<boolean> {
  if (entityType === 'model') {
    try {
      const endpoints = await getModelEndpoints(entityCode);
      return endpoints.includes('/v1/chat/completions');
    } catch (error) {
      return false;
    }
  } else if (entityType === 'endpoint') {
    return entityCode === '/v1/chat/completions';
  }
  return false;
}
