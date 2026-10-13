import type { StreamStatus } from '@/lib/types/chat';

/**
 * 内容块类型定义(与 useChatInput.ts 保持一致)
 */
export type ContentPart =
  | { type: 'text'; text: string }
  | { type: 'image_url'; image_url: { url: string } }
  | { type: 'video_url'; video_url: { url: string }, fps?: string }

export type ChatInputState = {
  value: string | ContentPart[];
  isComposing: boolean;
  status: StreamStatus;
}

export type ChatInputController = {
  value: string | ContentPart[];
  setValue(v: string | ContentPart[]): void;
  canSend: boolean;
  send(): Promise<void>;
  abort(): void;
  status: StreamStatus;
}