'use client';

import React, { useState, useEffect, useRef } from 'react';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { api_host } from '@/config';
import { useUser } from "@/lib/context/user-context";
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Download, Maximize2, Upload, X, Link, FileText, Image as ImageIcon } from 'lucide-react';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';

export default function ImageGenerationsPlayground() {
  const [model, setModel] = useState('');
  const [prompt, setPrompt] = useState('一只可爱的小猫在草地上玩耍，阳光明媚');
  const [response, setResponse] = useState(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const { userInfo } = useUser();
  const [selectedTab, setSelectedTab] = useState('base64');
  
  const [singleImageFile, setSingleImageFile] = useState(null);
  const [imagePreview, setImagePreview] = useState(null);
  const [imageBase64, setImageBase64] = useState('');
  
  const [imageUrl, setImageUrl] = useState('');
  
  // 单文件上传，但保持列表显示
  const [uploadedFile, setUploadedFile] = useState(null);
  
  const [selectedImage, setSelectedImage] = useState(null);
  
  const singleFileInputRef = useRef(null);
  const fileUploadInputRef = useRef(null);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const modelParam = params.get('model');
    if (modelParam) {
      setModel(modelParam);
    }
  }, []);

  const handleSingleFileUpload = (e) => {
    const file = e.target.files[0];
    if (!file) return;
    
    setSingleImageFile(file);
    
    const reader = new FileReader();
    reader.onload = (event) => {
      const dataUrl = event.target.result;
      setImagePreview(dataUrl);
      setImageBase64(dataUrl);
    };
    reader.readAsDataURL(file);
  };
  
  const handleUrlChange = (e) => {
    setImageUrl(e.target.value);
  };
  
  // 单文件上传处理
  const handleFileUpload = (e) => {
    const file = e.target.files[0];
    if (!file) return;
    
    setUploadedFile(file);
    if (fileUploadInputRef.current) {
      fileUploadInputRef.current.value = '';
    }
  };
  
  // 移除上传的文件
  const removeFile = () => {
    setUploadedFile(null);
  };
  
  const clearSingleImage = () => {
    setSingleImageFile(null);
    setImagePreview(null);
    setImageBase64('');
    if (singleFileInputRef.current) {
      singleFileInputRef.current.value = '';
    }
  };

  const generateImages = async () => {
    if (!prompt.trim()) {
      setError('请输入描述文字');
      return;
    }
    
    if (selectedTab === 'base64' && !imageBase64) {
      setError('请上传图片');
      return;
    } else if (selectedTab === 'url' && !imageUrl) {
      setError('请输入图片URL');
      return;
    } else if (selectedTab === 'multiple' && !uploadedFile) {
      setError('请上传文件');
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
      
      if (selectedTab === 'base64' && imageBase64) {
        formData.append('image_b64_json', imageBase64);
      } else if (selectedTab === 'url' && imageUrl) {
        formData.append('image_url', imageUrl);
      } else if (selectedTab === 'multiple' && uploadedFile) {
        // 只传递当前文件
        formData.append('image', uploadedFile);
      }
      
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

  const handleKeyDown = (e) => {
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
  
  const hasValidInput = () => {
    if (!prompt.trim()) return false;
    
    switch (selectedTab) {
      case 'base64':
        return !!imageBase64;
      case 'url':
        return !!imageUrl;
      case 'multiple':
        return !!uploadedFile;
      default:
        return false;
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

      <div className="mb-4 h-[300px]">
        <div className="flex flex-col md:flex-row gap-4 h-full">
          <div className="w-full md:w-1/2 h-full">
            <div className="border-2 border-gray-300 rounded-lg p-4 flex flex-col h-full">
              <Tabs value={selectedTab} onValueChange={setSelectedTab} className="h-full flex flex-col">
                <TabsList className="grid grid-cols-3 mb-4">
                  <TabsTrigger value="base64" className="flex items-center gap-1">
                    <ImageIcon className="h-4 w-4" />
                    <span>单图上传</span>
                  </TabsTrigger>
                  <TabsTrigger value="url" className="flex items-center gap-1">
                    <Link className="h-4 w-4" />
                    <span>图片URL</span>
                  </TabsTrigger>
                  <TabsTrigger value="multiple" className="flex items-center gap-1">
                    <FileText className="h-4 w-4" />
                    <span>文件上传</span>
                  </TabsTrigger>
                </TabsList>
                
                <TabsContent value="base64" className="flex-grow flex flex-col">
                  {imagePreview ? (
                    <div className="w-full h-full flex flex-col">
                      <div className="flex-grow flex items-center justify-center bg-gray-50 rounded-lg overflow-hidden relative" style={{ minHeight: "180px" }}>
                        <div className="w-full h-full flex items-center justify-center p-2">
                          <img 
                            src={imagePreview} 
                            alt="Preview" 
                            className="object-contain max-h-[200px] max-w-full shadow-sm"
                          />
                        </div>
                        
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={clearSingleImage}
                          className="absolute top-2 right-2 bg-white/80 text-red-500 hover:text-red-600 hover:bg-white"
                        >
                          <X className="h-4 w-4" />
                        </Button>
                      </div>
                    </div>
                  ) : (
                    <div className="flex flex-col items-center justify-center h-full border-2 border-dashed border-gray-300 rounded-lg p-6">
                      <Upload className="h-10 w-10 text-gray-400 mb-2" />
                      <p className="text-sm text-gray-500 mb-4">上传单张图片，将转换为Base64格式</p>
                      <label className="cursor-pointer bg-gray-100 hover:bg-gray-200 text-gray-700 py-2 px-4 rounded-md text-sm transition-colors">
                        选择图片
                        <input
                          ref={singleFileInputRef}
                          type="file"
                          accept="image/*"
                          onChange={handleSingleFileUpload}
                          className="hidden"
                        />
                      </label>
                    </div>
                  )}
                </TabsContent>
                
                <TabsContent value="url" className="flex-grow">
                  <div className="flex flex-col h-full">
                    <label className="block text-sm font-medium text-gray-700 mb-2">
                      输入图片URL
                    </label>
                    <input
                      type="text"
                      placeholder="https://example.com/image.png"
                      className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-1 focus:ring-gray-400"
                      value={imageUrl}
                      onChange={handleUrlChange}
                    />
                    
                    <div className="mt-4 flex-grow flex flex-col">
                      {imageUrl && (
                        <div className="mt-2 p-3 bg-gray-50 rounded-md overflow-auto flex-grow">
                          <p className="text-sm font-medium mb-1">已输入URL:</p>
                          <p className="text-sm text-gray-600 break-all">{imageUrl}</p>
                        </div>
                      )}
                    </div>
                  </div>
                </TabsContent>
                
                {/* 文件上传 - 单文件上传但显示为列表 */}
                <TabsContent value="multiple" className="flex-grow flex flex-col h-full">
                  <div className="flex justify-between items-center mb-2">
                    <label className="block text-sm font-medium text-gray-700">
                      文件上传
                    </label>
                    <label className="cursor-pointer bg-gray-100 hover:bg-gray-200 text-gray-700 py-1 px-3 rounded-md text-xs transition-colors">
                      选择文件
                      <input
                        ref={fileUploadInputRef}
                        type="file"
                        onChange={handleFileUpload}
                        className="hidden"
                      />
                    </label>
                  </div>
                  
                  <div className="flex-grow border border-gray-200 rounded-md overflow-hidden">
                    {uploadedFile ? (
                      <div className="h-full flex flex-col">
                        {/* 文件列表 */}
                        <div className="flex-grow overflow-auto">
                          <ul className="divide-y divide-gray-200">
                            <li className="py-3 px-4 flex justify-between items-center hover:bg-gray-50">
                              <div className="flex items-center min-w-0 flex-grow">
                                <FileText className="h-5 w-5 text-gray-400 mr-3 flex-shrink-0" />
                                <div className="min-w-0 flex-grow">
                                  <p className="text-sm font-medium truncate" title={uploadedFile.name}>
                                    {uploadedFile.name}
                                  </p>
                                  <p className="text-xs text-gray-500">
                                    {(uploadedFile.size / 1024).toFixed(1)} KB
                                  </p>
                                </div>
                              </div>
                              <Button
                                variant="ghost"
                                size="sm"
                                onClick={removeFile}
                                className="text-red-500 hover:text-red-600 flex-shrink-0"
                              >
                                <X className="h-4 w-4" />
                              </Button>
                            </li>
                          </ul>
                        </div>
                        
                        {/* 底部信息 */}
                        <div className="bg-gray-50 p-3 border-t border-gray-200">
                          <div className="text-center">
                            <p className="text-xs text-gray-500">点击"编辑图像"按钮开始处理</p>
                          </div>
                        </div>
                      </div>
                    ) : (
                      <div className="flex flex-col items-center justify-center h-full p-6">
                        <FileText className="h-10 w-10 text-gray-400 mb-2" />
                        <p className="text-sm text-gray-500 mb-4">上传文件</p>
                        <label className="cursor-pointer bg-gray-100 hover:bg-gray-200 text-gray-700 py-2 px-4 rounded-md text-sm transition-colors">
                          选择文件
                          <input
                            type="file"
                            onChange={handleFileUpload}
                            className="hidden"
                          />
                        </label>
                      </div>
                    )}
                  </div>
                </TabsContent>
              </Tabs>
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
                  <div className="text-center">
                    <ImageIcon className="h-12 w-12 text-gray-300 mx-auto mb-3" />
                    <p>请提供图片并输入描述文字，然后点击"编辑图像"开始</p>
                    <p className="text-xs mt-2">支持单图上传、图片URL或文件上传</p>
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