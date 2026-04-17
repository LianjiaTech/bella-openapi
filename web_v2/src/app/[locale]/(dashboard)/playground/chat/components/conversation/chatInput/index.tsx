'use client';

import { useCallback, KeyboardEvent, useEffect, memo, useRef, useMemo } from 'react';
import { Textarea } from '@/components/ui/textarea';
import type { ChatInputController, ContentPart } from '../types';
import { Send, CircleStop, Paperclip, Bookmark, Plus } from 'lucide-react';
import { Button } from '@/components/common/button';
import { DropdownMenu, DropdownMenuTrigger, DropdownMenuContent, DropdownMenuItem } from '@/components/common/dropdown-menu';
import { MediaPreview, type MediaFile } from './media-preview';

export interface ChatInputProps {
  controller: ChatInputController;
  onCompositionStart?: () => void;
  onCompositionEnd?: () => void;
  /** 模型能力特性（控制上传功能） */
  modelFeatures?: {
    vision?: boolean;
    video?: boolean;
  };
}

/**
 * 聊天输入框组件
 *
 * 职责：
 * - 提供消息输入界面
 * - 处理发送和中止消息操作
 * - 支持流式输出状态下的交互
 * - 支持图片和视频上传(转换为 base64 格式)
 * - 根据模型能力动态控制上传功能可用性
 *
 * 性能优化：
 * - 使用 React.memo 避免不必要的重渲染
 * - 自定义比较函数只比较 controller 的关键状态属性
 * - 函数引用变化不会触发重渲染
 */
const ChatInputComponent = ({
  controller,
  modelFeatures = { vision: false, video: false }
}: ChatInputProps) => {
  const { value, setValue, canSend, send, abort, status } = controller;

  // 文件上传 ref
  const fileInputRef = useRef<HTMLInputElement>(null);

  // 判断当前是否正在流式输出
  const isStreaming = status === 'streaming';

  /**
   * 检查是否有有效内容
   *
   * 逻辑:
   * - string 类型: 检查是否为空字符串
   * - ContentPart[] 类型: 检查数组是否有内容
   */
  const hasContent = typeof value === 'string'
    ? value.trim().length > 0
    : value.length > 0;

  // 判断按钮是否禁用(输入框为空且非流式状态)
  const isDisabled = !isStreaming && !hasContent;

  useEffect(() => {
    console.log('status', status);
  }, [status]);

  /**
   * 从 ContentPart[] 中提取媒体文件
   *
   * 职责:
   * - 将 ContentPart[] 中的图片和视频转换为 MediaFile[] 格式
   * - 为每个媒体文件生成唯一 id
   * - 从 base64 URL 中提取文件名和类型
   *
   * 性能优化:
   * - 使用 useMemo 缓存转换结果,只在 value 变化时重新计算
   */
  const mediaFiles = useMemo<MediaFile[]>(() => {
    if (typeof value === 'string') {
      return [];
    }

    return value
      .map((part, index): MediaFile | null => {
        if (part.type === 'image_url') {
          return {
            id: `image-${index}`,
            type: 'image',
            url: part.image_url.url,
            name: `图片 ${index + 1}`,
          };
        }
        if (part.type === 'video_url') {
          return {
            id: `video-${index}`,
            type: 'video',
            url: part.video_url.url,
            name: `视频 ${index + 1}`,
          };
        }
        return null;
      })
      .filter((file): file is MediaFile => file !== null);
  }, [value]);

  /**
   * 提取 ContentPart[] 中的文本内容用于 Textarea 显示
   *
   * 职责:
   * - 从 ContentPart[] 中找到 type: 'text' 的块
   * - 返回其 text 字段,如果不存在则返回空字符串
   *
   * 性能优化:
   * - 使用 useMemo 缓存,只在 value 变化时重新计算
   */
  const textValue = useMemo<string>(() => {
    if (typeof value === 'string') {
      return value;
    }

    // 从数组中提取文本块
    const textPart = value.find(part => part.type === 'text');
    return textPart?.text || '';
  }, [value]);

  /**
   * 检测当前已上传的媒体类型
   *
   * 职责:
   * - 检查 value 中是否已存在图片或视频
   * - 返回 'none' | 'image' | 'video'
   * - 用于控制上传菜单项的禁用状态(互斥上传)
   *
   * 设计逻辑:
   * - 如果存在图片,返回 'image' (禁止再上传视频)
   * - 如果存在视频,返回 'video' (禁止再上传图片)
   * - 如果都不存在,返回 'none' (允许上传任意类型)
   *
   * 性能优化:
   * - 使用 useMemo 缓存计算结果,只在 value 变化时重新计算
   */
  const uploadedMediaType = useMemo<'none' | 'image' | 'video'>(() => {
    if (typeof value === 'string') {
      return 'none';
    }

    // 查找是否有图片或视频
    const hasImage = value.some(part => part.type === 'image_url');
    const hasVideo = value.some(part => part.type === 'video_url');

    if (hasImage) return 'image';
    if (hasVideo) return 'video';
    return 'none';
  }, [value]);

  /**
   * 删除指定的媒体文件
   *
   * 职责:
   * - 从 ContentPart[] 中移除指定的图片或视频
   * - 保持数组格式稳定性,避免频繁的类型切换
   * - 只有完全为空时才降级为空字符串
   *
   * 设计逻辑:
   * - 通过 id 中的索引定位要删除的媒体元素
   * - 保留其他类型的 ContentPart (如 text)
   * - 删除后仍有内容(文本或其他媒体) → 保持 ContentPart[] 格式
   * - 删除后完全为空 → 降级为 '' (空字符串)
   *
   * 一致性优化:
   * - 避免"删除最后一个媒体且有文本"时的 ContentPart[] → string 切换
   * - 与 handleTextChange 保持一致:只在真正清空时才降级
   */
  const handleRemoveMedia = useCallback((id: string) => {
    if (typeof value === 'string') return;

    // 从 id 中提取索引 (格式: "image-0", "video-1")
    const index = parseInt(id.split('-')[1]);

    // 过滤掉指定索引的媒体文件
    let mediaIndex = -1;
    const newValue = value.filter((part) => {
      if (part.type === 'image_url' || part.type === 'video_url') {
        mediaIndex++;
        return mediaIndex !== index;
      }
      return true;
    });

    // 一致性优化: 保持数组格式稳定性
    if (newValue.length === 0) {
      setValue('');
    } else {
      setValue(newValue);
    }
  }, [value, setValue]);

  /**
   * 处理文本输入变化
   *
   * 职责：
   * - 更新文本内容的同时保留已上传的媒体文件
   * - 自动在字符串模式和数组模式之间切换
   *
   * 设计逻辑：
   * - 字符串模式(无媒体) → 直接更新为新文本
   * - 数组模式(有媒体) → 保留媒体部分,仅更新文本块
   * - 删除所有文本且无媒体 → 降级为字符串模式
   *
   * 性能优化：
   * - 使用 useCallback 避免每次渲染创建新函数
   * - 减少不必要的 Textarea props 变化
   */
  const handleTextChange = useCallback((e: React.ChangeEvent<HTMLTextAreaElement>) => {
    const newText = e.target.value;

    // 如果当前是字符串模式,直接更新
    if (typeof value === 'string') {
      setValue(newText);
      return;
    }

    // 如果当前是数组模式,需要保留媒体文件
    const mediaParts = value.filter(
      part => part.type === 'image_url' || part.type === 'video_url'
    );

    // 构建新的 value
    if (mediaParts.length === 0) {
      // 没有媒体文件,降级为字符串
      setValue(newText);
    } else {
      // 有媒体文件,构建新数组
      const newValue: ContentPart[] = newText.trim()
        ? [{ type: 'text', text: newText }, ...mediaParts]
        : mediaParts;
      setValue(newValue);
    }
  }, [value, setValue]);

  /**
   * 处理文件上传
   *
   * 职责：
   * - 读取用户选择的图片或视频文件
   * - 转换为 base64 格式
   * - 合并到现有 value 中(保留文本内容)
   *
   * 数据转换逻辑：
   * - string → ContentPart[] (当添加媒体文件时)
   * - 保留原有文本内容作为 type: 'text' 块
   * - 追加新的 image_url 或 video_url 块
   */
  const handleFileUpload = useCallback((acceptType: 'image' | 'video') => {
    const input = fileInputRef.current;
    if (!input) return;

    // 设置文件类型过滤
    input.accept = acceptType === 'image' ? 'image/*' : 'video/*';

    // 触发文件选择
    input.click();
  }, []);

  /**
   * 处理文件选择后的读取和转换
   *
   * 职责：
   * - 支持多文件同时上传
   * - 验证每个文件的类型和大小
   * - 批量转换为 base64 格式
   * - 合并到现有 value 中
   *
   * 性能优化：
   * - 使用 Promise.all 并发读取所有文件
   * - 一次性更新 setValue，避免多次渲染
   */
  const handleFileChange = useCallback(async (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(e.target.files || []);
    if (files.length === 0) return;

    // 验证和读取所有文件
    const filePromises = files.map((file) => {
      return new Promise<ContentPart | null>((resolve) => {
        // 文件类型验证
        const isImage = file.type.startsWith('image/');
        const isVideo = file.type.startsWith('video/');

        if (!isImage && !isVideo) {
          console.warn('不支持的文件类型:', file.type);
          resolve(null);
          return;
        }

        // 文件大小限制 (图片10MB, 视频50MB)
        const maxSize = isImage ? 10 * 1024 * 1024 : 50 * 1024 * 1024;
        if (file.size > maxSize) {
          console.warn(`文件过大: ${file.name}, ${(file.size / 1024 / 1024).toFixed(2)}MB, 限制: ${maxSize / 1024 / 1024}MB`);
          resolve(null);
          return;
        }

        // 读取文件并转换为 base64
        const reader = new FileReader();
        reader.onloadend = () => {
          const base64Url = reader.result as string;

          // 构建新的 ContentPart
          const newPart: ContentPart = isImage
            ? { type: 'image_url', image_url: { url: base64Url } }
            : { type: 'video_url', video_url: { url: base64Url } };

          resolve(newPart);
        };

        reader.onerror = () => {
          console.warn('文件读取失败:', file.name);
          resolve(null);
        };

        reader.readAsDataURL(file);
      });
    });

    // 等待所有文件读取完成
    const results = await Promise.all(filePromises);

    // 过滤掉失败的文件
    const validParts = results.filter((part): part is ContentPart => part !== null);

    if (validParts.length === 0) {
      console.warn('没有有效的文件可上传');
      e.target.value = '';
      return;
    }

    // 合并到现有 value
    let newValue: ContentPart[];

    if (typeof value === 'string') {
      // 当前是字符串: 转换为 ContentPart[] 格式
      newValue = value.trim()
        ? [{ type: 'text', text: value }, ...validParts]
        : validParts;
    } else {
      // 当前已是数组: 直接追加
      newValue = [...value, ...validParts];
    }

    setValue(newValue);

    // 重置 input，允许重复选择同一文件
    e.target.value = '';
  }, [value, setValue]);

  /**
   * 处理按钮点击
   *
   * 行为逻辑：
   * - streaming状态 + 有输入内容 → 先abort，再发送新消息
   * - streaming状态 + 无输入内容 → 仅abort
   * - 非streaming状态 + 有内容 → 发送消息
   */
  const handleButtonClick = useCallback(async () => {
    if (isStreaming) {
      // 先停止当前流式输出
      abort();

      // 如果输入框有内容,发送新消息
      const hasValidContent = typeof value === 'string'
        ? value.trim().length > 0
        : value.length > 0;

      if (hasValidContent) {
        setValue('');
        await send();
      }
    } else if (canSend) {
      // 正常发送
      setValue('');
      await send();
    }
  }, [isStreaming, canSend, value, abort, send, setValue]);

  /**
   * 处理键盘事件
   *
   * Enter 行为：
   * - streaming状态 + 有输入 → 先abort，再发送新消息
   * - streaming状态 + 无输入 → 不处理（避免误触）
   * - 非streaming状态 + 有输入 → 发送消息
   * - Shift+Enter → 换行
   */
  const handleKeyDown = useCallback(
    (e: KeyboardEvent<HTMLTextAreaElement>) => {
      if (e.key !== 'Enter' || e.shiftKey) return

      if (e.nativeEvent.isComposing) return

      // 检查是否有有效内容
      const hasValidContent = typeof value === 'string'
        ? value.trim().length > 0
        : value.length > 0;

      if (!hasValidContent) return

      e.preventDefault();

      handleButtonClick();

    },
    [value, handleButtonClick]
  );

  /**
   * 根据模型能力和已上传媒体类型构建菜单项
   *
   * 设计逻辑：
   * - 如果模型支持 vision，显示"添加图片"菜单项
   * - 如果模型支持 video，显示"添加视频"菜单项
   * - 互斥上传：已上传图片时禁用视频，已上传视频时禁用图片
   * - 删除所有媒体后，两种类型都可以重新上传
   * - 使用 useMemo 缓存菜单项，避免每次渲染都重新创建
   *
   * 用户体验优化：
   * - 禁用项显示半透明+禁止光标
   * - 提供清晰的禁用原因提示
   * - 点击禁用项时阻止默认行为
   */
  const menuItems = useMemo(() => {
    const items = [];

    if (modelFeatures.vision) {
      const isDisabled = uploadedMediaType === 'video';
      items.push({
        icon: Paperclip,
        label: "添加图片",
        onClick: () => handleFileUpload('image'),
        disabled: isDisabled,
        disabledReason: isDisabled ? '当前已上传视频，请先删除视频后再添加图片' : undefined
      });
    }

    if (modelFeatures.video) {
      const isDisabled = uploadedMediaType === 'image';
      items.push({
        icon: Bookmark,
        label: "添加视频",
        onClick: () => handleFileUpload('video'),
        disabled: isDisabled,
        disabledReason: isDisabled ? '当前已上传图片，请先删除图片后再添加视频' : undefined
      });
    }

    return items;
  }, [modelFeatures.vision, modelFeatures.video, handleFileUpload, uploadedMediaType]);

  /**
   * 判断上传按钮是否应该禁用
   *
   * 设计逻辑：
   * - 如果模型既不支持 vision 也不支持 video，则禁用上传按钮
   */
  const isUploadDisabled = !modelFeatures.vision && !modelFeatures.video;
  return (
    <div className="relative w-full">
      {/* 隐藏的文件上传 input */}
      <input
        ref={fileInputRef}
        type="file"
        multiple
        className="hidden"
        onChange={handleFileChange}
      />

      {/* 输入框容器 */}
      <div className="relative">
        {/* 媒体预览区域 - 显示在输入框上方 */}
        {mediaFiles.length > 0 && (
          <MediaPreview
            files={mediaFiles}
            onRemove={handleRemoveMedia}
            className="mb-2"
          />
        )}

        <Textarea
          value={textValue}
          onChange={handleTextChange}
          onKeyDown={handleKeyDown}
          placeholder="输入消息... (Enter发送, Shift+Enter换行)"
          className="resize-none overflow-y-auto"
          disabled={status === 'connecting'}
        />

        {/* 添加按钮 ,定位左下角*/}
        <div className="absolute bottom-2 left-2">
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button
                variant="ghost"
                size="icon"
                disabled={isUploadDisabled}
                className="h-9 w-9 shrink-0 rounded-full hover:bg-muted disabled:opacity-50 disabled:cursor-not-allowed"
                title={isUploadDisabled ? "当前模型不支持上传图片或视频" : "添加图片或视频"}
              >
                <Plus className="h-5 w-5 text-muted-foreground" />
                <span className="sr-only">添加</span>
              </Button>
            </DropdownMenuTrigger>
            {!isUploadDisabled && (
              <DropdownMenuContent
                align="start"
                side="top"
                className="w-56 rounded-xl p-1"
              >
                {menuItems.map((item, index) => {
                  const Icon = item.icon

                  return (
                    <DropdownMenuItem
                      key={index}
                      className={`flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm ${
                        item.disabled
                          ? 'opacity-50 cursor-not-allowed'
                          : 'cursor-pointer'
                      }`}
                      onClick={item.disabled ? undefined : item.onClick}
                      disabled={item.disabled}
                      title={item.disabled ? item.disabledReason : undefined}
                    >
                      <Icon className="h-4 w-4 text-muted-foreground" />
                      <span>{item.label}</span>
                    </DropdownMenuItem>
                  )
                })}
              </DropdownMenuContent>
            )}
          </DropdownMenu>
        </div>

        {/* 发送/停止按钮 - 绝对定位在右下角 */}
        <div
          onClick={isStreaming ? abort : (isDisabled ? undefined : handleButtonClick)}
          className={`
            absolute right-2 bottom-2 w-8 h-8
            flex items-center justify-center
            rounded-md font-medium
            transition-colors duration-200
            text-white
            ${isStreaming
              ? 'bg-red-500 hover:bg-red-600 cursor-pointer'
              : isDisabled
                ? 'bg-gray-400 cursor-not-allowed'
                : 'bg-blue-500 hover:bg-blue-600 cursor-pointer'
            }
          `}
        >
          {isStreaming ? <CircleStop className="w-5 h-5" /> : <Send className="w-5 h-5" />}
        </div>
      </div>
    </div>
  );
};

export const ChatInput = memo(ChatInputComponent);
