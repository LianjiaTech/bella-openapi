/**
 * ChatInput - 数据转换逻辑测试
 *
 * 测试范围:
 * - string → ContentPart[] 转换
 * - 保留原有文本内容
 * - 追加多个媒体文件
 * - 混合场景处理
 */

import React from 'react';
import { render, fireEvent, waitFor } from '@testing-library/react';
import { ChatInput } from '../index';
import {
  createMockController,
  createControllerWithText,
  createControllerWithContent,
  mockImageFile,
  mockVideoFile,
  mockFileReader,
} from './mocks';

describe('ChatInput - 数据转换逻辑', () => {
  let cleanup: () => void;

  beforeEach(() => {
    cleanup = mockFileReader('data:image/png;base64,mockBase64String');
  });

  afterEach(() => {
    cleanup();
    jest.clearAllMocks();
  });

  test('1. 空字符串 + 图片 → [image_url]', async () => {
    const controller = createMockController({ value: '' });
    render(<ChatInput controller={controller} />);

    const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
    const file = mockImageFile();

    fireEvent.change(fileInput, { target: { files: [file] } });

    await waitFor(() => {
      expect(controller.setValue).toHaveBeenCalled();
    });

    const result = (controller.setValue as jest.Mock).mock.calls[0][0];
    expect(result).toEqual([
      { type: 'image_url', image_url: { url: 'data:image/png;base64,mockBase64String' } },
    ]);
  });

  test('2. 文本 + 图片 → [text, image_url]', async () => {
    const controller = createControllerWithText('Hello World');
    render(<ChatInput controller={controller} />);

    const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
    const file = mockImageFile();

    fireEvent.change(fileInput, { target: { files: [file] } });

    await waitFor(() => {
      expect(controller.setValue).toHaveBeenCalled();
    });

    const result = (controller.setValue as jest.Mock).mock.calls[0][0];
    expect(result).toEqual([
      { type: 'text', text: 'Hello World' },
      { type: 'image_url', image_url: { url: 'data:image/png;base64,mockBase64String' } },
    ]);
  });

  test('3. 纯空格文本 + 图片 → [image_url]', async () => {
    const controller = createControllerWithText('   ');
    render(<ChatInput controller={controller} />);

    const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
    const file = mockImageFile();

    fireEvent.change(fileInput, { target: { files: [file] } });

    await waitFor(() => {
      expect(controller.setValue).toHaveBeenCalled();
    });

    const result = (controller.setValue as jest.Mock).mock.calls[0][0];
    // 空格会被 trim，所以不保留文本块
    expect(result).toEqual([
      { type: 'image_url', image_url: { url: 'data:image/png;base64,mockBase64String' } },
    ]);
  });

  test('4. 已有数组 + 图片 → 追加到数组', async () => {
    const existingContent = [
      { type: 'text', text: 'Hi' },
      { type: 'image_url', image_url: { url: 'data:image/jpeg;base64,existing' } },
    ];
    const controller = createControllerWithContent(existingContent);
    render(<ChatInput controller={controller} />);

    const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
    const file = mockImageFile();

    fireEvent.change(fileInput, { target: { files: [file] } });

    await waitFor(() => {
      expect(controller.setValue).toHaveBeenCalled();
    });

    const result = (controller.setValue as jest.Mock).mock.calls[0][0];
    expect(result).toHaveLength(3);
    expect(result[0]).toEqual({ type: 'text', text: 'Hi' });
    expect(result[1]).toEqual({ type: 'image_url', image_url: { url: 'data:image/jpeg;base64,existing' } });
    expect(result[2]).toEqual({ type: 'image_url', image_url: { url: 'data:image/png;base64,mockBase64String' } });
  });

  test('5. 连续上传多个文件', async () => {
    const controller = createMockController();
    const { rerender } = render(<ChatInput controller={controller} />);

    const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;

    // 上传第一个图片
    const file1 = mockImageFile();
    fireEvent.change(fileInput, { target: { files: [file1] } });

    await waitFor(() => {
      expect(controller.setValue).toHaveBeenCalledTimes(1);
    });

    const result1 = (controller.setValue as jest.Mock).mock.calls[0][0];
    expect(result1).toHaveLength(1);

    // 模拟 value 更新后重新渲染
    const updatedController = createControllerWithContent(result1);
    rerender(<ChatInput controller={updatedController} />);

    // 上传第二个图片
    cleanup();
    cleanup = mockFileReader('data:image/png;base64,secondImage');

    const file2 = mockImageFile();
    fireEvent.change(fileInput, { target: { files: [file2] } });

    await waitFor(() => {
      expect(updatedController.setValue).toHaveBeenCalled();
    });

    const result2 = (updatedController.setValue as jest.Mock).mock.calls[0][0];
    expect(result2).toHaveLength(2);
    expect(result2[1].image_url.url).toBe('data:image/png;base64,secondImage');
  });

  test('6. 文本 + 图片 + 视频混合场景', async () => {
    const controller = createControllerWithText('Check this out!');
    const { rerender } = render(<ChatInput controller={controller} />);

    const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;

    // 上传图片
    const imageFile = mockImageFile();
    fireEvent.change(fileInput, { target: { files: [imageFile] } });

    await waitFor(() => {
      expect(controller.setValue).toHaveBeenCalled();
    });

    const resultAfterImage = (controller.setValue as jest.Mock).mock.calls[0][0];
    expect(resultAfterImage).toHaveLength(2);
    expect(resultAfterImage[0].type).toBe('text');
    expect(resultAfterImage[1].type).toBe('image_url');

    // 模拟 value 更新
    const updatedController = createControllerWithContent(resultAfterImage);
    rerender(<ChatInput controller={updatedController} />);

    // 上传视频
    cleanup();
    cleanup = mockFileReader('data:video/mp4;base64,mockVideoBase64');

    const videoFile = mockVideoFile();
    fireEvent.change(fileInput, { target: { files: [videoFile] } });

    await waitFor(() => {
      expect(updatedController.setValue).toHaveBeenCalled();
    });

    const finalResult = (updatedController.setValue as jest.Mock).mock.calls[0][0];
    expect(finalResult).toHaveLength(3);
    expect(finalResult[0].type).toBe('text');
    expect(finalResult[1].type).toBe('image_url');
    expect(finalResult[2].type).toBe('video_url');
  });

  test('7. 空数组 + 图片', async () => {
    const controller = createControllerWithContent([]);
    render(<ChatInput controller={controller} />);

    const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
    const file = mockImageFile();

    fireEvent.change(fileInput, { target: { files: [file] } });

    await waitFor(() => {
      expect(controller.setValue).toHaveBeenCalled();
    });

    const result = (controller.setValue as jest.Mock).mock.calls[0][0];
    expect(result).toHaveLength(1);
    expect(result[0].type).toBe('image_url');
  });
});
