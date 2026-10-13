import { apiClient } from './client';

interface FileUploadResp {
  id: string;
}

interface FileUrlResp {
  url: string;
}

export async function uploadAudioFile(file: File): Promise<{ url: string }> {
  // Step 1: 上传文件到 /v1/files
  const formData = new FormData();
  formData.append('file', file);
  formData.append('purpose', 'temp');
  const { id: fileId } = await apiClient.post<FileUploadResp, FileUploadResp>('/v1/files', formData);

  // Step 2: 通过 fileId 获取文件 URL
  const { url } = await apiClient.get<FileUrlResp, FileUrlResp>(`/v1/files/${fileId}/url`);
  return { url };
}

export async function submitTranscriptionTask(params: {
  url: string;
  model: string;
  user?: string;
  callback_url?: string;
  language?: string;
  speaker_diarization?: boolean;
  enable_words?: boolean;
  max_end_silence?: number;
}): Promise<{ task_id: string }> {
  return apiClient.post('/v1/audio/transcriptions/file', {
    callback_url: '',
    user: 'playground',
    ...params,
  });
}

export interface TranscriptionTaskResult {
  task_id: string;
  task?: string;
  text?: string;
  language?: string;
  duration?: number;
  words?: any[];
  segments?: any[];
  output_file_id?: string;
  [key: string]: any;
}

/**
 * 查询转录结果
 */
export async function queryTranscriptionResult(taskIds: string[]): Promise<any> {
  return apiClient.post('/v1/audio/transcriptions/file/result', {
    task_id: taskIds,
  });
}
