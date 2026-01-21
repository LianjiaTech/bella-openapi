'use client';

import React, {useEffect, useRef, useState} from 'react';
import {Button} from '@/components/ui/button';
import {Card, CardContent} from '@/components/ui/card';
import {Alert, AlertDescription} from '@/components/ui/alert';
import {
  ChatCompletionsEventType,
  ChatCompletionsProcessor,
  ChatMessage
} from '@/components/playground/ChatCompletionsProcessor';
import {api_host} from '@/config';
import {SendIcon, ImageIcon, X, VideoIcon} from 'lucide-react';
import {Textarea} from '@/components/ui/textarea';
import {ChatMessage as MessageComponent} from '@/components/ui/ChatMessage';
import {v4 as uuidv4} from 'uuid';
import {useUser} from "@/lib/context/user-context";
import {getEndpointDetails} from '@/lib/api/meta';
import {Model} from '@/lib/types/openapi';
import {CostCalculatorModal} from '@/components/playground/CostCalculatorModal';
import {Calculator} from 'lucide-react';

// 默认system prompt
const DEFAULT_SYSTEM_PROMPT = '你是一个智能助手，可以回答各种问题并提供帮助。请尽量提供准确、有帮助的信息。';

// 文件上传配置常量
const MAX_IMAGE_SIZE = 10 * 1024 * 1024; // 10MB
const MAX_VIDEO_SIZE = 10 * 1024 * 1024; // 10MB
const SUPPORTED_VIDEO_FORMATS = ['video/mp4', 'video/avi', 'video/mov', 'video/quicktime'];
const SUPPORTED_VIDEO_MIME_TYPES = {
  'video/mp4': 'MP4',
  'video/avi': 'AVI',
  'video/mov': 'MOV',
  'video/quicktime': 'MOV'
};
const DEFAULT_VIDEO_FPS = '1';
const VIDEO_FPS_OPTIONS = [
  { value: '0.2', label: '0.2 (低)' },
  { value: '0.5', label: '0.5' },
  { value: '1', label: '1 (默认)' },
  { value: '2', label: '2' },
  { value: '3', label: '3' },
  { value: '5', label: '5 (高)' }
];

// 工具函数：格式化文件大小
const formatFileSize = (bytes: number): string => {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(2))} ${sizes[i]}`;
};

// 增强版消息类型定义，包含ID
// 参照MessageHandler.ts的实现
interface EnhancedChatMessage extends ChatMessage {
  id: string;
  timestamp?: number;
  // 深度思考内容
  reasoning_content?: string;
  // 是否是思考内容
  isReasoningContent?: boolean;
  // 会话轮次，用于标记当前回复属于哪个轮次
  sessionTurn?: number;
  // 原始内联数据
  rawInlineData?: string;
  // 处理后的内容（包含HTML标签）
  processedContent?: string;
  // 用于API请求的清理后内容
  apiContent?: string;
  // 错误信息
  error?: string;
  // 多模态内容
  multimodalContent?: any[];
  // 是否包含图像
  hasImage?: boolean;
  // Gemini模型的思维签名
  reasoning_content_signature?: string;
}

// 多模态内容项类型
interface MultimodalContentItem {
  type: string;
  text?: string;
  image_url?: {
    url: string;
  };
  video_url?: {
    url: string;
  };
  fps?: string;
}

export default function ChatCompletions() {
  // 状态管理
  const [input, setInput] = useState('');
  const [systemPrompt, setSystemPrompt] = useState(DEFAULT_SYSTEM_PROMPT);
  const [isEditingPrompt, setIsEditingPrompt] = useState(false);
  const [isPromptExpanded, setIsPromptExpanded] = useState(false);
  const [messages, setMessages] = useState<EnhancedChatMessage[]>([{
    id: uuidv4(),
    role: 'system',
    content: DEFAULT_SYSTEM_PROMPT,
    timestamp: Date.now()
  }]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const [model, setModel] = useState('');
  const [streamingResponse, setStreamingResponse] = useState('');
  const {userInfo} = useUser();

  // 图像上传相关状态
  const [uploadedImages, setUploadedImages] = useState<{id: string, file: File, base64: string}[]>([]);
  const [hasVisionCapability, setHasVisionCapability] = useState(false);
  const [currentModelInfo, setCurrentModelInfo] = useState<Model | null>(null);

  // 视频上传相关状态
  const [uploadedVideos, setUploadedVideos] = useState<{id: string, file: File, base64: string, objectUrl?: string}[]>([]);
  const [hasVideoCapability, setHasVideoCapability] = useState(false);
  const [videoFps, setVideoFps] = useState<string>(DEFAULT_VIDEO_FPS);

  // 新增状态：用于处理内联数据
  const [isReceivingInline, setIsReceivingInline] = useState(false);
  const [inlineBuffer, setInlineBuffer] = useState('');

  // 费用计算器 Modal 相关状态
  const [isCostModalOpen, setIsCostModalOpen] = useState(false);

  // Token 统计状态 (从后端返回的usage中获取)
  const [sessionTokens, setSessionTokens] = useState({
    inputTokens: 0,
    outputTokens: 0,
    cacheCreationTokens: 0,
    cacheReadTokens: 0
  });

  // Refs
  const chatCompletionsWriterRef = useRef<ChatCompletionsProcessor | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const videoInputRef = useRef<HTMLInputElement>(null);
  const usageProcessedRef = useRef<boolean>(false); // 防止重复处理 usage
  const sessionTokensRef = useRef({
    inputTokens: 0,
    outputTokens: 0,
    cacheCreationTokens: 0,
    cacheReadTokens: 0
  });

  // 图像处理函数
  const handleImageUpload = (event: React.ChangeEvent<HTMLInputElement>) => {
    const files = event.target.files;
    if (!files) return;

    Array.from(files).forEach(file => {
      // 检查文件类型
      if (!file.type.startsWith('image/')) {
        setError('请选择图片文件');
        return;
      }

      // 检查文件大小
      if (file.size > MAX_IMAGE_SIZE) {
        setError(`图片大小不能超过${MAX_IMAGE_SIZE / 1024 / 1024}MB`);
        return;
      }

      const reader = new FileReader();
      reader.onload = (e) => {
        const base64 = e.target?.result as string;
        const newImage = {
          id: uuidv4(),
          file,
          base64
        };
        setUploadedImages(prev => [...prev, newImage]);
      };
      reader.readAsDataURL(file);
    });

    // 清空input以允许重复选择同一文件
    if (event.target) {
      event.target.value = '';
    }
  };

  const removeImage = (imageId: string) => {
    setUploadedImages(prev => prev.filter(img => img.id !== imageId));
  };

  const openImageSelector = () => {
    fileInputRef.current?.click();
  };

  // 视频处理函数
  const handleVideoUpload = (event: React.ChangeEvent<HTMLInputElement>) => {
    const files = event.target.files;
    if (!files) return;

    Array.from(files).forEach(file => {
      // 检查文件类型
      if (!file.type.startsWith('video/')) {
        setError('请选择视频文件');
        return;
      }

      // 检查是否是支持的视频格式
      if (!SUPPORTED_VIDEO_FORMATS.includes(file.type)) {
        const supportedFormats = Object.values(SUPPORTED_VIDEO_MIME_TYPES).join('、');
        setError(`不支持的视频格式。支持的格式：${supportedFormats}`);
        return;
      }

      // 检查文件大小
      if (file.size > MAX_VIDEO_SIZE) {
        setError(`视频大小不能超过${MAX_VIDEO_SIZE / 1024 / 1024}MB（建议使用较短的视频片段）`);
        return;
      }

      const reader = new FileReader();

      reader.onloadstart = () => {
        setError(''); // 清除之前的错误
      };

      reader.onload = (e) => {
        const base64 = e.target?.result as string;

        // 验证base64格式
        if (!base64 || !base64.startsWith('data:video/')) {
          setError('视频编码失败，请重试');
          return;
        }

        // 对于MOV格式，需要特殊处理
        let processedBase64 = base64;
        if (file.type === 'video/quicktime' && base64.startsWith('data:video/quicktime;base64,')) {
          // 根据文档要求，base64编码时使用 video/mov
          processedBase64 = base64.replace('data:video/quicktime;base64,', 'data:video/mov;base64,');
        }

        // 创建Object URL用于预览（更高效，浏览器兼容性更好）
        const objectUrl = URL.createObjectURL(file);

        const newVideo = {
          id: uuidv4(),
          file,
          base64: processedBase64,
          objectUrl: objectUrl
        };
        setUploadedVideos(prev => [...prev, newVideo]);
      };

      reader.onerror = () => {
        setError('视频读取失败，请重试');
      };

      reader.readAsDataURL(file);
    });

    // 清空input以允许重复选择同一文件
    if (event.target) {
      event.target.value = '';
    }
  };

  const removeVideo = (videoId: string) => {
    setUploadedVideos(prev => {
      const videoToRemove = prev.find(vid => vid.id === videoId);
      // 清理Object URL以释放内存
      if (videoToRemove?.objectUrl) {
        URL.revokeObjectURL(videoToRemove.objectUrl);
      }
      return prev.filter(vid => vid.id !== videoId);
    });
  };

  const openVideoSelector = () => {
    videoInputRef.current?.click();
  };

  // 处理内联内容的函数
  function processInlineContent(content: string) {
    // 匹配 <inline><data>...</data><mimeType>...</mimeType></inline> 格式
    const regex = /<inline><data>(.*?)<\/data><mimeType>(.*?)<\/mimeType><\/inline>/g;
    let result = content;
    let match;

    while ((match = regex.exec(content)) !== null) {
      const fullMatch = match[0];
      const base64Data = match[1];
      const mimeType = match[2];

      if (mimeType.startsWith('image/')) {
        // 创建可显示的图像元素
        const imageElement = `<img src="data:${mimeType};base64,${base64Data}" alt="AI生成的图像" style="max-width: 100%;" />`;
        result = result.replace(fullMatch, imageElement);
      } else {
        // 处理其他类型的内联数据
        result = result.replace(fullMatch, `[包含${mimeType}类型的数据]`);
      }
    }

    return result;
  }

  // 提取内联图像数据
  function extractInlineImages(content: string): { text: string, images: Array<{mimeType: string, base64Data: string}> } {
    if (!content) return { text: '', images: [] };

    const regex = /<inline><data>(.*?)<\/data><mimeType>(.*?)<\/mimeType><\/inline>/g;
    const images: Array<{mimeType: string, base64Data: string}> = [];
    let textContent = content;
    let match;

    // 收集所有图像数据
    while ((match = regex.exec(content)) !== null) {
      const fullMatch = match[0];
      const base64Data = match[1];
      const mimeType = match[2];

      if (mimeType.startsWith('image/')) {
        images.push({ mimeType, base64Data });
        // 在文本中用占位符替换图像
        textContent = textContent.replace(fullMatch, '');
      }
    }

    return { text: textContent, images };
  }

  // 从HTML内容中提取图像
  function extractImagesFromHTML(content: string): { text: string, images: Array<{mimeType: string, base64Data: string}> } {
    if (!content) return { text: '', images: [] };

    const regex = /<img\s+src="data:(.*?);base64,(.*?)"\s+alt=".*?"\s+style=".*?".*?\/>/g;
    const images: Array<{mimeType: string, base64Data: string}> = [];
    let textContent = content;
    let match;

    // 收集所有图像数据
    while ((match = regex.exec(content)) !== null) {
      const fullMatch = match[0];
      const mimeType = match[1];
      const base64Data = match[2];

      if (mimeType.startsWith('image/')) {
        images.push({ mimeType, base64Data });
        // 在文本中用占位符替换图像
        textContent = textContent.replace(fullMatch, '');
      }
    }

    return { text: textContent, images };
  }

  // 创建多模态内容
  function createMultimodalContent(content: string): { multimodalContent: MultimodalContentItem[], hasImage: boolean } {
    // 首先尝试从原始内联格式提取图像
    let extracted = extractInlineImages(content);

    // 如果没有找到图像，尝试从HTML格式提取
    if (extracted.images.length === 0) {
      extracted = extractImagesFromHTML(content);
    }

    const multimodalContent: MultimodalContentItem[] = [];

    // 添加文本内容（如果有）
    if (extracted.text.trim()) {
      multimodalContent.push({
        type: "text",
        text: extracted.text.trim()
      });
    }

    // 添加图像内容
    for (const image of extracted.images) {
      multimodalContent.push({
        type: "image_url",
        image_url: {
          url: `data:${image.mimeType};base64,${image.base64Data}`
        }
      });
    }

    return {
      multimodalContent,
      hasImage: extracted.images.length > 0
    };
  }

  // 为API请求清理内联数据的函数 - 增强版
  function cleanInlineContentForAPI(content: string | any[]): string {
    if (!content) return '';

    // 如果是数组（多模态内容），返回占位符
    if (Array.isArray(content)) {
      return '[多模态内容]';
    }

    let cleanedContent = content;
    // 1. 清理原始内联数据格式
    cleanedContent = cleanedContent.replace(
      /<inline><data>.*?<\/data><mimeType>(.*?)<\/mimeType><\/inline>/g,
      '[图像内容，MIME类型: $1]'
    );

    // 2. 清理已转换为HTML的图像标签
    cleanedContent = cleanedContent.replace(
      /<img\s+src="data:(.*?);base64,.*?"\s+alt=".*?"\s+style=".*?".*?\/>/g,
      '[图像内容，MIME类型: $1]'
    );

    // 3. 清理其他可能的base64数据
    cleanedContent = cleanedContent.replace(
      /data:(.*?);base64,[a-zA-Z0-9+/=]+/g,
      '[图像内容，MIME类型: $1]'
    );

    return cleanedContent;
  }


  // 从URL中获取model参数和模型数据
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const modelParam = params.get('model');
    const modelDataParam = params.get('modelData');

    // 设置模型名称
    setModel(modelParam || '');

    // 解析模型数据
    if (modelDataParam) {
      try {
        const modelInfo: Model = JSON.parse(decodeURIComponent(modelDataParam));
        setCurrentModelInfo(modelInfo);

        // 检查模型是否支持vision和video功能
        if (modelInfo.features) {
          const features = JSON.parse(modelInfo.features);
          setHasVisionCapability(features.vision === true);
          setHasVideoCapability(features.video === true);
        }
      } catch (error) {
        console.error('Failed to parse model data:', error);
        // 如果解析失败，回退到原来的逻辑
        if (modelParam) {
          fetchModelInfoFallback(modelParam);
        }
      }
    } else if (modelParam) {
      // 如果没有modelData参数，回退到原来的逻辑
      fetchModelInfoFallback(modelParam);
    }
  }, []);

  // 回退方案：当URL中没有modelData时使用
  const fetchModelInfoFallback = async (modelParam: string) => {
    try {
      const endpointDetails = await getEndpointDetails('/v1/chat/completions', modelParam, []);
      const modelInfo = endpointDetails.models.find(m => m.modelName === modelParam || m.terminalModel === modelParam);
      if (modelInfo) {
        setCurrentModelInfo(modelInfo);
        // 检查模型是否支持vision和video功能
        const features = JSON.parse(modelInfo.features || '{}');
        setHasVisionCapability(features.vision === true);
        setHasVideoCapability(features.video === true);
      }
    } catch (error) {
      console.error('Failed to fetch model info:', error);
    }
  };

  // 初始化ChatCompletionsWriter
  useEffect(() => {
    // 先清理旧实例的监听器
    if (chatCompletionsWriterRef.current) {
      chatCompletionsWriterRef.current.removeAllListeners();
    }

    const protocol = typeof window !== 'undefined' ? window.location.protocol : 'http:';
    const host = api_host || window.location.host;
    const newWriter = new ChatCompletionsProcessor({
      url: `${protocol}//${host}/v1/chat/completions`,
      headers: {
        'Content-Type': 'application/json'
      }
    });

    chatCompletionsWriterRef.current = newWriter;
    const writer = newWriter;

    // 设置事件监听
    writer.on(ChatCompletionsEventType.START, () => {
      setStreamingResponse('');
      setMessages(prev => {
        const newMessages = [...prev];
        newMessages.push({
          id: uuidv4(),
          role: 'assistant',
          content: '',
          timestamp: Date.now()
        });
        return newMessages;
      });
      // 重置内联数据处理状态
      setIsReceivingInline(false);
      setInlineBuffer('');
      // 重置usage处理标志
      usageProcessedRef.current = false;
    });

    writer.on(ChatCompletionsEventType.DELTA, (data) => {
      // 处理 reasoning_content_signature 字段（作为 delta 的独立字段）
      if (data.reasoning_content_signature) {
        setMessages(prev => {
          const messages = [...prev];
          if (messages.length > 0 && messages[messages.length - 1].role === 'assistant') {
            messages[messages.length - 1] = {
              ...messages[messages.length - 1],
              reasoning_content_signature: data.reasoning_content_signature
            };
          }
          return messages;
        });
      }

      if (!data.content) return;

      let processedContent = data.content;

      // 检查是否开始接收内联数据
      if (processedContent.includes('<inline>') && !processedContent.includes('</inline>')) {
        setIsReceivingInline(true);
        setInlineBuffer(prev => prev + processedContent);

        // 更新消息，但不处理内联数据
        setMessages(prev => {
          const messages = [...prev];
          if (messages.length > 0 && messages[messages.length - 1].role === 'assistant') {
            const lastMsg = messages[messages.length - 1];
            messages[messages.length - 1] = {
              ...lastMsg,
              content: (lastMsg.content || '') + processedContent
            };
          }
          return messages;
        });
        return;
      }

      // 处理内联数据的中间和结束
      if (isReceivingInline) {
        const updatedBuffer = inlineBuffer + processedContent;
        setInlineBuffer(updatedBuffer);

        // 更新消息内容，但暂不处理
        setMessages(prev => {
          const messages = [...prev];
          if (messages.length > 0 && messages[messages.length - 1].role === 'assistant') {
            const lastMsg = messages[messages.length - 1];
            messages[messages.length - 1] = {
              ...lastMsg,
              content: (lastMsg.content || '') + processedContent
            };
          }
          return messages;
        });

        // 检查是否接收完成
        if (processedContent.includes('</inline>')) {
          setIsReceivingInline(false);

          // 处理完整的内联数据
          const processedInlineContent = processInlineContent(updatedBuffer);

          // 创建多模态内容
          const { multimodalContent, hasImage } = createMultimodalContent(updatedBuffer);

          // 更新消息，替换原始内联数据为处理后的内容
          setMessages(prev => {
            const messages = [...prev];
            if (messages.length > 0 && messages[messages.length - 1].role === 'assistant') {
              const lastMsg = messages[messages.length - 1];
              const currentContent = typeof lastMsg.content === 'string' ? lastMsg.content : '';
              // 替换内联数据部分
              const newContent = currentContent.replace(updatedBuffer, processedInlineContent);

              messages[messages.length - 1] = {
                ...lastMsg,
                content: newContent,
                rawInlineData: updatedBuffer,
                processedContent: processedInlineContent,
                apiContent: cleanInlineContentForAPI(newContent),
                multimodalContent: multimodalContent,
                hasImage: hasImage
              };
            }
            return messages;
          });

          // 更新流式响应
          setStreamingResponse(prev => {
            return prev.replace(updatedBuffer, processedInlineContent);
          });

          // 清空缓冲区
          setInlineBuffer('');
        }
        return;
      }

      // 正常处理非内联数据
      setMessages(prev => {
        const messages = [...prev];
        // 检查最后一条消息是否是助手消息
        if (messages.length > 0 && messages[messages.length - 1].role === 'assistant') {
          // 更新现有助手消息
          const lastMsg = messages[messages.length - 1];
          const current : EnhancedChatMessage = {
            ...lastMsg,
          }
          if(data.reasoning_content && data.isReasoningContent) {
            current.reasoning_content = (lastMsg.reasoning_content || '') + data.reasoning_content;
            current.isReasoningContent = true
          } else if(processedContent) {
            const currentContentStr = typeof lastMsg.content === 'string' ? lastMsg.content : '';
            current.content = currentContentStr + processedContent;
            // 同时更新apiContent
            current.apiContent = cleanInlineContentForAPI(current.content);

            // 检查是否需要更新多模态内容
            if (typeof current.content === 'string' && (current.content.includes('<img') || current.content.includes('data:image/'))) {
              const { multimodalContent, hasImage } = createMultimodalContent(current.content);
              current.multimodalContent = multimodalContent;
              current.hasImage = hasImage;
            }
          }
          messages[messages.length - 1] = current;
        }
        return messages;
      });
      // 直接更新流式响应状态
      setStreamingResponse(prev => prev + processedContent);
    });

    writer.on(ChatCompletionsEventType.FINISH, (data: any) => {
      setIsLoading(false);
      // 完成时重置流式响应和思考内容
      setStreamingResponse('');

      // 使用后端返回的 usage 信息更新 token 统计
      // 使用 ref 防止重复处理 - FINISH 事件可能触发多次
      if (data?.usage && !usageProcessedRef.current) {
        usageProcessedRef.current = true; // 标记为已处理
        // 先在 ref 中累加
        sessionTokensRef.current = {
          inputTokens: sessionTokensRef.current.inputTokens + (data.usage.prompt_tokens || 0),
          outputTokens: sessionTokensRef.current.outputTokens + (data.usage.completion_tokens || 0),
          cacheCreationTokens: sessionTokensRef.current.cacheCreationTokens + (data.usage.cache_creation_tokens || 0),
          cacheReadTokens: sessionTokensRef.current.cacheReadTokens + (data.usage.cache_read_tokens || 0)
        };
        // 然后直接设置 state（避免 React 双重渲染导致的重复累加）
        setSessionTokens(sessionTokensRef.current);
      }

      // 确保所有内联数据都已处理完成
      if (isReceivingInline && inlineBuffer) {
        // 如果还有未处理完的内联数据，尝试处理
        const processedContent = processInlineContent(inlineBuffer);
        const { multimodalContent, hasImage } = createMultimodalContent(inlineBuffer);

        setMessages(prev => {
          const messages = [...prev];
          if (messages.length > 0 && messages[messages.length - 1].role === 'assistant') {
            const lastMsg = messages[messages.length - 1];
            const currentContent = typeof lastMsg.content === 'string' ? lastMsg.content : '';
            // 替换内联数据部分
            const newContent = currentContent.replace(inlineBuffer, processedContent);

            messages[messages.length - 1] = {
              ...lastMsg,
              content: newContent,
              rawInlineData: inlineBuffer,
              processedContent: processedContent,
              apiContent: cleanInlineContentForAPI(newContent),
              multimodalContent: multimodalContent,
              hasImage: hasImage
            };
          }
          return messages;
        });

        // 重置内联数据状态
        setIsReceivingInline(false);
        setInlineBuffer('');
      }

      // 最终检查所有消息，确保所有内联内容都已处理
      setMessages(prev => {
        return prev.map(msg => {
          if (msg.role === 'assistant' && typeof msg.content === 'string' && msg.content) {
            // 检查消息内容是否包含未处理的内联数据
            if (msg.content.includes('<inline>') && msg.content.includes('</inline>')) {
              const processedContent = processInlineContent(msg.content);
              const { multimodalContent, hasImage } = createMultimodalContent(msg.content);

              return {
                ...msg,
                content: processedContent,
                processedContent: processedContent,
                apiContent: cleanInlineContentForAPI(processedContent),
                multimodalContent: multimodalContent,
                hasImage: hasImage
              };
            }

            // 检查是否需要更新多模态内容
            if (!msg.multimodalContent && (msg.content.includes('<img') || msg.content.includes('data:image/'))) {
              const { multimodalContent, hasImage } = createMultimodalContent(msg.content);
              return {
                ...msg,
                multimodalContent: multimodalContent,
                hasImage: hasImage
              };
            }
          }
          return msg;
        });
      });
    });

    writer.on(ChatCompletionsEventType.ERROR, (error) => {
      setError(`请求错误: ${error}`);
      setMessages(prev => {
        const messages = [...prev];
        // 检查最后一条消息是否是助手消息
        if (messages.length > 0 && messages[messages.length - 1].role === 'assistant') {
          // 更新现有助手消息
          messages[messages.length - 1] = {
            ...messages[messages.length - 1],
            error: error
          };
        }
        return messages;
      });
      setIsLoading(false);
      // 重置内联数据状态
      setIsReceivingInline(false);
      setInlineBuffer('');
    });

    return () => {
      // 清理事件监听
      if (writer) {
        writer.removeAllListeners();
      }
    };
  }, [model]); // 只在 model 改变时重新初始化

  // 自动滚动到底部
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, streamingResponse]);

  // 组件卸载时清理所有Object URLs
  useEffect(() => {
    return () => {
      uploadedVideos.forEach(video => {
        if (video.objectUrl) {
          URL.revokeObjectURL(video.objectUrl);
        }
      });
    };
  }, [uploadedVideos]);

  // 更新系统提示词
  const updateSystemPrompt = () => {
    // 更新messages中的system消息
    setMessages(prev => [
      { id: uuidv4(), role: 'system', content: systemPrompt, timestamp: Date.now() },
      ...prev.filter(msg => msg.role !== 'system')
    ]);
    setIsPromptExpanded(false);
    setIsEditingPrompt(false);
  };

  // 清除对话
  const clearConversation = () => {
    // 保留系统提示词，清除所有其他消息
    const systemMsg = messages.find(msg => msg.role === 'system') || {
      id: uuidv4(),
      role: 'system',
      content: systemPrompt,
      timestamp: Date.now()
    };
    setMessages([systemMsg]);
    setError('');
    setStreamingResponse('');
    // 重置内联数据状态
    setIsReceivingInline(false);
    setInlineBuffer('');

    // 清除上传的视频并释放Object URLs
    uploadedVideos.forEach(video => {
      if (video.objectUrl) {
        URL.revokeObjectURL(video.objectUrl);
      }
    });

    // 清除上传的图片和视频
    setUploadedImages([]);
    setUploadedVideos([]);

    // 重置 token 统计（同时重置 ref 和 state）
    sessionTokensRef.current = {
      inputTokens: 0,
      outputTokens: 0,
      cacheCreationTokens: 0,
      cacheReadTokens: 0
    };
    setSessionTokens(sessionTokensRef.current);
  };

  // 发送消息 - 支持多模态格式
  const sendMessage = async () => {
    if ((!input.trim() && uploadedImages.length === 0 && uploadedVideos.length === 0) || isLoading) return;

    // 如果正在编辑系统提示词，先保存
    if (isEditingPrompt) {
      updateSystemPrompt();
    }

    // 先停止之前的加载状态
    setIsLoading(false);
    // 清除之前的错误
    setError('');
    // 等待一帧渲染完成，确保不会同时有多条加载消息
    setTimeout(() => {
      setIsLoading(true);
    }, 0);

    // 创建用户消息内容（支持多模态）
    let userMessageHasImage = false;
    let userMessageMultimodalContent: MultimodalContentItem[] = [];

    // 如果有上传的图片或视频，创建多模态内容
    if (uploadedImages.length > 0 || uploadedVideos.length > 0) {
      userMessageHasImage = true;

      // 添加文本内容（如果有）
      if (input.trim()) {
        userMessageMultimodalContent.push({
          type: "text",
          text: input.trim()
        });
      }

      // 添加视频内容（视频在前）
      uploadedVideos.forEach(video => {
        userMessageMultimodalContent.push({
          type: "video_url",
          video_url: {
            url: video.base64
          },
          fps: videoFps
        });
      });

      // 添加图片内容
      uploadedImages.forEach(image => {
        userMessageMultimodalContent.push({
          type: "image_url",
          image_url: {
            url: image.base64
          }
        });
      });
    }

    // 添加用户消息
    const userMessage: EnhancedChatMessage = {
      id: uuidv4(),
      role: 'user',
      content: userMessageHasImage ? '' : input, // 当有图片时，内容为空，实际内容在multimodalContent中
      timestamp: Date.now(),
      multimodalContent: userMessageHasImage ? userMessageMultimodalContent : undefined,
      hasImage: userMessageHasImage
    };

    // 重置流式响应和思考内容
    setStreamingResponse('');
    // 重置内联数据状态
    setIsReceivingInline(false);
    setInlineBuffer('');

    // 清空输入、图片和视频，并清理Object URLs
    setInput('');
    setUploadedImages([]);

    // 释放视频的Object URLs
    uploadedVideos.forEach(video => {
      if (video.objectUrl) {
        URL.revokeObjectURL(video.objectUrl);
      }
    });
    setUploadedVideos([]);

    try {
      // 重要：先获取当前所有消息的副本
      const allCurrentMessages = [...messages];

      // 添加用户消息到本地副本（不依赖状态更新）
      allCurrentMessages.push(userMessage);

      // 确保首条消息是system prompt
      const systemMessage = allCurrentMessages.find(msg => msg.role === 'system') || {
        role: 'system',
        content: systemPrompt
      };

      // 构建请求消息，支持多模态格式
      const requestMessages = [];

      // 添加系统消息
      requestMessages.push({
        role: systemMessage.role,
        content: systemMessage.content
      });

      // 处理非系统消息
      for (const msg of allCurrentMessages.filter(msg => msg.role !== 'system')) {
        const messageObj: any = {
          role: msg.role,
          content: ''
        };

        // 如果消息包含图像，使用多模态格式
        if (msg.hasImage && msg.multimodalContent) {
          messageObj.content = msg.multimodalContent;
        }
        // 否则，检查是否需要提取图像
        else if (typeof msg.content === 'string' && msg.content && (msg.content.includes('<inline>') ||
                 msg.content.includes('data:image/') ||
                 msg.content.includes('<img'))) {

          const { multimodalContent, hasImage } = createMultimodalContent(msg.content);

          if (hasImage) {
            // 使用多模态格式
            messageObj.content = multimodalContent;

            // 更新消息对象以包含多模态内容
            msg.multimodalContent = multimodalContent;
            msg.hasImage = true;
          } else {
            // 没有图像，使用普通格式
            messageObj.content = msg.apiContent || cleanInlineContentForAPI(msg.content);
          }
        }
        // 普通文本消息
        else {
          messageObj.content = typeof msg.content === 'string' ? msg.content : '';
        }

        // 如果消息存在 reasoning_content_signature，添加到请求中（适用于所有角色和模型）
        // reasoning_content_signature 作为 Message 对象的顶层字段
        if (msg.reasoning_content_signature) {
          messageObj.reasoning_content_signature = msg.reasoning_content_signature;
        }

        requestMessages.push(messageObj);
      }

      // 构建请求对象
      const request = {
        model: model,
        messages: requestMessages,
        stream: true,
        user: userInfo?.userId
      };

      // 在发送请求前，更新UI状态
      setMessages([...allCurrentMessages]);

      // 发送请求（token统计将在FINISH事件中通过后端返回的usage更新）
      await chatCompletionsWriterRef.current?.send(request);
    } catch (err) {
      setError(`发送请求失败: ${err instanceof Error ? err.message : String(err)}`);
      setIsLoading(false);
    }
  };

  // 取消请求
  const cancelRequest = () => {
    chatCompletionsWriterRef.current?.cancel();
    setIsLoading(false);
    // 重置内联数据状态
    setIsReceivingInline(false);
    setInlineBuffer('');
  };

  // 处理Enter键发送
  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      sendMessage();
    }
  };

  return (
    <>
      <div className="container mx-auto px-4 py-3 max-w-5xl h-screen flex flex-col">
        <div className="flex flex-col md:flex-row md:items-center md:justify-between mb-4">
          <h1 className="text-2xl font-bold">智能问答</h1>

          <div className="mt-2 md:mt-0 flex items-center gap-3">
            <span className="text-sm font-medium mr-2">模型:</span>
            <div className="p-2 rounded flex items-center gap-2">
              <span>{model}</span>
              {hasVisionCapability && (
                <span className="inline-flex items-center gap-1 px-2 py-1 bg-green-100 text-green-800 text-xs font-medium rounded-full">
                  <ImageIcon className="w-3 h-3" />
                  支持图像
                </span>
              )}
              {hasVideoCapability && (
                <span className="inline-flex items-center gap-1 px-2 py-1 bg-blue-100 text-blue-800 text-xs font-medium rounded-full">
                  <VideoIcon className="w-3 h-3" />
                  支持视频
                </span>
              )}
            </div>

            {/* 费用计算器按钮 */}
            <Button
              variant="outline"
              size="sm"
              onClick={() => setIsCostModalOpen(true)}
              className="flex items-center gap-2"
              title="费用计算器"
            >
              <Calculator className="w-4 h-4" />
              费用
            </Button>
          </div>
        </div>

      {/* 系统提示词编辑区域 */}
      <div className="mb-3 flex-shrink-0">
        <div
          className="flex items-center cursor-pointer"
          onClick={() => {
            if (!isLoading) {
              if (isPromptExpanded) {
                updateSystemPrompt();
              } else {
                setIsPromptExpanded(true);
                setIsEditingPrompt(true);
              }
            }
          }}
        >
          <span className="text-sm font-medium mr-1">系统提示词：</span>
          {!isPromptExpanded ? (
            <span className="text-sm text-gray-700 overflow-hidden text-ellipsis whitespace-nowrap flex-1">
              {systemPrompt.length > 50 ? systemPrompt.substring(0, 50) + '...' : systemPrompt}
            </span>
          ) : null}
          {!isEditingPrompt && (
            <svg
              xmlns="http://www.w3.org/2000/svg"
              className={`h-4 w-4 ml-1 transition-transform ${isPromptExpanded ? 'rotate-180' : ''}`}
              viewBox="0 0 20 20"
              fill="currentColor"
            >
              <path fillRule="evenodd" d="M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z" clipRule="evenodd" />
            </svg>
          )}
        </div>

        {isPromptExpanded && (
          <div className="mt-2">
            <div className="flex flex-col">
              <Textarea
                value={systemPrompt}
                onChange={(e) => setSystemPrompt(e.target.value)}
                className="min-h-[80px] bg-white border border-gray-300 focus:border-gray-400 focus:ring-gray-300"
                placeholder="输入系统提示词..."
                disabled={isLoading}
                autoFocus
              />
              <div className="self-end mt-2">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={updateSystemPrompt}
                  className="text-gray-600 hover:bg-gray-100"
                  disabled={isLoading}
                >
                  保存
                </Button>
              </div>
            </div>
          </div>
        )}
      </div>

      {error && (
        <Alert variant="destructive" className="mb-4">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      <Card className="shadow-sm flex-grow overflow-hidden flex flex-col">
        <CardContent className="p-3 flex-grow overflow-hidden">
          <div className="h-full overflow-y-auto p-3 bg-white rounded">
            {messages.filter(msg => msg.role !== 'system').length === 0 ? (
              <div className="flex items-center justify-center h-full text-gray-500">
                <p>开始一个新的对话</p>
              </div>
            ) : (
              <div className="space-y-2">
                {/* 按消息的时间戳顺序显示所有消息 */}
                {(() => {
                  // 只考虑非系统消息，按时间戳排序
                  const visibleMessages = messages
                    .filter(msg => msg.role !== 'system')
                    .sort((a, b) => (a.timestamp || 0) - (b.timestamp || 0));

                  return visibleMessages.map((msg) => {
                    const isUser = msg.role === 'user';
                    // 根据时间戳或序号确定是否是最新消息 - 只有用户最新消息后没有助手回复时才显示加载
                    const assistantMessages = messages.filter(m => m.role === 'assistant');
                    const lastAssistantMessage = assistantMessages.length > 0 ? assistantMessages[assistantMessages.length - 1] : null;

                    // 没有助手回复的最新用户消息才显示加载状态
                    const needsLoadingIndicator = isLoading && !isUser &&
                      msg.id === lastAssistantMessage?.id;

                    // 对于有图片或视频的用户消息，使用自定义显示
                    if (isUser && msg.hasImage && msg.multimodalContent) {
                      const textContent = msg.multimodalContent.find(item => item.type === 'text')?.text || '';
                      const imageItems = msg.multimodalContent.filter(item => item.type === 'image_url');
                      const videoItems = msg.multimodalContent.filter(item => item.type === 'video_url');

                      return (
                        <div key={msg.id} className={`flex justify-end mb-3`}>
                          <div className={`flex flex-row-reverse max-w-[85%]`}>
                            <div className={`flex-shrink-0 h-8 w-8 rounded-full flex items-center justify-center bg-blue-500 ml-2`}>
                              <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5 text-white" viewBox="0 0 20 20" fill="currentColor">
                                <path fillRule="evenodd" d="M10 9a3 3 0 100-6 3 3 0 000 6zm-7 9a7 7 0 1114 0H3z" clipRule="evenodd" />
                              </svg>
                            </div>
                            <div className={`py-2 px-3 rounded-lg bg-blue-100 text-blue-900`}>
                              <div className="whitespace-pre-wrap text-sm">
                                {videoItems.map((item, idx) => (
                                  <div key={idx} className="mb-2 p-2 border border-blue-300 rounded-lg bg-blue-50 flex items-center gap-2">
                                    <VideoIcon className="w-5 h-5 text-blue-600 flex-shrink-0" />
                                    <div className="text-xs">
                                      <div className="font-medium text-blue-800">视频内容</div>
                                      <div className="text-blue-600">
                                        FPS: {item.fps || '1'}
                                      </div>
                                    </div>
                                  </div>
                                ))}
                                {imageItems.map((item, idx) => (
                                  <img
                                    key={idx}
                                    src={item.image_url?.url}
                                    alt="用户上传的图片"
                                    className="max-w-xs rounded-lg mb-2"
                                  />
                                ))}
                                {textContent && <div>{textContent}</div>}
                              </div>
                            </div>
                          </div>
                        </div>
                      );
                    }

                    // 使用处理后的内容（如果有）
                    const displayContent = msg.processedContent || (typeof msg.content === 'string' ? msg.content : '') || '';

                    return (
                      <MessageComponent
                        key={msg.id}
                        isUser={isUser}
                        content={displayContent}
                        reasoning_content={msg.reasoning_content}
                        error={msg.error}
                        isLoading={needsLoadingIndicator}
                        // 添加一个新的属性，表示内容包含HTML
                        dangerousHTML={!!msg.processedContent}
                      />
                    );
                  });
                })()}


                <div ref={messagesEndRef} />
              </div>
            )}
          </div>
        </CardContent>
        <div className="p-2 bg-gray-50 border-t border-gray-100 flex justify-end">
          <Button
            variant="ghost"
            size="sm"
            onClick={clearConversation}
            className="text-gray-500 hover:text-blue-600 hover:bg-blue-50 flex items-center"
            title="清除对话"
          >
            <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4 mr-2" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <polyline points="1 4 1 10 7 10"></polyline>
              <polyline points="23 20 23 14 17 14"></polyline>
              <path d="M20.49 9A9 9 0 0 0 5.64 5.64L1 10m22 4l-4.64 4.36A9 9 0 0 1 3.51 15"></path>
            </svg>
            清除对话
          </Button>
        </div>
      </Card>

      <div className="flex items-end gap-3 mt-3 flex-shrink-0">
        <div className="flex-grow">
          {/* 视频预览区域 */}
          {uploadedVideos.length > 0 && (
            <div className="mb-2 p-2 border border-blue-200 rounded-lg bg-blue-50">
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs font-medium text-blue-800">视频内容</span>
                <div className="flex items-center gap-2">
                  <label className="text-xs text-blue-700">帧率(fps):</label>
                  <select
                    value={videoFps}
                    onChange={(e) => setVideoFps(e.target.value)}
                    className="text-xs px-2 py-1 border border-blue-300 rounded bg-white"
                    disabled={isLoading}
                  >
                    {VIDEO_FPS_OPTIONS.map(option => (
                      <option key={option.value} value={option.value}>
                        {option.label}
                      </option>
                    ))}
                  </select>
                </div>
              </div>
              <div className="text-xs text-blue-600 mb-2">
                提示：较高的fps可以捕捉更多画面细节，但会消耗更多token
              </div>
              <div className="flex flex-wrap gap-2">
                {uploadedVideos.map((video) => (
                  <div key={video.id} className="relative group">
                    <video
                      src={video.objectUrl || video.base64}
                      className="w-32 h-20 object-cover rounded border"
                      controls
                    />
                    <div className="absolute bottom-0 left-0 right-0 bg-black bg-opacity-60 text-white text-xs px-1 py-0.5 text-center">
                      {formatFileSize(video.file.size)}
                    </div>
                    <button
                      onClick={() => removeVideo(video.id)}
                      className="absolute -top-1 -right-1 w-5 h-5 bg-red-500 text-white rounded-full flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity"
                      disabled={isLoading}
                    >
                      <X className="w-3 h-3" />
                    </button>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* 图片预览区域 */}
          {uploadedImages.length > 0 && (
            <div className="mb-2 p-2 border border-gray-200 rounded-lg bg-gray-50">
              <div className="flex flex-wrap gap-2">
                {uploadedImages.map((image) => (
                  <div key={image.id} className="relative group">
                    <img
                      src={image.base64}
                      alt="上传的图片"
                      className="w-16 h-16 object-cover rounded border"
                    />
                    <button
                      onClick={() => removeImage(image.id)}
                      className="absolute -top-1 -right-1 w-5 h-5 bg-red-500 text-white rounded-full flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity"
                      disabled={isLoading}
                    >
                      <X className="w-3 h-3" />
                    </button>
                  </div>
                ))}
              </div>
            </div>
          )}

          <div className="relative">
            <Textarea
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder={
                hasVideoCapability && hasVisionCapability
                  ? "输入问题或上传图片/视频..."
                  : hasVideoCapability
                  ? "输入问题或上传视频..."
                  : hasVisionCapability
                  ? "输入问题或上传图片..."
                  : "输入问题..."
              }
              className="h-20 resize-none rounded-xl shadow-sm focus:border-blue-400 focus:ring-blue-400 pr-24"
              disabled={isLoading}
            />

            {/* 视频上传按钮 */}
            {hasVideoCapability && (
              <button
                onClick={openVideoSelector}
                className="absolute bottom-2 right-12 p-2 text-gray-500 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
                disabled={isLoading}
                title="上传视频"
              >
                <VideoIcon className="w-5 h-5" />
              </button>
            )}

            {/* 图片上传按钮 */}
            {hasVisionCapability && (
              <button
                onClick={openImageSelector}
                className="absolute bottom-2 right-2 p-2 text-gray-500 hover:text-green-600 hover:bg-green-50 rounded-lg transition-colors"
                disabled={isLoading}
                title="上传图片"
              >
                <ImageIcon className="w-5 h-5" />
              </button>
            )}
          </div>

          {/* 隐藏的文件输入 */}
          <input
            ref={fileInputRef}
            type="file"
            accept="image/*"
            multiple
            onChange={handleImageUpload}
            className="hidden"
          />
          <input
            ref={videoInputRef}
            type="file"
            accept={SUPPORTED_VIDEO_FORMATS.join(',')}
            onChange={handleVideoUpload}
            className="hidden"
          />
        </div>

        {isLoading ? (
          <Button
            variant="destructive"
            onClick={cancelRequest}
            className="h-12 px-5 rounded-full shadow-sm">
            取消
          </Button>
        ) : (
          <Button
            onClick={sendMessage}
            disabled={!input.trim() && uploadedImages.length === 0 && uploadedVideos.length === 0}
            className="h-12 px-6 rounded-full shadow-sm bg-blue-600 hover:bg-blue-700">
            <SendIcon className="h-5 w-5 mr-2" /> 发送
          </Button>
        )}
      </div>

        <div className="mt-2 text-xs text-gray-500 text-center flex-shrink-0">
          <p>提示：按Enter键发送，Shift+Enter换行</p>
        </div>
      </div>

      {/* 费用计算器弹窗 */}
      <CostCalculatorModal
        isOpen={isCostModalOpen}
        onClose={() => setIsCostModalOpen(false)}
        model={currentModelInfo}
        currentSessionTokens={sessionTokens}
      />
    </>
  );
}
