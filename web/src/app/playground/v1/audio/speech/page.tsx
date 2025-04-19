'use client';

import React, { useEffect, useRef, useState } from 'react';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Textarea } from '@/components/ui/textarea';
import { PlayIcon, CircleStop, TrashIcon } from 'lucide-react';
import { api_host } from '@/config';
import { PCMPlayer } from '@/components/playground/PCMPlayer';
import { useVoiceSelector } from '@/components/playground/VoiceSelector';
import { useUser } from '@/lib/context/user-context';

export default function SpeechPlayground() {
  // 状态管理
  const [model, setModel] = useState('');
  const [inputText, setInputText] = useState('');
  const [isPlaying, setIsPlaying] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [isProcessing, setIsProcessing] = useState(false);
  const { userInfo } = useUser();

  // 从 URL 中获取 model 参数
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const modelParam = params.get('model');
    if (modelParam) {
      setModel(modelParam);
    }
  }, []);

  // 使用声音选择钩子
  const { voiceTypes, voiceName, setVoiceName, showMoreVoices, toggleMoreVoices } = useVoiceSelector(model !== null && model !== '', model || '/v1/audio/speech');

  // Refs
  const pcmPlayerRef = useRef<PCMPlayer | null>(null);
  const abortControllerRef = useRef<AbortController | null>(null);
  const playbackCheckIntervalRef = useRef<NodeJS.Timeout | null>(null);

  // 初始化 PCM 播放器
  useEffect(() => {
    if (typeof window === 'undefined') return;

    pcmPlayerRef.current = new PCMPlayer({
      inputCodec: 'Int16',
      channels: 1,
      sampleRate: 24000
    });

    return () => {
      if (pcmPlayerRef.current) {
        pcmPlayerRef.current.destroy();
      }
    };
  }, []);

  // 播放语音
  const playTTS = async () => {
    if (playbackCheckIntervalRef.current) {
      clearInterval(playbackCheckIntervalRef.current);
      playbackCheckIntervalRef.current = null;
    }
    if (!inputText.trim()) {
      setErrorMessage('请输入要转换为语音的文本');
      return;
    }

    try {
      setIsPlaying(true);
      setIsProcessing(true);
      setErrorMessage('');

      // 如果有正在进行的请求，取消它
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
      }

      // 创建新的 AbortController
      abortControllerRef.current = new AbortController();
      const signal = abortControllerRef.current.signal;

      // 准备请求参数
      const protocol = typeof window !== 'undefined' ? window.location.protocol : 'http:';
      const host = api_host || window.location.host;
      const url = `${protocol}//${host}/v1/audio/speech`;

      const requestBody = {
        user: userInfo?.userId,
        model: model,
        input: inputText,
        stream: true,
        voice: voiceTypes[voiceName],
        sample_rate: 24000,
        response_format: "pcm"
      };

      // 发送请求
      const response = await fetch(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        credentials: 'include',
        body: JSON.stringify(requestBody),
        signal
      });

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      // 处理流式响应
      const reader = response.body?.getReader();
      if (!reader) {
        throw new Error('无法获取响应流');
      }

      // 创建固定大小的数据块
      const CHUNK_SIZE = 2048; // 固定每次发送 2048 字节
      let buffer = new Uint8Array(0);

      // 读取和处理音频数据
      while (true) {
        const { done, value } = await reader.read();
        if (done) {
          if (buffer.length > 0 && pcmPlayerRef.current) {
            const finalChunk = new Uint8Array(CHUNK_SIZE);
            finalChunk.set(buffer);
            pcmPlayerRef.current.feed(finalChunk);
          }
          break;
        }

        if (value) {
          const newBuffer = new Uint8Array(buffer.length + value.length);
          newBuffer.set(buffer);
          newBuffer.set(value, buffer.length);
          buffer = newBuffer;
          while (buffer.length >= CHUNK_SIZE) {
            if (pcmPlayerRef.current) {
              const chunk = buffer.slice(0, CHUNK_SIZE);
              pcmPlayerRef.current.feed(chunk);
              buffer = buffer.slice(CHUNK_SIZE);
            }
          }
        }
      }
      // 使用PCMPlayer的isPlaybackEnded方法检查播放是否完成
      const checkPlaybackStatus = () => {
        if (pcmPlayerRef.current && pcmPlayerRef.current.isPlaybackEnded()) {
          stopPlayback();
        }
      };
      playbackCheckIntervalRef.current = setInterval(checkPlaybackStatus, 500);
      setIsProcessing(false);
    } catch (error: any) {
      if (error.name === 'AbortError') {
        console.log('请求被取消');
      } else {
        console.error('TTS 请求错误:', error);
        setErrorMessage(`语音合成失败: ${error.message}`);
      }
      setIsProcessing(false);
    }
  };

  // 停止播放
  const stopPlayback = () => {
    if (pcmPlayerRef.current) {
      pcmPlayerRef.current.stop();
    }

    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
      abortControllerRef.current = null;
    }

    if (playbackCheckIntervalRef.current) {
      clearInterval(playbackCheckIntervalRef.current);
      playbackCheckIntervalRef.current = null;
    }

    setIsPlaying(false);
    setIsProcessing(false);
  };

  // 清除文本
  const clearText = () => {
    setInputText('');
    stopPlayback();
  };

  return (
    <div className="container mx-auto px-4 py-3 max-w-5xl h-screen flex flex-col">
      <div className="flex flex-col md:flex-row md:items-center md:justify-between mb-3 flex-shrink-0">
        <h1 className="text-2xl font-bold">语音合成</h1>

        <div className="mt-2 md:mt-0 flex items-center space-x-2">
          {/* 声音选择区域 */}
          {Object.keys(voiceTypes).length > 0 && (
            <div className="flex items-center space-x-1">
              <span className="text-sm font-medium">声音:</span>
              {/* 如果声音选项不超过2个，直接显示所有选项 */}
              {Object.keys(voiceTypes).length <= 2 ? (
                Object.keys(voiceTypes).map((voice) => (
                  <button
                    key={voice}
                    onClick={() => setVoiceName(voice)}
                    disabled={isPlaying}
                    className={`px-3 py-1 rounded-md text-sm ${
                      voiceName === voice
                        ? "bg-blue-500 text-white"
                        : "bg-gray-100 hover:bg-gray-200 transition-colors"
                    } ${isPlaying ? "opacity-50 cursor-not-allowed" : ""}`}
                  >
                    {voice}
                  </button>
                ))
              ) : (
                <>
                  {/* 只显示前两个选项 */}
                  {Object.keys(voiceTypes).slice(0, 2).map((voice) => (
                    <button
                      key={voice}
                      onClick={() => setVoiceName(voice)}
                      disabled={isPlaying}
                      className={`px-3 py-1 rounded-md text-sm ${
                        voiceName === voice
                          ? "bg-blue-500 text-white"
                          : "bg-gray-100 hover:bg-gray-200 transition-colors"
                      } ${isPlaying ? "opacity-50 cursor-not-allowed" : ""}`}
                    >
                      {voice}
                    </button>
                  ))}

                  {/* 更多选项按钮 */}
                  <div className="relative">
                    <button
                      disabled={isPlaying}
                      onClick={() => !isPlaying && toggleMoreVoices()}
                      className={`px-3 py-1 rounded-md text-sm bg-gray-100 hover:bg-gray-200 transition-colors ${isPlaying ? "opacity-50 cursor-not-allowed" : ""}`}
                    >
                      ...
                    </button>
                    {/* 下拉菜单 */}
                    {showMoreVoices && (
                      <div className="absolute right-0 top-full mt-1 bg-white shadow-lg rounded-md p-2 w-48 z-10">
                        <p className="text-xs text-gray-500 mb-1 font-medium">更多声音选项:</p>
                        {Object.keys(voiceTypes).slice(2).map((voice) => (
                          <div
                            key={voice}
                            onClick={() => {
                              setVoiceName(voice);
                              toggleMoreVoices();
                            }}
                            className={`px-2 py-1 rounded-md text-sm cursor-pointer ${
                              voiceName === voice
                                ? "bg-blue-100 text-blue-700"
                                : "hover:bg-gray-100"
                            }`}
                          >
                            {voice}
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                </>
              )}
            </div>
          )}
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
              value={inputText}
              onChange={(e) => setInputText(e.target.value)}
              placeholder="请输入要转换为语音的文本..."
              className="h-full p-3 font-medium resize-none"
              disabled={isPlaying}
            />
            {isProcessing && (
              <div className="absolute top-2 right-2">
                <div className="px-2 py-1 rounded-full text-xs bg-blue-100 text-blue-800">
                  处理中
                  <span className="ml-1 inline-flex">
                    <span className="animate-ping absolute h-1.5 w-1.5 rounded-full bg-current"></span>
                    <span className="relative rounded-full h-1.5 w-1.5 bg-current"></span>
                  </span>
                </div>
              </div>
            )}
          </div>
        </CardContent>

        <div className="p-3 border-t flex justify-center space-x-2">
          {!isPlaying ? (
            <Button
              onClick={playTTS}
              disabled={!inputText.trim() || isProcessing}
              className="px-6 py-2 rounded-full"
            >
              <PlayIcon className="h-5 w-5 mr-2" /> 播放
            </Button>
          ) : (
            <Button
              onClick={stopPlayback}
              variant="destructive"
              className="px-6 py-2 rounded-full"
            >
              <CircleStop className="h-5 w-5 mr-2" /> 停止
            </Button>
          )}

          <Button
            variant="outline"
            onClick={clearText}
            disabled={!inputText.trim() || isPlaying}
            className="px-4 py-2 rounded-full"
          >
            <TrashIcon className="h-4 w-4 mr-2" /> 清除
          </Button>
        </div>
      </Card>

      <div className="mt-3 text-xs text-gray-500 bg-gray-50 p-3 rounded flex-shrink-0">
        <p className="font-medium mb-1">使用说明:</p>
        <ol className="list-decimal pl-5 space-y-1">
          <li>输入要转换为语音的文本</li>
          <li>选择合适的声音类型</li>
          <li>点击播放按钮开始语音合成</li>
          <li>合成完成后会自动播放语音</li>
          <li>可以随时点击停止按钮中断播放</li>
        </ol>
      </div>
    </div>
  );
}
