import { apiClient } from './client';

export interface RerankRequest {
  model: string;
  query: string;
  documents: string[];
  top_n?: number;
  instruct?: string;
  user?: string;
}

export interface RerankResult {
  index: number;
  document?: {
    text?: string;
    multi_modal?: unknown;
  } | string | null;
  relevance_score: number;
}

export interface RerankResponse {
  id?: string;
  object?: string;
  model?: string;
  request_id?: string;
  results?: RerankResult[];
  usage?: {
    total_tokens?: number;
  };
}

export async function rerank(request: RerankRequest): Promise<RerankResponse> {
  return apiClient.post<RerankResponse, RerankResponse>('/v1/reranks', request);
}
