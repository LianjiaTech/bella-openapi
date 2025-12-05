'use client';

import React, { useState, useEffect, useRef } from 'react';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { api_host } from '@/config';
import { useUser } from "@/lib/context/user-context";
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Maximize2, Upload, X, Link, Image as ImageIcon, Trash2 } from 'lucide-react';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';

interface ImageItem {
  id: string;
  file?: File;
  url?: string;
  preview: string;
  isLoading?: boolean;
  error?: string;
}

export default function ImageGenerationsPlayground() {
  const [model, setModel] = useState('');
  const [prompt, setPrompt] = useState('一只可爱的小猫在草地上玩耍，阳光明媚');
  const [response, setResponse] = useState<any>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const { userInfo } = useUser();
  const [selectedTab, setSelectedTab] = useState('upload');
  
  const [images, setImages] = useState<ImageItem[]>([]);
  const [urlInput, setUrlInput] = useState('');
  const [isUrlLoading, setIsUrlLoading] = useState(false);
  
  const [selectedImage, setSelectedImage] = useState<string | null>(null);
  
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const modelParam = params.get('model');
    if (modelParam) {
      setModel(modelParam);
    }
  }, []);

  const generateId = () => Math.random().toString(36).substr(2, 9);

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(e.target.files || []);
    if (files.length === 0) return;
    
    files.forEach(file => {
      const id = generateId();
      const reader = new FileReader();
      reader.onload = (event) => {
        const dataUrl = event.target?.result as string;
        setImages(prev => [...prev, {
          id,
          file,
          preview: dataUrl
        }]);
      };
      reader.readAsDataURL(file);
    });
    
    // 清空文件输入
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  };
  
  const removeImage = (id: string) => {
    setImages(prev => prev.filter(img => img.id !== id));
  };
  
  const clearAllImages = () => {
    setImages([]);
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  };
  
  const fetchImageFromUrl = async () => {
    if (!urlInput.trim()) {
      setError('请输入有效的图片URL');
      return;
    }
    
    setIsUrlLoading(true);
    setError('');
    
    const id = generateId();
    
    // 添加加载状态的图片项
    setImages(prev => [...prev, {
      id,
      url: urlInput,
      preview: '',
      isLoading: true
    }]);

    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 30000);
    
    try {
      const response = await fetch('/api/fetch-image', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ url: urlInput }),
        signal: controller.signal
      });
      
      clearTimeout(timeoutId);
      if (!response.ok) {
        const errorData = await response.json();
        throw new Error(errorData.error || `获取图像失败: ${response.status}`);
      }
      
      const data = await response.json();
      
      if (data.success) {
        const base64Response = await fetch(data.imageData);
        const blob = await base64Response.blob();
        
        const fileName = urlInput.split('/').pop() || 'image.png';
        const file = new File([blob], fileName, { type: blob.type });
        
        // 更新图片项
        setImages(prev => prev.map(img => 
          img.id === id 
            ? { ...img, file, preview: data.imageData, isLoading: false }
            : img
        ));
        
        // 清空URL输入
        setUrlInput('');
      } else {
        throw new Error(data.error || '获取图像失败');
      }
    } catch (err) {
      clearTimeout(timeoutId);
      const errorMessage = err instanceof DOMException && err.name === 'AbortError'
        ? '⚠️ 请求超时：无法获取图片。可能是服务器响应慢、URL无效或网络问题，请稍后重试或检查URL是否正确。'
        : err instanceof Error ? err.message : '获取图像失败';
      
      // 更新图片项的错误状态
      setImages(prev => prev.map(img => 
        img.id === id 
          ? { ...img, isLoading: false, error: errorMessage }
          : img
      ));
    } finally {
      setIsUrlLoading(false);
    }
  };

  const generateImages = async () => {
    if (!prompt.trim()) {
      setError('请输入描述文字');
      return;
    }
    
    if (images.length === 0) {
      setError('请上传图片或提供图片URL');
      return;
    }
    
    const hasLoadingImages = images.some(img => img.isLoading);
    if (hasLoadingImages) {
      setError('请等待所有图片加载完成');
      return;
    }
    
    // 检查是否有错误的图片
    const hasErrorImages = images.some(img => img.error);
    if (hasErrorImages) {
      setError('请移除加载失败的图片');
      return;
    }
    
    setIsLoading(true);
    setError('');
    
    try {
      const protocol = typeof window !== 'undefined' ? window.location.protocol : 'http:';
      const host = api_host || window.location.host;
      const formData = new FormData();
      
      formData.append('prompt', prompt);
      if (model) formData.append('model', model);
      if (userInfo?.userId) formData.append('user', userInfo.userId);
      
      const imageFiles = images.filter(img => img.file);
      imageFiles.forEach((image) => {
        formData.append('image', image.file!);
      });
      
      const imageUrls = images.filter(img => img.url && !img.file).map(img => img.url!);
      imageUrls.forEach((url) => {
        formData.append('image_url', url);
      });
      
      const result = await fetch(`${protocol}//${host}/v1/images/edits`, {
        method: 'POST',
        body: formData,
        credentials: 'include'
      });
      
      if (!result.ok) {
        const errorData = await result.json().catch(() => ({}));
        throw new Error(`请求失败: ${result.status} ${errorData.error?.message || '未知错误'}`);
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

  const handleUrlKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      fetchImageFromUrl();
    }
  };
  
  const hasValidInput = () => {
    return prompt.trim() && images.length > 0 && !images.some(img => img.isLoading || img.error);
  };

  return (
    <div className="container mx-auto px-4 py-3 max-w-6xl h-screen flex flex-col">
      <div className="flex flex-col md:flex-row md:items-center md:justify-between mb-4">
        <h1 className="text-2xl font-bold">图像编辑</h1>

        <div className="mt-2 md:mt-0 flex items-center">
          <span className="text-sm font-medium mr-2">模型:</span>
          <div className="p-2 rounded">
            {model || 'doubao-seededit-3.0-i2i'}
          </div>
        </div>
      </div>

      <div className="mb-4 h-[350px]">
        <div className="flex flex-col lg:flex-row gap-4 h-full">
          <div className="w-full lg:w-2/3 h-full">
            <div className="border-2 border-gray-300 rounded-lg p-4 flex flex-col h-full">
              <Tabs value={selectedTab} onValueChange={setSelectedTab} className="h-full flex flex-col">
                <div className="flex justify-between items-center mb-4">
                  <TabsList className="grid grid-cols-2">
                    <TabsTrigger value="upload" className="flex items-center gap-1">
                      <ImageIcon className="h-4 w-4" />
                      <span>上传图片</span>
                    </TabsTrigger>
                    <TabsTrigger value="url" className="flex items-center gap-1">
                      <Link className="h-4 w-4" />
                      <span>图片URL</span>
                    </TabsTrigger>
                  </TabsList>
                  
                  {images.length > 0 && (
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={clearAllImages}
                      className="text-red-500 hover:text-red-600"
                    >
                      <Trash2 className="h-4 w-4 mr-1" />
                      清空所有
                    </Button>
                  )}
                </div>
                
                <div className="flex-grow flex flex-col overflow-hidden">
                  <TabsContent value="upload" className="flex-grow flex flex-col overflow-hidden">
                    <div className="flex flex-col h-full">
                      <div className="flex items-center justify-center border-2 border-dashed border-gray-300 rounded-lg p-4 mb-4">
                        <div className="text-center">
                          <Upload className="h-8 w-8 text-gray-400 mx-auto mb-2" />
                          <p className="text-sm text-gray-500 mb-2">上传多张图片</p>
                          <label className="cursor-pointer bg-gray-100 hover:bg-gray-200 text-gray-700 py-2 px-4 rounded-md text-sm transition-colors">
                            选择图片
                            <input
                              ref={fileInputRef}
                              type="file"
                              accept="image/*"
                              multiple
                              onChange={handleFileUpload}
                              className="hidden"
                            />
                          </label>
                        </div>
                      </div>
                      
                      {images.length > 0 && (
                        <div className="flex-grow overflow-y-auto">
                          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-3">
                            {images.map((image) => (
                              <div key={image.id} className="relative group border rounded-lg overflow-hidden bg-white">
                                {image.isLoading ? (
                                  <div className="w-full h-24 flex items-center justify-center bg-gray-100">
                                    <div className="animate-spin rounded-full h-6 w-6 border-b-2 border-gray-900"></div>
                                  </div>
                                ) : image.error ? (
                                  <div className="w-full h-24 flex items-center justify-center bg-red-50 p-2">
                                    <p className="text-xs text-red-500 text-center break-words">{image.error}</p>
                                  </div>
                                ) : (
                                  <div className="w-full h-24 overflow-hidden">
                                    <img 
                                      src={image.preview} 
                                      alt="Preview" 
                                      className="w-full h-full object-cover cursor-pointer hover:scale-110 transition-transform duration-200"
                                      onClick={() => setSelectedImage(image.preview)}
                                    />
                                  </div>
                                )}
                                
                                <Button
                                  variant="outline"
                                  size="sm"
                                  onClick={() => removeImage(image.id)}
                                  className="absolute top-1 right-1 bg-white/90 text-red-500 hover:text-red-600 hover:bg-white p-1 h-6 w-6 opacity-0 group-hover:opacity-100 transition-opacity"
                                >
                                  <X className="h-3 w-3" />
                                </Button>
                              </div>
                            ))}
                          </div>
                        </div>
                      )}
                    </div>
                  </TabsContent>
                  
                  <TabsContent value="url" className="flex-grow flex flex-col overflow-hidden">
                    <div className="flex flex-col h-full">
                      {/* URL输入区域 */}
                      <div className="mb-4">
                        <label className="block text-sm font-medium text-gray-700 mb-2">
                          输入图片URL
                        </label>
                        <div className="flex gap-2">
                          <input
                            type="text"
                            placeholder="https://example.com/image.png"
                            className="flex-1 px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 text-sm"
                            value={urlInput}
                            onChange={(e) => setUrlInput(e.target.value)}
                            onKeyDown={handleUrlKeyDown}
                            disabled={isUrlLoading}
                          />
                          <Button 
                            onClick={fetchImageFromUrl}
                            disabled={isUrlLoading || !urlInput.trim()}
                            className="whitespace-nowrap px-4"
                          >
                            {isUrlLoading ? (
                              <>
                                <div className="animate-spin mr-2 h-4 w-4 border-2 border-b-transparent border-white rounded-full"></div>
                                加载中
                              </>
                            ) : '加载图片'}
                          </Button>
                        </div>
                        <p className="text-xs text-gray-500 mt-1">
                          输入图片URL后按回车键或点击"加载图片"按钮
                        </p>
                      </div>
                      
                      {/* 已加载图片展示区域 */}
                      {images.length > 0 ? (
                        <div className="flex-grow overflow-hidden">
                          <div className="flex items-center justify-between mb-3">
                            <h3 className="text-sm font-medium text-gray-700">
                              已加载的图片 ({images.length})
                            </h3>
                          </div>
                          <div className="h-full overflow-y-auto">
                            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-3">
                              {images.map((image) => (
                                <div key={image.id} className="relative group border rounded-lg overflow-hidden bg-white">
                                  {image.isLoading ? (
                                    <div className="w-full h-24 flex items-center justify-center bg-gray-100">
                                      <div className="animate-spin rounded-full h-6 w-6 border-b-2 border-gray-900"></div>
                                    </div>
                                  ) : image.error ? (
                                    <div className="w-full h-24 flex items-center justify-center bg-red-50 p-2">
                                      <p className="text-xs text-red-500 text-center break-words">{image.error}</p>
                                    </div>
                                  ) : (
                                    <div className="w-full h-24 overflow-hidden">
                                      <img 
                                        src={image.preview} 
                                        alt="Preview" 
                                        className="w-full h-full object-cover cursor-pointer hover:scale-110 transition-transform duration-200"
                                        onClick={() => setSelectedImage(image.preview)}
                                      />
                                    </div>
                                  )}
                                  
                                  <Button
                                    variant="outline"
                                    size="sm"
                                    onClick={() => removeImage(image.id)}
                                    className="absolute top-1 right-1 bg-white/90 text-red-500 hover:text-red-600 hover:bg-white p-1 h-6 w-6 opacity-0 group-hover:opacity-100 transition-opacity"
                                  >
                                    <X className="h-3 w-3" />
                                  </Button>
                                </div>
                              ))}
                            </div>
                          </div>
                        </div>
                      ) : (
                        <div className="flex-grow flex items-center justify-center">
                          <div className="text-center text-gray-500">
                            <ImageIcon className="h-12 w-12 text-gray-300 mx-auto mb-3" />
                            <p className="text-sm">暂无图片</p>
                            <p className="text-xs mt-1">请在上方输入图片URL并加载</p>
                          </div>
                        </div>
                      )}
                    </div>
                  </TabsContent>
                </div>
              </Tabs>
            </div>
          </div>

          <div className="w-full lg:w-1/3 flex flex-col h-full">
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
                  <p>已选择 {images.length} 张图片</p>
                  <p>Ctrl+Enter 快速生成</p>
                </div>
                <Button
                  variant={hasValidInput() ? "default" : "outline"}
                  size="sm"
                  onClick={generateImages}
                  className={hasValidInput() ? "bg-blue-600 text-white hover:bg-blue-700" : "text-gray-600 opacity-50 cursor-not-allowed"}
                  disabled={isLoading || !hasValidInput()}
                >
                  {isLoading ? (
                    <>
                      <div className="animate-spin mr-2 h-4 w-4 border-2 border-b-transparent border-white rounded-full"></div>
                      生成中...
                    </>
                  ) : '编辑图像'}
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
                  <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                    {response.data && response.data.map((image: any, index: number) => (
                      <div key={index} className="relative group border rounded-lg shadow-md overflow-hidden">
                        <div className="p-3">
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
                  <div className="text-center">
                    <ImageIcon className="h-12 w-12 text-gray-300 mx-auto mb-3" />
                    <p>请提供图片并输入描述文字，然后点击"编辑图像"开始</p>
                    <p className="text-xs mt-2">支持上传多张图片或输入图片URL</p>
                  </div>
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
          </div>
        </div>
      )}
    </div>
  );
}