'use client';

import React, { useState, useEffect } from 'react';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { api_host } from '@/config';
import { useUser } from "@/lib/context/user-context";
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Download, Maximize2, Upload, X } from 'lucide-react';


export default function ImageGenerationsPlayground() {
  const [model, setModel] = useState('');
  const [prompt, setPrompt] = useState('一只可爱的小猫在草地上玩耍，阳光明媚');
  const [response, setResponse] = useState<Response | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const { userInfo } = useUser();
  const [sourceImage, setSourceImage] = useState(null);
  const [sourceImageBase64, setSourceImageBase64] = useState(null); 
  const [selectedImage, setSelectedImage] = useState(null);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const modelParam = params.get('model');
    if (modelParam) {
      setModel(modelParam);
    }
  }, []);
  
  const handleImageUpload = (e) => {
    const file = e.target.files[0];
    if (!file) return;
    
    if (!['image/jpeg', 'image/png'].includes(file.type)) {
      setError('只支持 JPEG 和 PNG 格式的图片');
      return;
    }
    
    if (file.size > 10 * 1024 * 1024) { // 10MB
      setError('图片大小不能超过 10MB');
      return;
    }
    
    const reader = new FileReader();
    reader.onload = (event) => {
      const img = new Image();
      img.onload = () => {
        const ratio = img.width / img.height;
        if (ratio <= 1/3 || ratio >= 3) {
          setError('图片宽高比必须在 1:3 到 3:1 之间');
          return;
        }
        
        if (img.width <= 14 || img.height <= 14) {
          setError('图片尺寸太小');
          return;
        }
        
        setSourceImage(event.target.result);
        setSourceImageBase64(event.target.result);

        setError('');
      };
      img.src = event.target.result;
    };
    reader.readAsDataURL(file);
  };
  
  const generateImages = async () => {
    if (!prompt.trim() || !sourceImageBase64) {
      setError('请上传图片并输入描述文字');
      return;
    }
    
    setIsLoading(true);
    setError(null);
    
    try {
      const protocol = typeof window !== 'undefined' ? window.location.protocol : 'http:';
      const host = api_host || window.location.host;
      const formData = new FormData();
      formData.append('prompt', prompt);
      formData.append('model', model);
      formData.append('user', userInfo.userId);
      formData.append('image_b64_json', sourceImageBase64);
      const result = await fetch(`${protocol}//${host}/v1/images/edits`, {
        method: 'POST',
        body: formData,
        credentials: 'include'
      });
      
      if (!result.ok) {
        throw new Error(`请求失败: ${result.status} ${await result.json().then(data => data.error?.message || '未知错误').catch(() => '未知错误')}`);
      }

      const data = await result.json();
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

  const downloadImage = (url, filename) => {
    
    if (url.startsWith('data:')) {
      const link = document.createElement('a');
      link.href = url;
      link.download = filename;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
    } else {
      fetch(url)
        .then(response => response.blob())
        .then(blob => {
          const blobUrl = URL.createObjectURL(blob);
          const link = document.createElement('a');
          link.href = blobUrl;
          link.download = filename;
          document.body.appendChild(link);
          link.click();
          document.body.removeChild(link);
          URL.revokeObjectURL(blobUrl);
        })
        .catch(error => {
          console.error('下载图片失败:', error);
          setError('下载图片失败');
        });
    }
  };

  return (
    <div className="container mx-auto px-4 py-3 max-w-5xl h-screen flex flex-col">
    <div className="flex flex-col md:flex-row md:items-center md:justify-between mb-4">
      <h1 className="text-2xl font-bold">图像编辑</h1>

      <div className="mt-2 md:mt-0 flex items-center">
        <span className="text-sm font-medium mr-2">模型:</span>
        <div className="p-2 rounded">
          {model || 'doubao-seededit-3.0-i2i'}
        </div>
      </div>
    </div>

    <div className="mb-4 h-[250px]">
      <div className="flex flex-col md:flex-row gap-4 h-full">
        <div className="w-full md:w-1/2 h-full">
          <div className="border-2 border-dashed border-gray-300 rounded-lg p-4 flex flex-col justify-center h-full">
            <div className="flex flex-col items-center justify-center relative h-full">
              {sourceImage ? (
                <div className="relative w-full h-full flex items-center justify-center">
                  <img 
                    src={sourceImage} 
                    alt="Source" 
                    className="max-h-full max-w-full object-contain" 
                  />
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => setSourceImage(null)}
                    className="absolute top-0 right-0 bg-white rounded-full p-1"
                  >
                    <span className="sr-only">Remove</span>
                    <X className="h-4 w-4" />
                  </Button>
                </div>
              ) : (
                <>
                  <div className="flex flex-col items-center justify-center">
                    <Upload className="h-10 w-10 text-gray-400 mb-2" />
                    <p className="text-sm text-gray-500 mb-1">点击上传需要编辑的图片</p>
                    <p className="text-xs text-gray-400">支持 JPEG、PNG 格式，大小不超过 10MB</p>
                  </div>
                  <input
                    type="file"
                    accept="image/jpeg,image/png"
                    onChange={handleImageUpload}
                    className="absolute inset-0 w-full h-full opacity-0 cursor-pointer"
                  />
                </>
              )}
            </div>
          </div>
        </div>

        <div className="w-full md:w-1/2 flex flex-col h-full">
          <div className="flex-grow h-full flex flex-col">
            <label htmlFor="prompt" className="block text-sm font-medium text-gray-700 mb-1">
              编辑提示词
            </label>
            <Textarea
              id="prompt"
              value={prompt}
              onChange={(e) => setPrompt(e.target.value)}
              onKeyDown={handleKeyDown}
              className="flex-grow bg-white border border-gray-300 focus:border-gray-400 focus:ring-gray-300 w-full resize-none"
              placeholder="输入描述文字，编辑上传的图像..."
              disabled={isLoading}
            />
          
            <div className="flex justify-between items-center mt-2">
              <div className="text-xs text-gray-500">
                <p>提示：描述您想要的编辑效果，Ctrl+Enter 或 Cmd+Enter 快速生成</p>
              </div>
              <Button
                variant="outline"
                size="sm"
                onClick={generateImages}
                className="text-gray-600 hover:bg-gray-100"
                disabled={isLoading || !prompt.trim() || !sourceImage}
              >
                {isLoading ? '生成中...' : '编辑图像'}
              </Button>
            </div>
          </div>
        </div>
      </div>
    </div>

    {error && (
      <Alert variant="destructive" className="mb-4">
        <AlertDescription>{error}</AlertDescription>
      </Alert>
    )}

    <div className="flex-grow overflow-hidden">
      <h2 className="text-lg font-medium mb-2">编辑结果</h2>
      <Card className="shadow-sm h-[calc(100%-6rem)] overflow-hidden">
        <CardContent className="p-3 h-full">
          <div className="bg-white rounded h-full overflow-y-auto">
            {isLoading ? (
              <div className="flex items-center justify-center h-full text-gray-500">
                <div className="text-center">
                  <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-gray-900 mx-auto mb-2"></div>
                  <p>正在编辑图像，请稍候...</p>
                </div>
              </div>
            ) : response ? (
              <div className="space-y-4 pb-4">
                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                  {response.data && response.data.map((image, index) => (
                    <div key={index} className="relative group border rounded-lg shadow-md overflow-hidden">
                      <div className="p-3">
                        {/* 图片显示 */}
                        <div className="overflow-hidden rounded-lg mb-3">
                          {image.url ? (
                            <img 
                              src={image.url} 
                              alt={`Generated image ${index + 1}`}
                              className="w-full h-auto object-contain cursor-pointer transition-transform duration-300 hover:scale-105"
                              onClick={() => setSelectedImage(image.url)}
                              loading="lazy"
                            />
                          ) : image.b64_json ? (
                            <img 
                              src={`data:image/png;base64,${image.b64_json}`}
                              alt={`Generated image ${index + 1}`}
                              className="w-full h-auto object-contain cursor-pointer transition-transform duration-300 hover:scale-105"
                              onClick={() => setSelectedImage(`data:image/png;base64,${image.b64_json}`)}
                              loading="lazy"
                            />
                          ) : null}
                        </div>
                        
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
                      
                      <div className="absolute top-2 right-2 flex space-x-2">
                        <Button
                          size="sm"
                          variant="secondary"
                          className="opacity-0 group-hover:opacity-100 transition-opacity bg-white/90"
                          onClick={() => setSelectedImage(image.url || `data:image/png;base64,${image.b64_json}`)}
                        >
                          <Maximize2 className="h-4 w-4" />
                        </Button>
                        <Button
                          size="sm"
                          variant="secondary"
                          className="opacity-0 group-hover:opacity-100 transition-opacity bg-white/90"
                          onClick={() => downloadImage(
                            image.url || `data:image/png;base64,${image.b64_json}`, 
                            `edited-image-${index}.png`
                          )}
                        >
                          <Download className="h-4 w-4" />
                        </Button>
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
            ) : (
              <div className="flex items-center justify-center h-full text-gray-500">
                <p>上传图片并输入描述文字，然后点击"编辑图像"开始</p>
              </div>
            )}
          </div>
        </CardContent>
      </Card>
    </div>

    {selectedImage && (
      <div className="fixed inset-0 bg-black/70 flex items-center justify-center z-50 p-4" onClick={() => setSelectedImage(null)}>
        <div className="relative max-w-4xl max-h-[90vh] w-full">
          <img 
            src={selectedImage} 
            alt="Enlarged view" 
            className="w-full h-auto object-contain"
            onClick={(e) => e.stopPropagation()}
          />
          <Button
            variant="outline"
            size="icon"
            className="absolute top-2 right-2 bg-white/90 rounded-full"
            onClick={() => setSelectedImage(null)}
          >
            <X className="h-4 w-4" />
          </Button>
          <Button
            variant="outline"
            size="sm"
            className="absolute bottom-2 right-2 bg-white/90"
            onClick={() => downloadImage(selectedImage, 'edited-image.png')}
          >
            <Download className="h-4 w-4 mr-1" />
            下载
          </Button>
        </div>
      </div>
    )}
  </div>
  );
}