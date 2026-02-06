'use client';

import React, { useEffect, useRef, useState } from 'react';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Textarea } from '@/components/ui/textarea';
import { PlayIcon, CircleStop, TrashIcon, Upload, X } from 'lucide-react';
import { api_host } from '@/config';
import { PCMPlayer } from '@/components/playground/PCMPlayer';
import { useVoiceSelector } from '@/components/playground/VoiceSelector';
import { useUser } from '@/lib/context/user-context';
import { Model } from '@/lib/types/openapi';
import { getEndpointDetails } from '@/lib/api/meta';

interface AudioItem {
  id: string;
  speakerId: 'S1' | 'S2';
  file?: File;
  url?: string;
  preview: string;
  text: string;
  dialect?: string;
  isLoading?: boolean;
  error?: string;
}

interface CurrentFormState {
  file?: File;
  url?: string;
  preview?: string;
  text: string;
  dialect?: string;
  isLoading?: boolean;
  error?: string;
}

export default function SpeechPlayground() {
  const [model, setModel] = useState('');
  const [inputText, setInputText] = useState('');
  const [isPlaying, setIsPlaying] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [isProcessing, setIsProcessing] = useState(false);
  const { userInfo } = useUser();

  const [hasCustomizeSoundColorFeature, setHasCustomizeSoundColorFeature] = useState(false);
  const [currentModelInfo, setCurrentModelInfo] = useState<Model | null>(null);
  const [showCustomizeSection, setShowCustomizeSection] = useState(false);
  const [inputStatePrompt, setInputStatePrompt] = useState<string>('');

  const [speakers, setSpeakers] = useState<{
    S1?: AudioItem;
    S2?: AudioItem;
  }>({});

  const [currentSpeaker, setCurrentSpeaker] = useState<'S1' | 'S2'>('S1');
  const [showDialectInput, setShowDialectInput] = useState(false);
  const [audioInputMode, setAudioInputMode] = useState<'file' | 'url' | null>('file');
  const [currentForm, setCurrentForm] = useState<CurrentFormState>({
    text: '',
    dialect: ''
  });

  const audioInputRef = useRef<HTMLInputElement>(null);
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const modelParam = params.get('model');
    const modelDataParam = params.get('modelData');

    if (modelParam) {
      setModel(modelParam);
    }

    if (modelDataParam) {
      try {
        const modelInfo: Model = JSON.parse(decodeURIComponent(modelDataParam));
        setCurrentModelInfo(modelInfo);

        if (modelInfo.features) {
          const features = JSON.parse(modelInfo.features);
          setHasCustomizeSoundColorFeature(features.customize_sound_color === true);
        }

        if (modelInfo.properties) {
          try {
            const properties = JSON.parse(modelInfo.properties);
            if (properties.input_state) {
              setInputStatePrompt(properties.input_state);
            }
          } catch (err) {
            console.error('Failed to parse properties:', err);
          }
        }
      } catch (error) {
        console.error('Failed to parse model data:', error);
        if (modelParam) {
          fetchModelInfoFallback(modelParam);
        }
      }
    } else if (modelParam) {
      fetchModelInfoFallback(modelParam);
    }
  }, []);

  const fetchModelInfoFallback = async (modelParam: string) => {
    try {
      const endpointDetails = await getEndpointDetails('/v1/audio/speech', modelParam, []);
      const modelInfo = endpointDetails.models.find(
        m => m.modelName === modelParam || m.terminalModel === modelParam
      );
      if (modelInfo) {
        setCurrentModelInfo(modelInfo);

        const features = JSON.parse(modelInfo.features || '{}');
        setHasCustomizeSoundColorFeature(features.customize_sound_color === true);

        const properties = JSON.parse(modelInfo.properties || '{}');
        if (properties.input_state) {
          setInputStatePrompt(properties.input_state);
        }
      }
    } catch (error) {
      console.error('Failed to fetch model info:', error);
    }
  };

  const { voiceTypes, voiceName, setVoiceName, showMoreVoices, toggleMoreVoices } = useVoiceSelector(model !== null && model !== '', model || '/v1/audio/speech');

  const pcmPlayerRef = useRef<PCMPlayer | null>(null);
  const abortControllerRef = useRef<AbortController | null>(null);
  const playbackCheckIntervalRef = useRef<NodeJS.Timeout | null>(null);
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

  const handleAudioUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (!file.type.startsWith('audio/')) {
      setErrorMessage('请选择音频文件');
      return;
    }

    const MAX_SIZE = 10 * 1024 * 1024;
    if (file.size > MAX_SIZE) {
      setErrorMessage('音频文件不能超过10MB');
      return;
    }

    const reader = new FileReader();
    reader.onload = (event) => {
      const dataUrl = event.target?.result as string;
      setCurrentForm(prev => ({
        ...prev,
        file,
        url: undefined,
        preview: dataUrl
      }));
    };
    reader.readAsDataURL(file);
    e.target.value = '';
  };

  const handleUrlInput = (url: string) => {
    setCurrentForm(prev => ({
      ...prev,
      url,
      file: undefined,
      preview: undefined
    }));
  };

  const fetchAudioFromUrl = async (url: string) => {
    if (!url.trim()) {
      setErrorMessage('请输入有效的音频URL');
      return;
    }

    setCurrentForm(prev => ({
      ...prev,
      isLoading: true,
      error: undefined
    }));

    try {
      const response = await fetch('/api/fetch-audio', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ url })
      });

      if (!response.ok) throw new Error('获取音频失败');

      const data = await response.json();

      if (data.success) {
        setCurrentForm(prev => ({
          ...prev,
          preview: data.audioData,
          isLoading: false
        }));
      } else {
        throw new Error(data.error || '获取音频失败');
      }
    } catch (err) {
      setCurrentForm(prev => ({
        ...prev,
        isLoading: false,
        error: '获取音频失败'
      }));
      setErrorMessage(err instanceof Error ? err.message : '获取音频失败');
    }
  };

  const updateText = (text: string) => {
    setCurrentForm(prev => ({ ...prev, text }));
  };

  const updateDialect = (dialect: string) => {
    setCurrentForm(prev => ({ ...prev, dialect }));
  };

  const toggleDialectInput = () => {
    setShowDialectInput(prev => !prev);
    if (showDialectInput) {
      setCurrentForm(prev => ({ ...prev, dialect: '' }));
    }
  };

  const submitCurrentSpeaker = () => {
    if (!currentForm.text.trim()) {
      setErrorMessage('请填写音频提示词');
      return;
    }

    if (!currentForm.file && !currentForm.url) {
      setErrorMessage('请上传音频文件或输入URL');
      return;
    }

    const newSpeaker = {
      id: currentSpeaker,
      speakerId: currentSpeaker,
      file: currentForm.file,
      url: currentForm.url,
      preview: currentForm.preview || '',
      text: currentForm.text,
      dialect: currentForm.dialect || ''
    };

    setSpeakers(prev => ({
      ...prev,
      [currentSpeaker]: newSpeaker
    }));

    setCurrentForm({
      text: '',
      dialect: ''
    });
    setShowDialectInput(false);
    setAudioInputMode(null);

    if (currentSpeaker === 'S1') {
      setCurrentSpeaker('S2');
    }

    setErrorMessage('');
  };

  const editSpeaker = (speakerId: 'S1' | 'S2') => {
    const speaker = speakers[speakerId];
    if (!speaker) return;

    setCurrentForm({
      file: speaker.file,
      url: speaker.url,
      preview: speaker.preview,
      text: speaker.text,
      dialect: speaker.dialect || ''
    });

    setCurrentSpeaker(speakerId);
    setShowDialectInput(!!speaker.dialect);
    setAudioInputMode(speaker.file ? 'file' : speaker.url ? 'url' : null);

    setSpeakers(prev => {
      const newSpeakers = { ...prev };
      delete newSpeakers[speakerId];
      return newSpeakers;
    });
  };

  const deleteSpeaker = (speakerId: 'S1' | 'S2') => {
    setSpeakers(prev => {
      const newSpeakers = { ...prev };
      delete newSpeakers[speakerId];
      return newSpeakers;
    });

    if (speakerId === 'S2' && currentSpeaker === 'S2' && !currentForm.text && !currentForm.file && !currentForm.url) {
      setCurrentSpeaker('S1');
    }
  };

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

      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
      }

      abortControllerRef.current = new AbortController();
      const signal = abortControllerRef.current.signal;

      const protocol = typeof window !== 'undefined' ? window.location.protocol : 'http:';
      const host = api_host || window.location.host;
      const url = `${protocol}//${host}/v1/audio/speech`;

      let speakersParam = undefined;

      if (hasCustomizeSoundColorFeature && (speakers.S1 || speakers.S2)) {
        speakersParam = {} as any;

        if (speakers.S1) {
          speakersParam.S1 = {
            text: speakers.S1.text,
            dialect: speakers.S1.dialect || undefined
          };

          if (speakers.S1.file && speakers.S1.preview) {
            const base64Data = speakers.S1.preview.includes(',')
              ? speakers.S1.preview.split(',')[1]
              : speakers.S1.preview;
            speakersParam.S1.base64 = base64Data;
          } else if (speakers.S1.url) {
            speakersParam.S1.url = speakers.S1.url;
          }
        }

        if (speakers.S2) {
          speakersParam.S2 = {
            text: speakers.S2.text,
            dialect: speakers.S2.dialect || undefined
          };

          if (speakers.S2.file && speakers.S2.preview) {
            const base64Data = speakers.S2.preview.includes(',')
              ? speakers.S2.preview.split(',')[1]
              : speakers.S2.preview;
            speakersParam.S2.base64 = base64Data;
          } else if (speakers.S2.url) {
            speakersParam.S2.url = speakers.S2.url;
          }
        }
      }

      const requestBody = {
        user: userInfo?.userId,
        model: model,
        input: inputText,
        stream: true,
        voice: voiceTypes[voiceName],
        sample_rate: 24000,
        response_format: "pcm",
        ...(speakersParam && { speakers: speakersParam })
      };
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

      const reader = response.body?.getReader();
      if (!reader) {
        throw new Error('无法获取响应流');
      }

      const CHUNK_SIZE = 2048;
      let buffer = new Uint8Array(0);
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

      const checkPlaybackStatus = () => {
        if (pcmPlayerRef.current && pcmPlayerRef.current.isPlaybackEnded()) {
          stopPlayback();
        }
      };
      playbackCheckIntervalRef.current = setInterval(checkPlaybackStatus, 500);
      setIsProcessing(false);
    } catch (error: any) {
      if (error.name !== 'AbortError') {
        console.error('TTS error:', error);
        setErrorMessage(`语音合成失败: ${error.message}`);
      }
      setIsProcessing(false);
    }
  };

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

  const clearText = () => {
    setInputText('');
    stopPlayback();
  };

  return (
    <div className="container mx-auto px-4 py-3 max-w-5xl h-screen flex flex-col">
      <div className="flex flex-col md:flex-row md:items-center md:justify-between mb-3 flex-shrink-0">
        <h1 className="text-2xl font-bold">语音合成</h1>

        <div className="mt-2 md:mt-0 flex items-center space-x-2">
          {/* 自定义音色按钮 - 只在有该特性时显示 */}
          {hasCustomizeSoundColorFeature && (
            <button
              onClick={() => {
                setShowCustomizeSection(!showCustomizeSection);
                if (!showCustomizeSection) {
                  setAudioInputMode('file');
                }
              }}
              disabled={isPlaying}
              className={`px-3 py-1 rounded-md text-sm ${
                showCustomizeSection
                  ? "bg-blue-500 text-white"
                  : "bg-gray-100 hover:bg-gray-200 transition-colors"
              } ${isPlaying ? "opacity-50 cursor-not-allowed" : ""}`}
            >
              自定义音色
            </button>
          )}

          {Object.keys(voiceTypes).length > 0 && (
            <div className="flex items-center space-x-1">
              <span className="text-sm font-medium">声音:</span>
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

                  <div className="relative">
                    <button
                      disabled={isPlaying}
                      onClick={() => !isPlaying && toggleMoreVoices()}
                      className={`px-3 py-1 rounded-md text-sm bg-gray-100 hover:bg-gray-200 transition-colors ${isPlaying ? "opacity-50 cursor-not-allowed" : ""}`}
                    >
                      ...
                    </button>
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

      {inputStatePrompt && (
        <div className="mb-3 flex-shrink-0 text-sm text-gray-600">
          {inputStatePrompt}
        </div>
      )}

      <Card className="flex-grow overflow-hidden flex flex-col">
        <CardContent className="p-3 flex-grow overflow-hidden flex flex-col gap-2">

          {hasCustomizeSoundColorFeature && showCustomizeSection && (
            <div className="flex-[2] border-2 border-gray-300 rounded-lg p-3 overflow-auto">
              <div className="flex items-center justify-between mb-3">
                <h4 className="text-xs font-medium">自定义音色</h4>
                <div className="flex gap-2">
                  {speakers.S1 && (
                    <div className="flex items-center gap-1">
                      <span className="text-xs text-green-600">✓ 音色1完成</span>
                      <Button
                        size="sm"
                        variant="ghost"
                        onClick={() => editSpeaker('S1')}
                        className="h-6 px-2 text-xs"
                        disabled={isPlaying}
                      >
                        编辑
                      </Button>
                      <Button
                        size="sm"
                        variant="ghost"
                        onClick={() => deleteSpeaker('S1')}
                        className="h-6 px-2 text-xs text-red-500"
                        disabled={isPlaying}
                      >
                        <X className="h-3 w-3" />
                      </Button>
                    </div>
                  )}
                  {speakers.S2 && (
                    <div className="flex items-center gap-1">
                      <span className="text-xs text-green-600">✓ 音色2完成</span>
                      <Button
                        size="sm"
                        variant="ghost"
                        onClick={() => editSpeaker('S2')}
                        className="h-6 px-2 text-xs"
                        disabled={isPlaying}
                      >
                        编辑
                      </Button>
                      <Button
                        size="sm"
                        variant="ghost"
                        onClick={() => deleteSpeaker('S2')}
                        className="h-6 px-2 text-xs text-red-500"
                        disabled={isPlaying}
                      >
                        <X className="h-3 w-3" />
                      </Button>
                    </div>
                  )}
                </div>
              </div>

              <div className="flex gap-3">
                <div className="w-1/3 border rounded-lg p-2 bg-white">
                  <div className="flex items-center gap-2 mb-2">
                    <Button
                      variant={audioInputMode === 'file' ? "default" : "outline"}
                      size="sm"
                      onClick={() => {
                        setAudioInputMode('file');
                        setCurrentForm(prev => ({ ...prev, url: undefined, preview: undefined }));
                      }}
                      disabled={isPlaying}
                      className="h-7 px-3 text-xs"
                    >
                      音频文件
                    </Button>
                    <Button
                      variant={audioInputMode === 'url' ? "default" : "outline"}
                      size="sm"
                      onClick={() => {
                        setAudioInputMode('url');
                        setCurrentForm(prev => ({ ...prev, file: undefined, preview: undefined }));
                      }}
                      disabled={isPlaying}
                      className="h-7 px-3 text-xs"
                    >
                      URL
                    </Button>
                  </div>

                  <input
                    ref={audioInputRef}
                    type="file"
                    accept="audio/*"
                    onChange={handleAudioUpload}
                    className="hidden"
                  />

                  {audioInputMode === 'file' && (
                    <div className="border-2 border-dashed border-gray-300 rounded-lg py-1 px-2 cursor-pointer hover:border-gray-400 transition-colors"
                         onClick={() => audioInputRef.current?.click()}>
                      <div className="text-center">
                        <Upload className="h-3 w-3 mx-auto mb-0 text-gray-400" />
                        <p className="text-xs text-gray-600 leading-tight">上传音色文件</p>
                        {currentForm.file && (
                          <p className="text-xs text-green-600 mt-0 leading-tight">✓ {currentForm.file.name}</p>
                        )}
                      </div>
                    </div>
                  )}

                  {audioInputMode === 'url' && (
                    <div className="space-y-2">
                      <div className="flex gap-1">
                        <input
                          type="text"
                          placeholder="上传音色URL"
                          className="flex-1 px-2 py-1 border rounded text-sm"
                          value={currentForm.url || ''}
                          onChange={(e) => handleUrlInput(e.target.value)}
                          disabled={isPlaying}
                        />
                        {currentForm.url && !currentForm.preview && (
                          <Button
                            size="sm"
                            onClick={() => fetchAudioFromUrl(currentForm.url!)}
                            disabled={isPlaying || currentForm.isLoading}
                            className="px-2"
                          >
                            {currentForm.isLoading ? '...' : '加载'}
                          </Button>
                        )}
                      </div>
                      {currentForm.url && currentForm.preview && (
                        <div className="text-xs text-green-600 mt-1">
                          ✓ URL已设置
                        </div>
                      )}
                      {currentForm.isLoading && (
                        <div className="text-xs text-blue-600 mt-1">
                          加载中...
                        </div>
                      )}
                      {currentForm.error && (
                        <div className="text-xs text-red-600 mt-1">
                          {currentForm.error}
                        </div>
                      )}
                    </div>
                  )}
                </div>

                <div className="flex-1 space-y-2">
                  <div>
                    <label className="text-xs text-gray-600 mb-1 block">
                      音频提示词 *(必填)
                    </label>
                    <input
                      type="text"
                      value={currentForm.text}
                      onChange={(e) => updateText(e.target.value)}
                      placeholder="音色文件内容：大家好，欢迎来到openai语音合成"
                      className="text-xs h-7 w-full px-2 py-1 border rounded resize-none leading-tight"
                      disabled={isPlaying}
                    />
                  </div>

                  <div>
                    <div className="flex justify-between items-center">
                      <Button
                        variant={showDialectInput ? "default" : "outline"}
                        size="sm"
                        onClick={toggleDialectInput}
                        disabled={isPlaying}
                      >
                        {showDialectInput ? '方言提示词' : '添加方言提示词（可选）'}
                      </Button>
                      <Button
                        onClick={submitCurrentSpeaker}
                        disabled={isPlaying}
                        variant="default"
                        size="sm"
                      >
                      上传
                      </Button>
                    </div>

                    {showDialectInput && (
                      <input
                        type="text"
                        value={currentForm.dialect || ''}
                        onChange={(e) => updateDialect(e.target.value)}
                        placeholder="例：<|Sichuan|>四川话示例..."
                        className="text-xs h-7 w-full px-2 py-1 border rounded resize-none mt-2 leading-tight"
                        disabled={isPlaying}
                      />
                    )}
                  </div>
                </div>
              </div>
            </div>
          )}

          <div className={`relative ${hasCustomizeSoundColorFeature && showCustomizeSection ? 'flex-[3]' : 'flex-1'}`}>
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
