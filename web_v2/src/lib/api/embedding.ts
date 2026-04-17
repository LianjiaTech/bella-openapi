import { apiClient } from './client';

/**
 * Embedding 请求参数
 */
export interface EmbeddingRequest {
  model: string;
  input: string | string[];
  encoding_format?: 'float' | 'base64';
  dimensions?: number;
  user?: string;
}

/**
 * Embedding 数据项
 */
export interface EmbeddingData {
  object: string;
  embedding: number[] | string; // float 格式返回数组，base64 格式返回字符串
  index: number;
}

/**
 * Embedding 响应
 */
export interface EmbeddingResponse {
  object: string;
  data: EmbeddingData[];
  model: string;
  usage: {
    prompt_tokens: number;
    total_tokens: number;
  };
}

/**
 * 调用 embedding API
 */
export async function createEmbedding(request: EmbeddingRequest): Promise<EmbeddingResponse> {
  return apiClient.post<EmbeddingResponse, EmbeddingResponse>('/v1/embeddings', request);
}