/**
 * ChatInput - 组件集成测试
 *
 * 测试范围:
 * - 组件渲染
 * - UI 交互
 * - 与现有功能兼容性
 * - hasContent 逻辑
 */

import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ChatInput } from '../index';
import {
  createMockController,
  createControllerWithText,
  createControllerWithContent,
  createStreamingController,
  mockImageFile,
  mockFileReader,
} from './mocks';

describe('ChatInput - 组件集成测试', () => {
  let cleanup: () => void;

  beforeEach(() => {
    cleanup = mockFileReader('data:image/png;base64,mockBase64String');
  });

  afterEach(() => {
    cleanup();
    jest.clearAllMocks();
  });

  describe('组件渲染', () => {
    test('1. 组件正常渲染', () => {
      const controller = createMockController();
      render(<ChatInput controller={controller} />);

      // 验证核心元素存在
      expect(screen.getByPlaceholderText(/输入消息/i)).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /添加/i })).toBeInTheDocument();
    });

    test('2. 下拉菜单包含上传选项', async () => {
      const controller = createMockController();
      render(<ChatInput controller={controller} />);

      // 点击 Plus 按钮
      const plusButton = screen.getByRole('button', { name: /添加/i });
      await userEvent.click(plusButton);

      // 验证菜单项
      expect(screen.getByText('添加图片')).toBeInTheDocument();
      expect(screen.getByText('添加视频')).toBeInTheDocument();
    });

    test('3. 隐藏的 file input 存在', () => {
      const controller = createMockController();
      render(<ChatInput controller={controller} />);

      const fileInput = document.querySelector('input[type="file"]');
      expect(fileInput).toBeInTheDocument();
      expect(fileInput).toHaveClass('hidden');
    });
  });

  describe('内容判断逻辑', () => {
    test('4. 空字符串 → hasContent = false, 按钮禁用', () => {
      const controller = createMockController({ value: '' });
      render(<ChatInput controller={controller} />);

      const sendButton = document.querySelector('.bg-gray-400');
      expect(sendButton).toBeInTheDocument();
      expect(sendButton).toHaveClass('cursor-not-allowed');
    });

    test('5. 纯空格 → hasContent = false, 按钮禁用', () => {
      const controller = createMockController({ value: '   ' });
      render(<ChatInput controller={controller} />);

      const sendButton = document.querySelector('.bg-gray-400');
      expect(sendButton).toBeInTheDocument();
    });

    test('6. 有效文本 → hasContent = true, 按钮启用', () => {
      const controller = createControllerWithText('Hello');
      render(<ChatInput controller={controller} />);

      const sendButton = document.querySelector('.bg-blue-500');
      expect(sendButton).toBeInTheDocument();
      expect(sendButton).toHaveClass('cursor-pointer');
    });

    test('7. 空数组 → hasContent = false', () => {
      const controller = createControllerWithContent([]);
      render(<ChatInput controller={controller} />);

      const sendButton = document.querySelector('.bg-gray-400');
      expect(sendButton).toBeInTheDocument();
    });

    test('8. 有内容的数组 → hasContent = true', () => {
      const controller = createControllerWithContent([
        { type: 'image_url', image_url: { url: 'data:image/png;base64,test' } },
      ]);
      render(<ChatInput controller={controller} />);

      const sendButton = document.querySelector('.bg-blue-500');
      expect(sendButton).toBeInTheDocument();
    });
  });

  describe('上传与输入兼容性', () => {
    test('9. 上传图片后不影响 Textarea 的可用性', async () => {
      const controller = createMockController();
      render(<ChatInput controller={controller} />);

      const textarea = screen.getByPlaceholderText(/输入消息/i) as HTMLTextAreaElement;
      const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;

      // 上传图片
      const file = mockImageFile();
      fireEvent.change(fileInput, { target: { files: [file] } });

      await waitFor(() => {
        expect(controller.setValue).toHaveBeenCalled();
      });

      // Textarea 应该仍然可以交互
      expect(textarea).not.toBeDisabled();
    });

    test('10. value 为数组时, Textarea 显示为空', () => {
      const controller = createControllerWithContent([
        { type: 'text', text: 'Hello' },
        { type: 'image_url', image_url: { url: 'data:image/png;base64,test' } },
      ]);
      render(<ChatInput controller={controller} />);

      const textarea = screen.getByPlaceholderText(/输入消息/i) as HTMLTextAreaElement;
      // 当 value 是数组时, Textarea 显示为空字符串
      expect(textarea.value).toBe('');
    });

    test('11. 在 Textarea 输入文本应该调用 setValue', () => {
      const controller = createMockController();
      render(<ChatInput controller={controller} />);

      const textarea = screen.getByPlaceholderText(/输入消息/i);
      fireEvent.change(textarea, { target: { value: 'New text' } });

      expect(controller.setValue).toHaveBeenCalledWith('New text');
    });
  });

  describe('发送功能', () => {
    test('12. 上传文件后发送按钮应该启用', async () => {
      const controller = createMockController();
      render(<ChatInput controller={controller} />);

      const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
      const file = mockImageFile();

      fireEvent.change(fileInput, { target: { files: [file] } });

      await waitFor(() => {
        expect(controller.setValue).toHaveBeenCalled();
      });

      // 模拟 value 更新后的状态
      const result = (controller.setValue as jest.Mock).mock.calls[0][0];
      const updatedController = createControllerWithContent(result);
      const { rerender } = render(<ChatInput controller={updatedController} />);
      rerender(<ChatInput controller={updatedController} />);

      // 发送按钮应该启用
      const sendButton = document.querySelector('.bg-blue-500');
      expect(sendButton).toBeInTheDocument();
    });

    test('13. 点击发送按钮应该调用 send()', () => {
      const controller = createControllerWithText('Hello');
      render(<ChatInput controller={controller} />);

      const sendButton = document.querySelector('.bg-blue-500') as HTMLElement;
      fireEvent.click(sendButton);

      expect(controller.send).toHaveBeenCalled();
    });

    test('14. Enter 键应该触发发送', () => {
      const controller = createControllerWithText('Hello');
      render(<ChatInput controller={controller} />);

      const textarea = screen.getByPlaceholderText(/输入消息/i);
      fireEvent.keyDown(textarea, { key: 'Enter', code: 'Enter' });

      expect(controller.send).toHaveBeenCalled();
    });

    test('15. Shift+Enter 不应该触发发送', () => {
      const controller = createControllerWithText('Hello');
      render(<ChatInput controller={controller} />);

      const textarea = screen.getByPlaceholderText(/输入消息/i);
      fireEvent.keyDown(textarea, { key: 'Enter', code: 'Enter', shiftKey: true });

      expect(controller.send).not.toHaveBeenCalled();
    });
  });

  describe('Streaming 状态', () => {
    test('16. streaming 状态应该显示停止按钮', () => {
      const controller = createStreamingController();
      render(<ChatInput controller={controller} />);

      const stopButton = document.querySelector('.bg-red-500');
      expect(stopButton).toBeInTheDocument();
    });

    test('17. 点击停止按钮应该调用 abort()', () => {
      const controller = createStreamingController();
      render(<ChatInput controller={controller} />);

      const stopButton = document.querySelector('.bg-red-500') as HTMLElement;
      fireEvent.click(stopButton);

      expect(controller.abort).toHaveBeenCalled();
    });
  });

  describe('禁用状态', () => {
    test('18. connecting 状态应该禁用 Textarea', () => {
      const controller = createMockController({ status: 'connecting' });
      render(<ChatInput controller={controller} />);

      const textarea = screen.getByPlaceholderText(/输入消息/i);
      expect(textarea).toBeDisabled();
    });
  });
});
