'use client';

import React, {useEffect, useRef, useState} from 'react';
import {Button} from '@/components/ui/button';
import {Card, CardContent} from '@/components/ui/card';
import {Alert, AlertDescription} from '@/components/ui/alert';
import {api_host} from "@/config";
import {RealtimeAudioRecorder, RealtimeAudioRecorderEventType} from "@/components/playground/RealtimeAudioRecorder";
import {Textarea} from '@/components/ui/textarea';
import {StopCircleIcon, TrashIcon} from 'lucide-react';
import {useAudioDevices} from "@/components/playground/AudioDeviceSelector";


export default function RealtimeTranscriptionPlayground() {
  // 状态管理
  const [model, setModel] = useState('');

  // 从 URL 中获取 model 参数
  useEffect(() => {
    // 从 URL 参数中获取 model 值
    const params = new URLSearchParams(window.location.search);
    const modelParam = params.get('model');
    // 如果有参数，则使用它
    if (modelParam) {
      setModel(modelParam);
    }
  }, []);

  // 状态管理
  const [isRecording, setIsRecording] = useState(false);
  const [transcriptionText, setTranscriptionText] = useState('');
  const [errorMessage, setErrorMessage] = useState('');

  // 使用音频设备选择钩子
  const { audioSources, selectedSource, setSelectedSource } = useAudioDevices(setErrorMessage);

  // Refs
  const audioRecorderRef = useRef<RealtimeAudioRecorder | null>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  // 获取WebSocket URL
  const getWebSocketUrl = () => {
    const protocol = typeof window !== 'undefined' ? window.location.protocol : 'http:';
    const wsProtocol = protocol === 'https:' ? 'wss:' : 'ws:';
    const host = api_host || window.location.host;
    return `${wsProtocol}//${host}/v1/audio/asr/stream?model=${model}`;
  };

  // 初始化音频录制器
  useEffect(() => {
    // 如果没有选择音频设备，不初始化录音器
    if (!selectedSource) return;

    // 创建音频录制器
    const recorder = new RealtimeAudioRecorder({
      deviceId: selectedSource,
      sampleRate: 16000,
      bufferSize: 3200,
      webSocketConfig: {
        url: getWebSocketUrl(),
        reconnectAttempts: 3,
        reconnectInterval: 2000,
        timeoutMs: 10000,
      },
      mode: 'transcription'
    });

    // 设置事件监听
    recorder.on(RealtimeAudioRecorderEventType.TRANSCRIPTION_UPDATE, (text: string) => {
      setTranscriptionText(text);
      // 自动滚动到底部
      if (textareaRef.current) {
        textareaRef.current.scrollTop = textareaRef.current.scrollHeight;
      }
    });

    recorder.on(RealtimeAudioRecorderEventType.ERROR, (error: string) => {
      setErrorMessage(`错误: ${error}`);
      setIsRecording(false);
    });

    recorder.on(RealtimeAudioRecorderEventType.ASR_CLOSED, (_data: any) => {
      setIsRecording(false);
    });

    // 保存到ref
    audioRecorderRef.current = recorder;

    return () => {
      if (recorder) {
        recorder.destroy();
      }
    };
  }, [selectedSource, model]);

  // 开始录音
  const startRecording = async () => {
    if (!audioRecorderRef.current) {
      setErrorMessage('录音器未初始化');
      return;
    }

    try {
      clearTranscription();
      const success = await audioRecorderRef.current.start();
      if (success) {
        setIsRecording(true);
        setErrorMessage('');
      }
    } catch (error) {
      setErrorMessage(`启动录音失败: ${error}`);
    }
  };

  // 停止录音
  const stopRecording = async () => {
    if (!audioRecorderRef.current || !isRecording) return;

    try {
      await audioRecorderRef.current.stop();
      setIsRecording(false);
    } catch (error) {
      setErrorMessage(`停止录音失败: ${error}`);
    }
  };

  // 清除转录内容
  const clearTranscription = () => {
    setTranscriptionText('');
  };

  // 处理音频源选择
  const handleSourceChange = (event: React.ChangeEvent<HTMLSelectElement>) => {
    setSelectedSource(event.target.value);
  };

  return (
    <div className="container mx-auto px-4 py-3 max-w-5xl h-screen flex flex-col">
      <div className="flex flex-col md:flex-row md:items-center md:justify-between mb-3 flex-shrink-0">
        <h1 className="text-2xl font-bold">实时转录</h1>

        <div className="mt-2 md:mt-0 flex items-center space-x-2">
          <select
            value={selectedSource || ''}
            onChange={handleSourceChange}
            disabled={isRecording}
            className="p-2 border rounded w-48 text-sm"
          >
            <option value="">--选择麦克风--</option>
            {audioSources.map((source) => (
              <option key={source.deviceId} value={source.deviceId}>
                {source.label || "未命名设备"}
              </option>
            ))}
          </select>

          <div className="flex space-x-1">
            {!isRecording ? (
              <Button
                size="sm"
                onClick={startRecording}
                disabled={!selectedSource}
                className="px-3 py-1"
              >
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" className="w-4 h-4">
                  <path d="M8.25 4.5a3.75 3.75 0 117.5 0v8.25a3.75 3.75 0 11-7.5 0V4.5z" />
                  <path d="M6 10.5a.75.75 0 01.75.75v1.5a5.25 5.25 0 1010.5 0v-1.5a.75.75 0 011.5 0v1.5a6.751 6.751 0 01-6 6.709v2.291h3a.75.75 0 010 1.5h-7.5a.75.75 0 010-1.5h3v-2.291a6.751 6.751 0 01-6-6.709v-1.5A.75.75 0 016 10.5z" />
                </svg>
              </Button>
            ) : (
              <Button
                size="sm"
                onClick={stopRecording}
                variant="destructive"
                className="px-3 py-1"
              >
                <StopCircleIcon className="h-4 w-4" />
              </Button>
            )}
            <Button
              size="sm"
              variant="outline"
              onClick={clearTranscription}
              title="清除转录内容"
              className="px-3 py-1"
            >
              <TrashIcon className="h-4 w-4" />
            </Button>
          </div>
        </div>
      </div>

      {errorMessage && (
        <Alert variant="destructive" className="mb-4">
          <AlertDescription>{errorMessage}</AlertDescription>
        </Alert>
      )}

      <Card className="flex-grow overflow-hidden flex flex-col">
        <CardContent className="p-3 flex-grow overflow-hidden">
          <div className="relative h-full">
            <Textarea
              ref={textareaRef}
              className="h-full p-3 font-medium text-foreground bg-muted/30 resize-none"
              placeholder="开始录音后，转录内容会显示在这里..."
              value={transcriptionText}
              readOnly
            />

            {isRecording && (
              <div className="absolute top-3 right-3">
                <div className="flex items-center">
                  <div className="h-3 w-3 bg-red-500 rounded-full animate-pulse mr-2"></div>
                  <span className="text-xs text-muted-foreground">录音中...</span>
                </div>
              </div>
            )}
          </div>

          <div className="text-xs text-muted-foreground text-center absolute bottom-2 left-0 right-0">
            {isRecording ? '正在进行实时转录，请对着麦克风说话...' : '点击麦克风按钮开始转录'}
          </div>
        </CardContent>
      </Card>
      <div className="mt-4 text-sm text-gray-500 bg-gray-50 p-4 rounded">
        <p className="font-medium mb-1">使用说明:</p>
        <ol className="list-decimal pl-5 space-y-1">
          <li>选择一个麦克风设备</li>
          <li>点击录音按钮开始录制</li>
          <li>说出您想要转录的内容</li>
          <li>系统会实时处理您的录音并显示转录结果</li>
          <li>点击停止按钮结束录制</li>
        </ol>
      </div>
    </div>
  );
}
