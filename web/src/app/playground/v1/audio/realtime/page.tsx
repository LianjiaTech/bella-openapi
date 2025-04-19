'use client';

import React, {useCallback, useEffect, useRef, useState} from 'react';
import {Card, CardContent} from "@/components/ui/card";
import {Alert, AlertDescription} from "@/components/ui/alert";
import {Button} from "@/components/ui/button";
import {api_host} from "@/config";
import {PCMPlayer} from "@/components/playground/PCMPlayer";
import {
  RealtimeAudioRecorder,
  RealtimeAudioRecorderConfig,
  RealtimeAudioRecorderEventType
} from "@/components/playground/RealtimeAudioRecorder";
import {ChatMessage} from "@/components/ui/ChatMessage";
import {Message, useMessageHandler} from "@/components/playground/MessageHandler";
import {useAudioDevices} from "@/components/playground/AudioDeviceSelector";
import {useVoiceSelector} from "@/components/playground/VoiceSelector";
import {Textarea} from "@/components/ui/textarea";


export default function RealtimeAudioPlayground() {
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

  const [isRecording, setIsRecording] = useState(false);
  const [transcriptionText, setTranscriptionText] = useState('');
  const [llmResponseText, setLlmResponseText] = useState('');
  const [errorMessage, setErrorMessage] = useState('');
  const [systemPrompt, setSystemPrompt] = useState("你是一个全能的语音助理，你的回复会转成音频给用户，所以请尽可能简洁的回复，同时首句话尽快结束以便更好的进行流式合成语音,并且你需要注意你的回复内容便于阅读，不要包含无法阅读的内容。");
  const [isEditingPrompt, setIsEditingPrompt] = useState(false);
  const [isPromptExpanded, setIsPromptExpanded] = useState(false);
  const [messages, setMessages] = useState<Message[]>([
    { isUser: true, content: "你好，我想了解一下今天的天气怎么样？" },
    { isUser: false, content: "你好！今天北京天气晴朗，气温22°C到28°C，空气质量良好，是个适合户外活动的好天气。" },
    { isUser: true, content: "谢谢，那我想知道有什么户外活动推荐吗？" },
    { isUser: false, content: "当然！考虑到今天的好天气，我推荐你可以去公园野餐、骑自行车、参观户外博物馆或者去植物园。如果你喜欢运动，打网球或者慢跑也是不错的选择。你对哪种活动更感兴趣呢？" }
  ]);

  // 使用音频设备选择钩子
  const { audioSources, selectedSource, setSelectedSource } = useAudioDevices(setErrorMessage);

  // 使用声音选择钩子
  const { voiceTypes, voiceName, setVoiceName, showMoreVoices, toggleMoreVoices } = useVoiceSelector(model !== null && model !== '', model || '/v1/audio/realtime');

  const audioRecorderRef = useRef<RealtimeAudioRecorder | null>(null);
  const playerRef = useRef<PCMPlayer | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  // 重置状态
  const resetState = () => {
    setTranscriptionText("");
    setLlmResponseText("");
    setMessages([]);
  };

  // 获取WebSocket URL
  const getWebSocketUrl = () => {
    // 检测当前页面协议
    const protocol = typeof window !== 'undefined' ? window.location.protocol : 'http:';
    const wsProtocol = protocol === 'https:' ? 'wss:' : 'ws:';
    const host = api_host || window.location.host;
    return `${wsProtocol}//${host}/v1/audio/realtime?model=${model}`;
  };

  // 滚动到最新消息
  const scrollToBottom = useCallback(() => {
    setTimeout(() => {
      messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
    }, 100);
  }, []);

  // 使用消息处理钩子
  const { updateTranscription, updateLlmResponse } = useMessageHandler(messages, setMessages, scrollToBottom);

  // 监听转写文本变化
  useEffect(() => {
    if (transcriptionText) {
      updateTranscription(transcriptionText);
    }
  }, [transcriptionText, updateTranscription]);

  // 监听LLM响应变化
  useEffect(() => {
    if (llmResponseText) {
      updateLlmResponse(llmResponseText);
    }
  }, [llmResponseText, updateLlmResponse]);

  // 初始化PCM播放器和音频录制器
  useEffect(() => {
    // 初始化PCM播放器
    const player = new PCMPlayer({
      inputCodec: "Int16",
      channels: 1,
      sampleRate: 24000,
      flushTime: 100,
      fftSize: 2048
    });
    playerRef.current = player;

    // 组件卸载时清理资源
    return () => {
      if (playerRef.current) {
        playerRef.current.destroy();
      }
      if (audioRecorderRef.current && audioRecorderRef.current.isRecording()) {
        audioRecorderRef.current.stop();
      }
    };
  }, []);

  // 处理音频源选择
  const handleSourceChange = (event: React.ChangeEvent<HTMLSelectElement>) => {
    setSelectedSource(event.target.value);
  };

  // 切换录制状态
  const toggleRecording = async () => {
    if (isRecording) {
      if (audioRecorderRef.current) {
        await audioRecorderRef.current.stop();
        setIsRecording(false);

        // 停止音频播放
        if (playerRef.current) {
          await playerRef.current.stop();
        }
      }
    } else {
      // 清空所有历史对话
      resetState();

      // 如果之前已经创建过录音器，需要先释放资源
      if (audioRecorderRef.current) {
        await audioRecorderRef.current.stop();
      }

      // 创建新的实时录音器实例
      if (!audioRecorderRef.current) {
        const realtimeConfig : RealtimeAudioRecorderConfig = {
          deviceId: selectedSource,
          sampleRate: 16000,
          bufferSize: 3200,
          webSocketConfig: {
            url: getWebSocketUrl(),
            reconnectAttempts: 3,
            reconnectInterval: 2000,
            timeoutMs: 10000,
          },
          voiceType: voiceName ? voiceTypes[voiceName] : undefined,
          mode: 'chat',
          system_prompt: systemPrompt
        };

        // 创建实时录音器实例
        const recorder = new RealtimeAudioRecorder(realtimeConfig);

        // 设置事件监听
        recorder.on(RealtimeAudioRecorderEventType.ERROR, (error: string) => {
          setErrorMessage(error);
        });

        recorder.on(RealtimeAudioRecorderEventType.TRANSCRIPTION_UPDATE, (text: string) => {
          setTranscriptionText(text);
        });

        recorder.on(RealtimeAudioRecorderEventType.LLM_RESPONSE_UPDATE, (text: string) => {
          setLlmResponseText(text);
        });

        recorder.on(RealtimeAudioRecorderEventType.TTS_AUDIO_DATA, (data: Uint8Array) => {
          playerRef.current?.feed(data);
        });

        recorder.on(RealtimeAudioRecorderEventType.SPEECH_START, async () => {
          // 当用户开始说话时停止当前音频播放
          if (playerRef.current) {
            await playerRef.current.stop();
          }
        });

        audioRecorderRef.current = recorder;
      } else {
        // 更新配置
        audioRecorderRef.current.updateConfig(
          selectedSource,
          voiceName ? voiceTypes[voiceName] : undefined,
          systemPrompt
        );
      }

      // 开始录音
      const success = await audioRecorderRef.current.start();

      if (success) {
        setIsRecording(true);
        setErrorMessage("");
      }
    }
  };

  return (
    <div className="container mx-auto px-4 py-3 max-w-5xl h-screen flex flex-col">
      <div className="flex flex-col md:flex-row md:items-center md:justify-between mb-3 flex-shrink-0">
        <h1 className="text-2xl font-bold">实时对话</h1>

        <div className="mt-2 md:mt-0 flex items-center">
          <label className="text-sm font-medium mr-2">音频设备:</label>
          <select
            value={selectedSource}
            onChange={handleSourceChange}
            disabled={isRecording}
            className="p-2 border rounded w-64"
          >
            <option value="">--请选择音频设备--</option>
            {audioSources.map((source) => (
              <option key={source.deviceId} value={source.deviceId}>
                {source.label || "未命名设备"}
              </option>
            ))}
          </select>
        </div>
      </div>

      {/* 系统提示词编辑区域 */}
      <div className="mb-3 flex-shrink-0">
        <div
          className="flex items-center cursor-pointer"
          onClick={() => {
            if (!isRecording) {
              if (isPromptExpanded) {
                setIsPromptExpanded(false);
                setIsEditingPrompt(false);
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
                disabled={isRecording}
                autoFocus
              />
              <div className="self-end mt-2">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => {
                    setIsPromptExpanded(false);
                    setIsEditingPrompt(false);
                  }}
                  className="text-gray-600 hover:bg-gray-100"
                  disabled={isRecording}
                >
                  保存
                </Button>
              </div>
            </div>
          </div>
        )}
      </div>

      {errorMessage && (
        <Alert variant="destructive" className="mb-4">
          <AlertDescription>{errorMessage}</AlertDescription>
        </Alert>
      )}

      {/* 聊天界面 */}
      <Card className="shadow-sm">
        <CardContent className="p-4">
          <div className="min-h-[450px] max-h-[550px] overflow-y-auto p-4 bg-white rounded">
            {messages.length > 0 ? (
              <div className="space-y-2">
                {messages.map((msg, index) => (
                  <ChatMessage
                    key={index}
                    isUser={msg.isUser}
                    content={msg.content}
                  />
                ))}
                {/* 如果正在录音但没有转写文本，显示等待输入状态 */}
                {isRecording && !transcriptionText && (
                  <ChatMessage isUser={true} isLoading={true} content="" />
                )}
                {/* 如果有转写文本但没有AI回复，显示AI等待状态 */}
                {transcriptionText && !llmResponseText && (
                  <ChatMessage isUser={false} isLoading={true} content="" />
                )}
                {/* 用于自动滚动到底部的空div */}
                <div ref={messagesEndRef} />
              </div>
            ) : (
              <div className="flex items-center justify-center h-full text-gray-400">
                <p>开始对话后，您的对话将显示在这里</p>
              </div>
            )}
          </div>

          {/* 底部控制区域 */}
          <div className="mt-4 relative">
            {/* 录音控制按钮 - 中间 */}
            <div className="flex justify-center">
              <button
                onClick={toggleRecording}
                className={`flex items-center justify-center px-4 py-2 rounded-full transition-all duration-300 shadow-md ${
                  isRecording 
                    ? "bg-red-500 hover:bg-red-600 text-white" 
                    : "bg-blue-500 hover:bg-blue-600 text-white"
                }`}
              >
                {isRecording ? (
                  <>
                    <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4 mr-1.5" viewBox="0 0 20 20" fill="currentColor">
                      <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8 7a1 1 0 00-1 1v4a1 1 0 001 1h4a1 1 0 001-1V8a1 1 0 00-1-1H8z" clipRule="evenodd" />
                    </svg>
                    停止对话
                  </>
                ) : (
                  <>
                    <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4 mr-1.5" viewBox="0 0 20 20" fill="currentColor">
                      <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM9.555 7.168A1 1 0 008 8v4a1 1 0 001.555.832l3-2a1 1 0 000-1.664l-3-2z" clipRule="evenodd" />
                    </svg>
                    开始对话
                  </>
                )}
              </button>
            </div>

            {/* 声音选择 - 右下角 */}
            {Object.keys(voiceTypes).length > 0 && (
              <div className="absolute bottom-0 right-4 flex items-center space-x-1">
                {/* 如果声音选项不超过2个，直接显示所有选项 */}
                {Object.keys(voiceTypes).length <= 2 ? (
                  Object.keys(voiceTypes).map((voice) => (
                    <button
                      key={voice}
                      onClick={() => !isRecording && setVoiceName(voice)}
                      disabled={isRecording}
                      className={`px-3 py-1 rounded-md text-sm transition-colors ${
                        voiceName === voice 
                          ? "bg-blue-100 text-blue-600 font-medium" 
                          : "bg-gray-100 hover:bg-gray-200 text-gray-600"
                      } ${isRecording ? "opacity-50 cursor-not-allowed" : ""}`}
                    >
                      {voice}
                    </button>
                  ))
                ) : (
                  <>
                    {/* 显示前两个选项 */}
                    {Object.keys(voiceTypes).slice(0, 2).map((voice) => (
                      <button
                        key={voice}
                        onClick={() => !isRecording && setVoiceName(voice)}
                        disabled={isRecording}
                        className={`px-3 py-1 rounded-md text-sm transition-colors ${
                          voiceName === voice 
                            ? "bg-blue-100 text-blue-600 font-medium" 
                            : "bg-gray-100 hover:bg-gray-200 text-gray-600"
                        } ${isRecording ? "opacity-50 cursor-not-allowed" : ""}`}
                      >
                        {voice}
                      </button>
                    ))}

                    {/* 更多选项下拉菜单 */}
                    <div className="relative">
                      <button
                        disabled={isRecording}
                        onClick={() => !isRecording && toggleMoreVoices()}
                        className={`px-3 py-1 rounded-md text-sm bg-gray-100 hover:bg-gray-200 transition-colors ${isRecording ? "opacity-50 cursor-not-allowed" : ""}`}
                      >
                        ...
                      </button>

                      {/* 下拉菜单 */}
                      {showMoreVoices && (
                        <div className="absolute right-0 bottom-full mb-2 bg-white shadow-lg rounded-md p-2 w-48 z-10">
                          <p className="text-xs text-gray-500 mb-1 font-medium">更多声音选项:</p>
                          {Object.keys(voiceTypes).slice(2).map((voice) => (
                            <div
                              key={voice}
                              onClick={() => {
                                if (!isRecording) {
                                  setVoiceName(voice);
                                  toggleMoreVoices();
                                }
                              }}
                              className={`p-2 rounded cursor-pointer ${voiceName === voice ? 'bg-blue-100' : 'hover:bg-gray-100'} ${isRecording ? 'opacity-50 cursor-not-allowed' : ''}`}
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
        </CardContent>
      </Card>

      <div className="mt-3 text-xs text-gray-500 bg-gray-50 p-3 rounded flex-shrink-0">
        <p className="font-medium mb-1">使用说明:</p>
        <ol className="list-decimal pl-5 space-y-1">
          <li>选择音频设备</li>
          <li>点击"开始对话"按钮开始捕获音频</li>
          <li>说话后会自动进行语音识别并显示转写结果</li>
          <li>语音识别完成后，AI会自动回复并通过语音播放</li>
          <li>点击"停止对话"按钮结束对话</li>
        </ol>
      </div>
    </div>
  );
}
