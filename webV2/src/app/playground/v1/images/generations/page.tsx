'use client';

import React, { useState, useEffect } from 'react';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { api_host } from '@/config';
import { useUser } from "@/lib/context/user-context";
import { Alert, AlertDescription } from '@/components/ui/alert';

interface ImageData {
  b64_json?: string;
  url?: string;
  revised_prompt?: string;
  output_format?: string;
  quality?: string;
  size?: string;
}

interface ImagesResponse {
  background?: string;
  created?: number;
  data: ImageData[];
}

export default function ImageGenerationsPlayground() {
  const [model, setModel] = useState('');
  const [prompt, setPrompt] = useState('一只可爱的小猫在草地上玩耍，阳光明媚');
  const [response, setResponse] = useState<ImagesResponse | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const { userInfo } = useUser();

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const modelParam = params.get('model');
    if (modelParam) {
      setModel(modelParam);
    }
  }, []);

  const generateImages = async () => {
    if (!prompt.trim()) return;
    
    setIsLoading(true);
    setError('');
    setResponse(null);

    try {
      const protocol = typeof window !== 'undefined' ? window.location.protocol : 'http:';
      const host = api_host || window.location.host;

      const response = await fetch(`${protocol}//${host}/v1/images/generations`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          prompt: prompt,
          model: model,
          user: userInfo?.userId
        }),
        credentials: 'include'
      });

      if (!response.ok) {
        throw new Error(`请求失败: ${response.status} ${await response.json().then(data => data.error?.message || '未知错误').catch(() => '未知错误')}`);
      }

      const data = await response.json();
      setResponse(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : '未知错误');
    } finally {
      setIsLoading(false);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
      e.preventDefault();
      generateImages();
    }
  };

  const renderImages = () => {
    if (!response || !response.data || response.data.length === 0) return null;

    return (
      <div className="space-y-4">
        <div className="text-sm font-medium mb-2">生成结果：</div>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {response.data.map((image, index) => (
            <div key={index} className="border rounded p-3 bg-white">
              {image.url && (
                <div className="mb-3">
                  <img 
                    src={image.url} 
                    alt={`Generated image ${index + 1}`}
                    className="w-full h-auto rounded"
                    loading="lazy"
                  />
                </div>
              )}
              {image.b64_json && (
                <div className="mb-3">
                  <img 
                    src={`data:image/png;base64,${image.b64_json}`}
                    alt={`Generated image ${index + 1}`}
                    className="w-full h-auto rounded"
                    loading="lazy"
                  />
                </div>
              )}
              
              {image.revised_prompt && (
                <div className="mt-2 text-xs text-gray-600">
                  <span className="font-medium">修正后的提示词:</span>
                  <div className="mt-1 p-2 bg-gray-50 rounded text-sm">
                    {image.revised_prompt}
                  </div>
                </div>
              )}
              
              <div className="mt-2 flex flex-wrap gap-2 text-xs text-gray-500">
                {image.size && <span className="bg-gray-100 px-2 py-1 rounded">尺寸: {image.size}</span>}
                {image.quality && <span className="bg-gray-100 px-2 py-1 rounded">质量: {image.quality}</span>}
                {image.output_format && <span className="bg-gray-100 px-2 py-1 rounded">格式: {image.output_format}</span>}
              </div>
            </div>
          ))}
        </div>
        
        {response.created && (
          <div className="text-xs text-gray-500 mt-4">
            生成时间: {new Date(response.created * 1000).toLocaleString()}
          </div>
        )}
      </div>
    );
  };

  return (
    <div className="container mx-auto px-4 py-3 max-w-5xl h-screen flex flex-col">
      <div className="flex flex-col md:flex-row md:items-center md:justify-between mb-4">
        <h1 className="text-2xl font-bold">图像生成</h1>

        <div className="mt-2 md:mt-0 flex items-center">
          <span className="text-sm font-medium mr-2">模型:</span>
          <div className="p-2 rounded">
            {model || 'dall-e-3'}
          </div>
        </div>
      </div>

      <div className="mb-3 flex-shrink-0">
        <div className="mt-2">
          <div className="flex flex-col">
            <Textarea
              value={prompt}
              onChange={(e) => setPrompt(e.target.value)}
              onKeyDown={handleKeyDown}
              className="min-h-[100px] bg-white border border-gray-300 focus:border-gray-400 focus:ring-gray-300"
              placeholder="输入描述文字，生成图像..."
              disabled={isLoading}
            />
            <div className="flex justify-between items-center mt-2">
              <div className="text-xs text-gray-500">
                <p>提示：描述您想要生成的图像内容，Ctrl+Enter 或 Cmd+Enter 快速生成</p>
              </div>
              <Button
                variant="outline"
                size="sm"
                onClick={generateImages}
                className="text-gray-600 hover:bg-gray-100"
                disabled={isLoading || !prompt.trim()}
              >
                {isLoading ? '生成中...' : '生成图像'}
              </Button>
            </div>
          </div>
        </div>
      </div>

      {error && (
        <Alert variant="destructive" className="mb-4">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      <Card className="shadow-sm flex-grow overflow-hidden flex flex-col">
        <CardContent className="p-3 flex-grow overflow-hidden">
          <div className="h-full overflow-y-auto p-3 bg-white rounded">
            {isLoading ? (
              <div className="flex items-center justify-center h-full text-gray-500">
                <div className="text-center">
                  <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-gray-900 mx-auto mb-2"></div>
                  <p>正在生成图像，请稍候...</p>
                </div>
              </div>
            ) : response ? (
              <div className="space-y-2">
                {renderImages()}
              </div>
            ) : (
              <div className="flex items-center justify-center h-full text-gray-500">
                <p>输入描述文字并点击"生成图像"开始</p>
              </div>
            )}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}