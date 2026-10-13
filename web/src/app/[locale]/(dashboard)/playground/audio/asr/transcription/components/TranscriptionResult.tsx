'use client';

import { useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/common/card';
import { Button } from '@/components/common/button';
import { Copy, Check, FileText } from 'lucide-react';
import { TranscriptionTaskResult } from '@/lib/api/audioTranscription';

interface TranscriptionResultProps {
  taskId: string;
  result: TranscriptionTaskResult;
}

export function TranscriptionResult({ taskId, result }: TranscriptionResultProps) {
  const [copied, setCopied] = useState(false);

  const outputFileId = result.output_file_id;
  const text = typeof result.text === 'string' ? result.text : null;

  const handleCopy = async (content: string) => {
    await navigator.clipboard.writeText(content);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const jsonStr = JSON.stringify(result, null, 2);

  return (
    <div className="space-y-4">
      <div className="text-sm text-muted-foreground">
        Task ID: <code className="bg-muted px-1.5 py-0.5 rounded text-xs">{taskId}</code>
      </div>

      {outputFileId && (
        <Card>
          <CardHeader className="pb-3">
            <div className="flex items-center justify-between">
              <CardTitle className="text-base flex items-center gap-2">
                <FileText className="h-4 w-4" />
                Output File
              </CardTitle>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => handleCopy(outputFileId)}
              >
                {copied ? <Check className="h-4 w-4" /> : <Copy className="h-4 w-4" />}
              </Button>
            </div>
          </CardHeader>
          <CardContent>
            <p className="text-sm text-muted-foreground mb-1">结果过长，已存储为文件：</p>
            <code className="bg-muted px-2 py-1 rounded text-sm break-all">{outputFileId}</code>
          </CardContent>
        </Card>
      )}

      {!outputFileId && text && (
        <Card>
          <CardHeader className="pb-3">
            <div className="flex items-center justify-between">
              <CardTitle className="text-base">Transcription</CardTitle>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => handleCopy(text)}
              >
                {copied ? <Check className="h-4 w-4" /> : <Copy className="h-4 w-4" />}
              </Button>
            </div>
          </CardHeader>
          <CardContent>
            <p className="whitespace-pre-wrap text-sm leading-relaxed max-h-[400px] overflow-auto">{text}</p>
          </CardContent>
        </Card>
      )}

      {!outputFileId && (
        <Card>
          <CardHeader className="pb-3">
            <div className="flex items-center justify-between">
              <CardTitle className="text-base">Raw JSON</CardTitle>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => handleCopy(jsonStr)}
              >
                {copied ? <Check className="h-4 w-4" /> : <Copy className="h-4 w-4" />}
              </Button>
            </div>
          </CardHeader>
          <CardContent>
            <pre className="p-4 bg-muted rounded-lg text-xs overflow-auto max-h-[600px] whitespace-pre-wrap">
              {jsonStr}
            </pre>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
