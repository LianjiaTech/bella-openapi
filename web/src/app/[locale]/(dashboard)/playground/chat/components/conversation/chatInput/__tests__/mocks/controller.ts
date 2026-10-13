/**
 * Mock ChatInputController
 *
 * 职责:
 * - 创建测试用的 controller 对象
 * - 提供可配置的 mock 函数
 */

import type { ChatInputController } from '../../../types';

/**
 * 创建 Mock Controller
 *
 * @param overrides - 覆盖默认值的配置
 * @returns Mock ChatInputController 对象
 */
export const createMockController = (
  overrides?: Partial<ChatInputController>
): ChatInputController => ({
  value: '',
  setValue: jest.fn(),
  canSend: false,
  send: jest.fn(),
  abort: jest.fn(),
  status: 'idle',
  ...overrides,
});

/**
 * 创建带有文本内容的 controller
 */
export const createControllerWithText = (text: string): ChatInputController =>
  createMockController({
    value: text,
    canSend: text.trim().length > 0,
  });

/**
 * 创建带有 ContentPart[] 的 controller
 */
export const createControllerWithContent = (
  content: Array<any>
): ChatInputController =>
  createMockController({
    value: content,
    canSend: content.length > 0,
  });

/**
 * 创建 streaming 状态的 controller
 */
export const createStreamingController = (): ChatInputController =>
  createMockController({
    status: 'streaming',
  });
