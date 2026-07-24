/**
 * ChatInput - 文件上传功能测试
 *
 * 测试范围:
 * - 文件选择触发
 * - 文件类型验证
 * - 文件大小验证
 * - base64 转换
 * - 数据格式正确性
 */

import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ChatInput } from '../index';
import {
  createMockController,
  mockImageFile,
  mockVideoFile,
  mockLargeImageFile,
  mockLargeVideoFile,
  mockInvalidFile,
  mockFileReader,
} from './mocks';

// Mock console.warn
const originalWarn = console.warn;
beforeAll(() => {
  console.warn = jest.fn();
});
afterAll(() => {
  console.warn = originalWarn;
});

describe('ChatInput - 文件上传功能', () => {
  let cleanup: () => void;

  beforeEach(() => {
    cleanup = mockFileReader('data:image/png;base64,mockBase64String');
  });

  afterEach(() => {
    cleanup();
    jest.clearAllMocks();
  });

  test('1. 点击"添加图片"按钮应该打开文件选择对话框', async () => {
    const controller = createMockController();
    render(<ChatInput controller={controller} />);

    // 点击 Plus 按钮打开菜单
    const plusButton = screen.getByRole('button', { name: /添加/i });
    await userEvent.click(plusButton);

    // 查找"添加图片"菜单项
    const imageMenuItem = screen.getByText('添加图片');
    expect(imageMenuItem).toBeInTheDocument();

    // 获取隐藏的 file input
    const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
    expect(fileInput).toBeInTheDocument();

    // Mock input.click
    const clickSpy = jest.spyOn(fileInput, 'click');

    // 点击菜单项
    await userEvent.click(imageMenuItem);

    // 验证 input.click 被调用
    expect(clickSpy).toHaveBeenCalled();

    // 验证 accept 属性设置为 image/*
    expect(fileInput.accept).toBe('image/*');
  });

  test('2. 点击"添加视频"按钮应该打开文件选择对话框', async () => {
    const controller = createMockController();
    render(<ChatInput controller={controller} />);

    // 点击 Plus 按钮
    const plusButton = screen.getByRole('button', { name: /添加/i });
    await userEvent.click(plusButton);

    // 查找"添加视频"菜单项
    const videoMenuItem = screen.getByText('添加视频');
    expect(videoMenuItem).toBeInTheDocument();

    // 获取 file input
    const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
    const clickSpy = jest.spyOn(fileInput, 'click');

    // 点击菜单项
    await userEvent.click(videoMenuItem);

    // 验证 accept 属性设置为 video/*
    expect(fileInput.accept).toBe('video/*');
    expect(clickSpy).toHaveBeenCalled();
  });

  test('3. 上传图片后应该转换为 base64 格式', async () => {
    const controller = createMockController();
    render(<ChatInput controller={controller} />);

    // 获取 file input
    const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;

    // 创建 mock 图片文件
    const file = mockImageFile();

    // 触发文件选择
    fireEvent.change(fileInput, { target: { files: [file] } });

    // 等待 FileReader 完成
    await waitFor(() => {
      expect(controller.setValue).toHaveBeenCalled();
    });

    // 验证 setValue 被调用，参数为 ContentPart[]
    const callArgs = (controller.setValue as jest.Mock).mock.calls[0][0];
    expect(Array.isArray(callArgs)).toBe(true);
    expect(callArgs).toHaveLength(1);
    expect(callArgs[0]).toEqual({
      type: 'image_url',
      image_url: { url: 'data:image/png;base64,mockBase64String' },
    });
  });

  test('4. 上传视频后应该转换为 base64 格式', async () => {
    cleanup(); // 清理图片 mock
    cleanup = mockFileReader('data:video/mp4;base64,mockVideoBase64');

    const controller = createMockController();
    render(<ChatInput controller={controller} />);

    const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
    const file = mockVideoFile();

    fireEvent.change(fileInput, { target: { files: [file] } });

    await waitFor(() => {
      expect(controller.setValue).toHaveBeenCalled();
    });

    const callArgs = (controller.setValue as jest.Mock).mock.calls[0][0];
    expect(callArgs[0]).toEqual({
      type: 'video_url',
      video_url: { url: 'data:video/mp4;base64,mockVideoBase64' },
    });
  });

  test('5. 文件类型验证 - 拒绝不支持的格式', async () => {
    const controller = createMockController();
    render(<ChatInput controller={controller} />);

    const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
    const file = mockInvalidFile(); // PDF 文件

    fireEvent.change(fileInput, { target: { files: [file] } });

    // 等待一小段时间
    await waitFor(() => {
      expect(console.warn).toHaveBeenCalledWith('不支持的文件类型:', 'application/pdf');
    });

    // setValue 不应该被调用
    expect(controller.setValue).not.toHaveBeenCalled();
  });

  test('6. 文件大小验证 - 图片超过 10MB', async () => {
    const controller = createMockController();
    render(<ChatInput controller={controller} />);

    const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
    const file = mockLargeImageFile(); // 15MB

    fireEvent.change(fileInput, { target: { files: [file] } });

    // 等待一段时间让文件验证完成
    await new Promise(resolve => setTimeout(resolve, 100));

    // 验证 console.warn 被调用,包含"文件过大"信息
    expect(console.warn).toHaveBeenCalledWith(
      expect.stringContaining('文件过大: 15.00MB, 限制: 10MB')
    );

    // setValue 不应该被调用
    expect(controller.setValue).not.toHaveBeenCalled();
  });

  test('7. 文件大小验证 - 视频超过 50MB', async () => {
    const controller = createMockController();
    render(<ChatInput controller={controller} />);

    const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
    const file = mockLargeVideoFile(); // 60MB

    fireEvent.change(fileInput, { target: { files: [file] } });

    // 等待一段时间让文件验证完成
    await new Promise(resolve => setTimeout(resolve, 100));

    // 验证 console.warn 被调用,包含"文件过大"信息
    expect(console.warn).toHaveBeenCalledWith(
      expect.stringContaining('文件过大: 60.00MB, 限制: 50MB')
    );

    // setValue 不应该被调用
    expect(controller.setValue).not.toHaveBeenCalled();
  });

  test('8. 上传后 input.value 应该被重置', async () => {
    const controller = createMockController();
    render(<ChatInput controller={controller} />);

    const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
    const file = mockImageFile();

    // 设置 input value (模拟文件选择)
    Object.defineProperty(fileInput, 'value', {
      writable: true,
      value: 'C:\\fakepath\\test.png',
    });

    fireEvent.change(fileInput, { target: { files: [file] } });

    await waitFor(() => {
      expect(controller.setValue).toHaveBeenCalled();
    });

    // 验证 input.value 被重置
    expect(fileInput.value).toBe('');
  });

  test('9. 取消文件选择不应该触发 setValue', () => {
    const controller = createMockController();
    render(<ChatInput controller={controller} />);

    const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;

    // 触发 change 但没有文件
    fireEvent.change(fileInput, { target: { files: [] } });

    // setValue 不应该被调用
    expect(controller.setValue).not.toHaveBeenCalled();
  });
});
