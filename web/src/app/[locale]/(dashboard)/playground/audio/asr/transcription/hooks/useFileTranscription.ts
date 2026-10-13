'use client';

import { useState, useRef, useCallback } from 'react';
import {
  uploadAudioFile,
  submitTranscriptionTask,
  queryTranscriptionResult,
  TranscriptionTaskResult,
} from '@/lib/api/audioTranscription';

export type TranscriptionStatus = 'idle' | 'uploading' | 'submitting' | 'polling' | 'done' | 'error';

interface TranscriptionOptions {
  language?: string;
  speaker_diarization?: boolean;
  enable_words?: boolean;
  max_end_silence?: number;
}

interface UseFileTranscriptionReturn {
  status: TranscriptionStatus;
  taskId: string | null;
  result: TranscriptionTaskResult | null;
  error: string | null;
  submit: (input: File | string, model: string, options?: TranscriptionOptions) => void;
  reset: () => void;
}

const POLL_INITIAL_INTERVAL = 2000;
const POLL_BACKOFF_FACTOR = 1.5;
const POLL_MAX_INTERVAL = 10000;
const POLL_TIMEOUT = 5 * 60 * 1000;

export function useFileTranscription(): UseFileTranscriptionReturn {
  const [status, setStatus] = useState<TranscriptionStatus>('idle');
  const [taskId, setTaskId] = useState<string | null>(null);
  const [result, setResult] = useState<TranscriptionTaskResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const pollingRef = useRef(false);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const stopPolling = useCallback(() => {
    pollingRef.current = false;
    if (timerRef.current) {
      clearTimeout(timerRef.current);
      timerRef.current = null;
    }
  }, []);

  const startPolling = useCallback((id: string) => {
    pollingRef.current = true;
    setStatus('polling');
    let interval = POLL_INITIAL_INTERVAL;
    const startTime = Date.now();

    const poll = async () => {
      if (!pollingRef.current) return;

      if (Date.now() - startTime > POLL_TIMEOUT) {
        pollingRef.current = false;
        setStatus('error');
        setError('Polling timeout: task took too long');
        return;
      }

      try {
        const resp = await queryTranscriptionResult([id]);

        const results = Array.isArray(resp)
          ? resp
          : (resp && typeof resp === 'object' && Array.isArray((resp as any).data))
            ? (resp as any).data
            : [];

        const taskResult = results.find((r: any) => r.task_id === id);
        if (taskResult) {
          pollingRef.current = false;
          setResult(taskResult);
          setStatus('done');
          return;
        }

        const fileResult = results.find((r: any) => r.file_id && !r.task_id);
        if (fileResult) {
          pollingRef.current = false;
          setResult({ task_id: id, output_file_id: fileResult.file_id });
          setStatus('done');
          return;
        }
      } catch (err: any) {
        console.warn('[Polling] query error, retrying...', err.message);
      }

      if (!pollingRef.current) return;
      interval = Math.min(interval * POLL_BACKOFF_FACTOR, POLL_MAX_INTERVAL);
      timerRef.current = setTimeout(poll, interval);
    };

    timerRef.current = setTimeout(poll, interval);
  }, []);

  const submit = useCallback(async (input: File | string, model: string, options?: TranscriptionOptions) => {
    stopPolling();
    setResult(null);
    setError(null);
    setTaskId(null);

    try {
      let url: string;

      if (input instanceof File) {
        setStatus('uploading');
        const uploadResp = await uploadAudioFile(input);
        url = typeof uploadResp === 'string' ? uploadResp : uploadResp.url;
      } else {
        url = input;
      }

      setStatus('submitting');
      const resp = await submitTranscriptionTask({
        url,
        model,
        ...options,
      });

      const id = resp.task_id;
      setTaskId(id);
      startPolling(id);
    } catch (err: any) {
      setStatus('error');
      setError(err.message || 'Unknown error');
    }
  }, [stopPolling, startPolling]);

  const reset = useCallback(() => {
    stopPolling();
    setStatus('idle');
    setTaskId(null);
    setResult(null);
    setError(null);
  }, [stopPolling]);

  return { status, taskId, result, error, submit, reset };
}
