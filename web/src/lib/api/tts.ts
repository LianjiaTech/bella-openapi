import { apiClient } from './client';

/**
 * TTS 请求参数
 */
export interface SpeechRequest {
  model: string;
  input: string;
  voice?: string;
  response_format?: string;
  speed?: number;
  sample_rate?: number;
  user?: string;
  stream?: boolean;
}

/**
 * 调用 TTS API 生成语音
 * @returns Blob 音频数据
 */
export async function generateSpeech(request: SpeechRequest): Promise<Blob> {
  // 注：第一个泛型参数为any，因为Blob响应不需要响应体类型推断
  return apiClient.post<any, Blob>('/v1/audio/speech', request, {
    responseType: 'blob',
  });
}
