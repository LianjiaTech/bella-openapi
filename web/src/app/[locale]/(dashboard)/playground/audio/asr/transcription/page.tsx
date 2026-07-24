'use client';

import { useState, useEffect, useRef } from 'react';
import { TopBar } from '@/components/layout/top-bar';
import { Button } from '@/components/common/button';
import { Input } from '@/components/common/input';
import { Card, CardContent } from '@/components/common/card';
import { useLanguage } from '@/components/providers/language-provider';
import { listModels } from '@/lib/api/metadata';
import { Upload, Link as LinkIcon, Loader2, RotateCcw } from 'lucide-react';
import { useFileTranscription, TranscriptionStatus } from './hooks/useFileTranscription';
import { TranscriptionResult } from './components/TranscriptionResult';

const ACCEPT_AUDIO = '.wav,.mp3';

const STATUS_LABELS: Record<TranscriptionStatus, { zh: string; en: string }> = {
  idle: { zh: '就绪', en: 'Ready' },
  uploading: { zh: '上传文件中...', en: 'Uploading file...' },
  submitting: { zh: '提交任务中...', en: 'Submitting task...' },
  polling: { zh: '等待转录结果...', en: 'Waiting for result...' },
  done: { zh: '转录完成', en: 'Transcription complete' },
  error: { zh: '出错', en: 'Error' },
};

export default function FileTranscriptionPage() {
  const { language } = useLanguage();
  const isZh = language === 'zh-CN';

  // Tab state
  const [tab, setTab] = useState<'file' | 'url'>('file');

  // File upload state
  const [file, setFile] = useState<File | null>(null);
  const [audioPreviewUrl, setAudioPreviewUrl] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  // URL input state
  const [audioUrl, setAudioUrl] = useState('');

  // Model selection
  const [models, setModels] = useState<{ modelName: string }[]>([]);
  const [selectedModel, setSelectedModel] = useState('');
  const [modelsLoading, setModelsLoading] = useState(true);

  // Language option
  const [audioLanguage, setAudioLanguage] = useState('');

  // Enable words
  const [enableWords, setEnableWords] = useState(false);

  // Speaker diarization
  const [speakerDiarization, setSpeakerDiarization] = useState(false);

  // Max end silence
  const [maxEndSilence, setMaxEndSilence] = useState(800);

  // Transcription hook
  const { status, taskId, result, error, submit, reset } = useFileTranscription();

  // Load models on mount
  useEffect(() => {
    let cancelled = false;
    setModelsLoading(true);
    listModels('/v1/audio/transcriptions')
      .then((data: any) => {
        if (cancelled) return;
        const list = Array.isArray(data) ? data : (Array.isArray(data?.data) ? data.data : []);
        setModels(list);
        if (list.length > 0 && !selectedModel) {
          setSelectedModel(list[0].modelName);
        }
      })
      .catch(console.error)
      .finally(() => { if (!cancelled) setModelsLoading(false); });
    return () => { cancelled = true; };
  }, []);

  // Clean up audio preview URL
  useEffect(() => {
    return () => {
      if (audioPreviewUrl) URL.revokeObjectURL(audioPreviewUrl);
    };
  }, [audioPreviewUrl]);

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const f = e.target.files?.[0];
    if (!f) return;
    setFile(f);
    if (audioPreviewUrl) URL.revokeObjectURL(audioPreviewUrl);
    setAudioPreviewUrl(URL.createObjectURL(f));
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    const f = e.dataTransfer.files?.[0];
    if (!f) return;
    setFile(f);
    if (audioPreviewUrl) URL.revokeObjectURL(audioPreviewUrl);
    setAudioPreviewUrl(URL.createObjectURL(f));
  };

  const handleSubmit = () => {
    if (!selectedModel) return;
    const options: any = { enable_words: enableWords, max_end_silence: maxEndSilence, speaker_diarization: speakerDiarization };
    if (audioLanguage) options.language = audioLanguage;
    if (tab === 'file' && file) {
      submit(file, selectedModel, options);
    } else if (tab === 'url' && audioUrl.trim()) {
      submit(audioUrl.trim(), selectedModel, options);
    }
  };

  const handleReset = () => {
    reset();
    setFile(null);
    setAudioUrl('');
    if (audioPreviewUrl) {
      URL.revokeObjectURL(audioPreviewUrl);
      setAudioPreviewUrl(null);
    }
  };

  const isProcessing = status === 'uploading' || status === 'submitting' || status === 'polling';
  const canSubmit = selectedModel && !isProcessing && (
    (tab === 'file' && file !== null) || (tab === 'url' && audioUrl.trim() !== '')
  );

  const statusLabel = STATUS_LABELS[status];

  return (
    <div className="flex flex-col h-full">
      <TopBar title={isZh ? '文件转录' : 'File Transcription'} />

      <div className="flex flex-1 overflow-hidden">
        {/* Main content */}
        <div className="flex-1 overflow-y-auto p-6 space-y-6">
          {/* Tab switcher */}
          <div className="flex gap-1 p-1 bg-muted rounded-lg w-fit">
            <button
              onClick={() => setTab('file')}
              className={`flex items-center gap-2 px-4 py-2 rounded-md text-sm font-medium transition-colors ${
                tab === 'file'
                  ? 'bg-background text-foreground shadow-sm'
                  : 'text-muted-foreground hover:text-foreground'
              }`}
            >
              <Upload className="h-4 w-4" />
              {isZh ? '上传文件' : 'Upload File'}
            </button>
            <button
              onClick={() => setTab('url')}
              className={`flex items-center gap-2 px-4 py-2 rounded-md text-sm font-medium transition-colors ${
                tab === 'url'
                  ? 'bg-background text-foreground shadow-sm'
                  : 'text-muted-foreground hover:text-foreground'
              }`}
            >
              <LinkIcon className="h-4 w-4" />
              {isZh ? '音频 URL' : 'Audio URL'}
            </button>
          </div>

          {/* File upload area */}
          {tab === 'file' && (
            <div>
              <div
                onDragOver={(e) => e.preventDefault()}
                onDrop={handleDrop}
                onClick={() => fileInputRef.current?.click()}
                className="border-2 border-dashed border-muted-foreground/25 rounded-lg p-8 text-center cursor-pointer hover:border-muted-foreground/50 transition-colors"
              >
                <Upload className="h-8 w-8 mx-auto mb-3 text-muted-foreground" />
                <p className="text-sm text-muted-foreground">
                  {isZh
                    ? '拖拽音频文件到此处，或点击选择文件'
                    : 'Drag & drop an audio file here, or click to select'}
                </p>
                <p className="text-xs text-muted-foreground/60 mt-1">
                  WAV, MP3
                </p>
                <input
                  ref={fileInputRef}
                  type="file"
                  accept={ACCEPT_AUDIO}
                  onChange={handleFileChange}
                  className="hidden"
                />
              </div>
              {file && (
                <div className="mt-3 space-y-2">
                  <p className="text-sm text-muted-foreground">
                    {isZh ? '已选择' : 'Selected'}: <span className="text-foreground">{file.name}</span>
                    <span className="ml-2 text-xs">({(file.size / 1024 / 1024).toFixed(2)} MB)</span>
                  </p>
                  {audioPreviewUrl && (
                    <audio controls src={audioPreviewUrl} className="w-full max-w-md" />
                  )}
                </div>
              )}
            </div>
          )}

          {/* URL input area */}
          {tab === 'url' && (
            <div>
              <label className="text-sm font-medium mb-1 block">{isZh ? '音频 URL' : 'Audio URL'}</label>
              <Input
                placeholder={isZh ? '输入音频文件的 URL 地址' : 'Enter audio file URL'}
                value={audioUrl}
                onChange={(e) => setAudioUrl(e.target.value)}
              />
            </div>
          )}

          {/* Action buttons */}
          <div className="flex items-center gap-3">
            <Button onClick={handleSubmit} disabled={!canSubmit}>
              {isProcessing && <Loader2 className="h-4 w-4 animate-spin" />}
              {isZh ? '开始转录' : 'Start Transcription'}
            </Button>
            {status !== 'idle' && (
              <Button variant="outline" onClick={handleReset}>
                <RotateCcw className="h-4 w-4" />
                {isZh ? '重置' : 'Reset'}
              </Button>
            )}
            {isProcessing && (
              <span className="text-sm text-muted-foreground">
                {isZh ? statusLabel.zh : statusLabel.en}
              </span>
            )}
          </div>

          {/* Error display */}
          {status === 'error' && error && (
            <div className="p-4 bg-destructive/10 text-destructive rounded-lg text-sm">
              {error}
            </div>
          )}

          {/* Result display */}
          {status === 'done' && taskId && result && (
            <TranscriptionResult taskId={taskId} result={result} />
          )}
        </div>

        {/* Right sidebar - config panel */}
        <div className="w-80 border-l border-sidebar-border p-6 space-y-6 overflow-y-auto">
          {/* Model selection */}
          <div>
            <label className="text-sm font-medium mb-1 block">{isZh ? '模型' : 'Model'}</label>
            {modelsLoading ? (
              <div className="flex items-center gap-2 text-sm text-muted-foreground mt-1">
                <Loader2 className="h-4 w-4 animate-spin" />
                {isZh ? '加载中...' : 'Loading...'}
              </div>
            ) : (
              <select
                value={selectedModel}
                onChange={(e) => setSelectedModel(e.target.value)}
                className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
              >
                {models.map((m) => (
                  <option key={m.modelName} value={m.modelName}>
                    {m.modelName}
                  </option>
                ))}
                {models.length === 0 && (
                  <option value="" disabled>
                    {isZh ? '无可用模型' : 'No models available'}
                  </option>
                )}
              </select>
            )}
          </div>

          {/* Language */}
          <div>
            <label className="text-sm font-medium mb-1 block">{isZh ? '语言 (可选)' : 'Language (optional)'}</label>
            <Input
              placeholder="e.g. zh, en, ja"
              value={audioLanguage}
              onChange={(e) => setAudioLanguage(e.target.value)}
            />
          </div>

          {/* Max End Silence */}
          <div>
            <div className="flex items-center justify-between mb-1">
              <label className="text-sm font-medium">max_end_silence</label>
              <span className="text-sm text-muted-foreground">{maxEndSilence}</span>
            </div>
            <input
              type="range"
              min={0}
              max={1000}
              step={10}
              value={maxEndSilence}
              onChange={(e) => setMaxEndSilence(Number(e.target.value))}
              className="w-full accent-primary"
            />
            <div className="flex justify-between text-xs text-muted-foreground mt-0.5">
              <span>0</span>
              <span>1000</span>
            </div>
          </div>

          {/* Speaker Diarization */}
          <div className="flex items-center justify-between">
            <label className="text-sm font-medium">{isZh ? '说话人日志' : 'Speaker Diarization'}</label>
            <button
              type="button"
              role="switch"
              aria-checked={speakerDiarization}
              onClick={() => setSpeakerDiarization(!speakerDiarization)}
              className={`relative inline-flex h-5 w-9 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors ${
                speakerDiarization ? 'bg-primary' : 'bg-muted'
              }`}
            >
              <span
                className={`pointer-events-none inline-block h-4 w-4 rounded-full bg-background shadow-lg ring-0 transition-transform ${
                  speakerDiarization ? 'translate-x-4' : 'translate-x-0'
                }`}
              />
            </button>
          </div>

          {/* Enable Words */}
          <div className="flex items-center justify-between">
            <label className="text-sm font-medium">{isZh ? '返回分词信息' : 'Enable Words'}</label>
            <button
              type="button"
              role="switch"
              aria-checked={enableWords}
              onClick={() => setEnableWords(!enableWords)}
              className={`relative inline-flex h-5 w-9 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors ${
                enableWords ? 'bg-primary' : 'bg-muted'
              }`}
            >
              <span
                className={`pointer-events-none inline-block h-4 w-4 rounded-full bg-background shadow-lg ring-0 transition-transform ${
                  enableWords ? 'translate-x-4' : 'translate-x-0'
                }`}
              />
            </button>
          </div>

          {/* Status indicator */}
          {status !== 'idle' && (
            <Card>
              <CardContent className="pt-4">
                <div className="text-sm space-y-2">
                  <div className="flex justify-between">
                    <span className="text-muted-foreground">{isZh ? '状态' : 'Status'}</span>
                    <span className={status === 'error' ? 'text-destructive' : status === 'done' ? 'text-green-600' : ''}>
                      {isZh ? statusLabel.zh : statusLabel.en}
                    </span>
                  </div>
                  {taskId && (
                    <div>
                      <span className="text-muted-foreground">Task ID</span>
                      <code className="text-xs bg-muted px-1 py-0.5 rounded block mt-1 break-all">{taskId}</code>
                    </div>
                  )}
                </div>
              </CardContent>
            </Card>
          )}
        </div>
      </div>
    </div>
  );
}
