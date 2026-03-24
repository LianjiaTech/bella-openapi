import type { StreamStatus } from '@/lib/types/chat';

export type ChatInputState = {
  value: string;
  isComposing: boolean;
  status: StreamStatus;
}

export type ChatInputController = {
  value: string;
  setValue(v: string): void;
  canSend: boolean;
  send(): Promise<void>;
  abort(): void;
  status: StreamStatus;
}