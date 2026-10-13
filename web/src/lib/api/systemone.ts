import { apiClient } from './client'

export interface SystemOneRequest {
  model: string
  state: string
  questions?: Record<string, unknown>
  user?: string
}

export interface SystemOneResponse {
  id?: string
  model?: string
  result?: unknown
  usage?: { input_tokens?: number; output_tokens?: number }
  [key: string]: unknown
}

export function evaluateSystemOne(request: SystemOneRequest): Promise<SystemOneResponse> {
  return apiClient.post<SystemOneResponse, SystemOneResponse>('/v1/systemone', request)
}
