'use client';

import React, {useEffect, useRef, useState} from 'react';
import {Button} from '@/components/ui/button';
import {Card, CardContent} from '@/components/ui/card';
import {Alert, AlertDescription} from '@/components/ui/alert';
import {Textarea} from '@/components/ui/textarea';
import {StopCircleIcon, TrashIcon} from 'lucide-react';
import {useAudioDevices} from "@/components/playground/AudioDeviceSelector";
import {
  FlashAudioRecorder,
  FlashAudioRecorderEventType,
  FlashTranscriptionResponse
} from "@/components/playground/FlashAudioRecorder";
import {api_host} from "@/config";


export default function FlashTranscriptionPlayground() {
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
  const [isProcessing, setIsProcessing] = useState(false);
  const [transcriptionText, setTranscriptionText] = useState('');
  const [errorMessage, setErrorMessage] = useState('');

  // 使用音频设备选择钩子
  const { audioSources, selectedSource, setSelectedSource } = useAudioDevices(setErrorMessage);

  // Refs
  const flashAudioRecorderRef = useRef<FlashAudioRecorder | null>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  // 初始化音频录制器
  useEffect(() => {
    if (!selectedSource) return;
    if (typeof window === 'undefined') return;
    const protocol = typeof window !== 'undefined' ? window.location.protocol : 'http:';
    const host = api_host || window.location.host;
    // 创建 FlashAudioRecorder 实例
    const recorder = new FlashAudioRecorder({
      url: `${protocol}//${host}/v1/audio/asr/flash`,
      deviceId: selectedSource,
      model: model,
      maxDuration: 60000, // 60秒最大录音时长
    });

    // 设置事件监听
    recorder.on(FlashAudioRecorderEventType.RECORDING_START, () => {
      setIsRecording(true);
      setErrorMessage('');
    });

    recorder.on(FlashAudioRecorderEventType.RECORDING_COMPLETE, () => {
      setIsRecording(false);
      setIsProcessing(true);
    });

    recorder.on(FlashAudioRecorderEventType.TRANSCRIPTION_START, () => {
      setIsProcessing(true);
    });

    recorder.on(FlashAudioRecorderEventType.TRANSCRIPTION_COMPLETE, (result: FlashTranscriptionResponse) => {
      setIsProcessing(false);

      // 显示转录结果
      if (result.flash_result && result.flash_result.sentences && result.flash_result.sentences.length > 0) {
        const transcription = result.flash_result.sentences
          .map(sentence => sentence.text)
          .join('\n');

        setTranscriptionText(transcription);
      } else {
        setTranscriptionText('未检测到语音内容');
      }
    });

    recorder.on(FlashAudioRecorderEventType.ERROR, (error: string) => {
      setErrorMessage(error);
      setIsRecording(false);
      setIsProcessing(false);
    });

    flashAudioRecorderRef.current = recorder;

    // 清理函数
    return () => {
      if (flashAudioRecorderRef.current) {
        flashAudioRecorderRef.current.destroy();
      }
    };
  }, [selectedSource, model]);

  // 开始录音
  const startRecording = async () => {
    if (!selectedSource) {
      setErrorMessage('请先选择音频设备');
      return;
    }

    if (!flashAudioRecorderRef.current) {
      setErrorMessage('录音器未初始化');
      return;
    }

    try {
      clearTranscription();
      await flashAudioRecorderRef.current.start();
    } catch (error) {
      console.error('启动录音失败:', error);
      setErrorMessage(`启动录音失败: ${error}`);
    }
  };

  // 停止录音
  const stopRecording = async () => {
    if (!flashAudioRecorderRef.current || !isRecording) return;

    try {
      await flashAudioRecorderRef.current.stop();
      // 状态更新由事件监听器处理
    } catch (error) {
      console.error('停止录音失败:', error);
      setErrorMessage(`停止录音失败: ${error}`);
    }
  };

  // 清除转录内容
  const clearTranscription = () => {
    setTranscriptionText('');
  };

  // 处理音频源选择
  const handleSourceChange = (event: React.ChangeEvent<HTMLSelectElement>) => {
    if (setSelectedSource) {
      setSelectedSource(event.target.value);
    }
  };

  return (
    <div className="container mx-auto px-4 py-3 max-w-5xl h-screen flex flex-col">
      <div className="flex flex-col md:flex-row md:items-center md:justify-between mb-3 flex-shrink-0">
        <h1 className="text-2xl font-bold">一句话转录</h1>

        <div className="mt-2 md:mt-0 flex items-center space-x-2">
          <select
            value={selectedSource || ''}
            onChange={handleSourceChange}
            disabled={isRecording || isProcessing}
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
                disabled={!selectedSource || isProcessing}
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
              disabled={isRecording || isProcessing}
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
              placeholder={isRecording ? "录音中..." : isProcessing ? "转录中..." : "开始录音后，您的语音将被转写在这里..."}
              value={transcriptionText}
              readOnly
              className="h-full resize-none"
            />
            {(isRecording || isProcessing) && (
              <div className="absolute top-2 right-2">
                <div className={`px-2 py-1 rounded-full text-xs ${isRecording ? 'bg-red-100 text-red-800' : 'bg-blue-100 text-blue-800'}`}>
                  {isRecording ? '录音中' : '转录中'}
                  <span className="ml-1 inline-flex">
                    <span className="animate-ping absolute h-1.5 w-1.5 rounded-full bg-current"></span>
                    <span className="relative rounded-full h-1.5 w-1.5 bg-current"></span>
                  </span>
                </div>
              </div>
            )}
          </div>
        </CardContent>
      </Card>

      <div className="mt-3 text-xs text-gray-500 bg-gray-50 p-3 rounded flex-shrink-0">
        <p className="font-medium mb-1">使用说明:</p>
        <ol className="list-decimal pl-5 space-y-1">
          <li>选择一个麦克风设备</li>
          <li>点击录音按钮开始录制</li>
          <li>说出您想要转录的内容</li>
          <li>点击停止按钮结束录制</li>
          <li>系统会自动处理您的录音并显示转录结果</li>
        </ol>
      </div>
    </div>
  );
}
