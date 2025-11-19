'use client';

import React, {useState, useEffect} from 'react';
import {Card, CardContent} from '@/components/ui/card';
import {Button} from '@/components/ui/button';
import {Textarea} from '@/components/ui/textarea';
import {Input} from '@/components/ui/input';
import {Tabs, TabsContent, TabsList, TabsTrigger} from '@/components/ui/tabs';
import {Label} from '@/components/ui/label';
import {api_host} from '@/config';
import {useUser} from "@/lib/context/user-context";
import {Alert, AlertDescription} from '@/components/ui/alert';
import {Upload, Link as LinkIcon, FileText, Code} from 'lucide-react';

interface OCRResponse {
    [key: string]: any;
}

interface OCRPlaygroundProps {
    title: string;
    apiEndpoint: string;
    description?: string;
}

export default function OCRPlayground({title, apiEndpoint, description}: OCRPlaygroundProps) {
    const [model, setModel] = useState('');
    const [inputMethod, setInputMethod] = useState<'upload' | 'url' | 'fileId' | 'base64'>('upload');

    // 四种输入方式的状态
    const [imageFile, setImageFile] = useState<File | null>(null);
    const [uploadImageBase64, setUploadImageBase64] = useState(''); // 上传图片的base64
    const [imageBase64, setImageBase64] = useState(''); // base64输入框的数据
    const [imageUrl, setImageUrl] = useState('');
    const [fileId, setFileId] = useState('');

    // 为每种输入方式维护独立的预览
    const [uploadPreviewUrl, setUploadPreviewUrl] = useState('');
    const [base64PreviewUrl, setBase64PreviewUrl] = useState('');
    const [response, setResponse] = useState<OCRResponse | null>(null);
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState('');
    const {userInfo} = useUser();

    useEffect(() => {
        const params = new URLSearchParams(window.location.search);
        const modelParam = params.get('model');
        if (modelParam) {
            setModel(modelParam);
        }
    }, []);

    // 处理文件上传
    const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        if (!file) return;

        // 验证文件类型
        if (!file.type.startsWith('image/')) {
            setError('请上传图片文件');
            return;
        }

        // 验证文件大小 (例如限制10MB)
        if (file.size > 10 * 1024 * 1024) {
            setError('图片大小不能超过10MB');
            return;
        }

        setImageFile(file);
        setError('');

        // 生成预览URL
        const reader = new FileReader();
        reader.onloadend = () => {
            const base64String = reader.result as string;
            setUploadPreviewUrl(base64String);
            // 保留完整的 base64 字符串（包括前缀），后端会自动处理
            setUploadImageBase64(base64String);
        };
        reader.readAsDataURL(file);
    };

    // 拖拽上传
    const handleDrop = (e: React.DragEvent) => {
        e.preventDefault();
        const file = e.dataTransfer.files[0];
        if (file && file.type.startsWith('image/')) {
            const syntheticEvent = {
                target: {files: [file]}
            } as any;
            handleFileChange(syntheticEvent);
        }
    };

    const handleDragOver = (e: React.DragEvent) => {
        e.preventDefault();
    };

    // 生成 Base64 预览
    const generateBase64Preview = (base64String: string) => {
        if (!base64String) {
            setBase64PreviewUrl('');
            return;
        }
        try {
            // 如果包含 data URL 前缀，直接使用
            if (base64String.startsWith('data:image')) {
                setBase64PreviewUrl(base64String);
            } else {
                // 否则添加前缀用于预览
                setBase64PreviewUrl(`data:image/png;base64,${base64String}`);
            }
        } catch (err) {
            console.error('Base64 预览失败', err);
        }
    };

    // 调用OCR API
    const recognizeImage = async () => {
        if (!model) {
            setError('请选择模型');
            return;
        }

        // 验证输入
        if (inputMethod === 'upload' && !uploadImageBase64) {
            setError('请上传图片');
            return;
        }
        if (inputMethod === 'url' && !imageUrl) {
            setError('请输入图片URL');
            return;
        }
        if (inputMethod === 'fileId' && !fileId) {
            setError('请输入文件ID');
            return;
        }
        if (inputMethod === 'base64' && !imageBase64) {
            setError('请粘贴 Base64 字符串');
            return;
        }

        setIsLoading(true);
        setError('');
        setResponse(null);

        try {
            const protocol = typeof window !== 'undefined' ? window.location.protocol : 'http:';
            const host = api_host || window.location.host;

            // 构建请求体
            const requestBody: any = {
                model: model,
                user: userInfo?.userId
            };

            // 根据输入方式添加对应字段
            if (inputMethod === 'upload') {
                requestBody.image_base64 = uploadImageBase64;
            } else if (inputMethod === 'base64') {
                requestBody.image_base64 = imageBase64;
            } else if (inputMethod === 'url') {
                requestBody.image_url = imageUrl;
            } else if (inputMethod === 'fileId') {
                requestBody.file_id = fileId;
            }

            const response = await fetch(`${protocol}//${host}${apiEndpoint}`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(requestBody),
                credentials: 'include'
            });

            if (!response.ok) {
                const errorData = await response.json().catch(() => ({}));
                throw new Error(`请求失败: ${response.status} ${errorData.error?.message || errorData.message || '未知错误'}`);
            }

            const data = await response.json();
            setResponse(data);
        } catch (err) {
            setError(err instanceof Error ? err.message : '未知错误');
        } finally {
            setIsLoading(false);
        }
    };

    // 清除结果
    const clearResults = () => {
        setResponse(null);
        setError('');
        setUploadPreviewUrl('');
        setBase64PreviewUrl('');
        setImageFile(null);
        setUploadImageBase64('');
        setImageBase64('');
        setImageUrl('');
        setFileId('');
    };

    // 渲染OCR结果
    const renderOCRResult = () => {
        if (!response) return null;

        // 智能渲染不同类型的值
        const renderValue = (value: any) => {
            // 处理null或undefined
            if (value === null || value === undefined) {
                return <span className="text-gray-400 italic">无</span>;
            }

            // 处理数组
            if (Array.isArray(value)) {
                if (value.length === 0) {
                    return <span className="text-gray-400 italic">空数组</span>;
                }
                // 如果数组只有一个元素，直接显示
                if (value.length === 1) {
                    return <span>{String(value[0])}</span>;
                }
                // 多个元素时，使用列表展示
                return (
                    <div className="space-y-1">
                        {value.map((item, index) => (
                            <div key={index} className="flex items-start">
                                <span>{String(item)}</span>
                            </div>
                        ))}
                    </div>
                );
            }

            // 处理字符串和其他基本类型
            return <span>{String(value)}</span>;
        };

        return (
            <div className="space-y-4">
                <div className="text-sm font-medium mb-2">识别结果：</div>

                {/* 如果有结构化数据，优先展示 */}
                {response.data && (
                    <div className="border rounded-lg p-3 bg-gray-50">
                        <div className="space-y-2">
                            {Object.entries(response.data).map(([key, value]) => (
                                <div key={key} className="flex">
                                    <span className="font-medium text-gray-700 w-32 flex-shrink-0">{key}:</span>
                                    <div className="text-gray-900 flex-1">{renderValue(value)}</div>
                                </div>
                            ))}
                        </div>
                    </div>
                )}

                {/* 原始JSON数据 */}
                <div className="mt-4">
                    <div className="text-sm font-medium mb-2">原始响应：</div>
                    <pre className="bg-gray-100 text-gray-900 p-3 rounded-lg overflow-auto text-xs max-h-96">
{JSON.stringify(response, null, 2)}
          </pre>
                </div>
            </div>
        );
    };

    return (
        <div className="w-full h-screen flex flex-col overflow-hidden">
            <div className="flex-shrink-0 container mx-auto max-w-6xl px-4 pt-3 pb-2">
                <div className="flex flex-col md:flex-row md:items-center md:justify-between mb-2">
                    <h1 className="text-2xl font-bold">{title}</h1>
                    <div className="mt-2 md:mt-0 flex items-center">
                        <span className="text-sm font-medium mr-2">模型:</span>
                        <div className="p-2 rounded">
                            {model || '未选择'}
                        </div>
                    </div>
                </div>
            </div>

            <div
                className="flex-1 flex flex-col lg:flex-row gap-4 overflow-hidden min-h-0 container mx-auto max-w-6xl px-4 pb-4">
                {/* 左侧：图片上传区域 */}
                <Card className="w-full lg:w-1/2 shadow-sm flex flex-col overflow-hidden">
                    <CardContent className="p-3 flex flex-col h-full overflow-hidden">
                        {/* Tab选择器 - 固定在顶部 */}
                        <Tabs value={inputMethod} onValueChange={(value) => setInputMethod(value as any)}
                              className="flex-1 flex flex-col overflow-hidden min-h-0">
                            <TabsList className="grid w-full grid-cols-4 mb-3 flex-shrink-0">
                                <TabsTrigger value="upload" className="text-xs">
                                    <Upload className="w-4 h-4 mr-1"/>
                                    上传图片
                                </TabsTrigger>
                                <TabsTrigger value="url" className="text-xs">
                                    <LinkIcon className="w-4 h-4 mr-1"/>
                                    图片URL
                                </TabsTrigger>
                                <TabsTrigger value="fileId" className="text-xs">
                                    <FileText className="w-4 h-4 mr-1"/>
                                    文件ID
                                </TabsTrigger>
                                <TabsTrigger value="base64" className="text-xs">
                                    <Code className="w-4 h-4 mr-1"/>
                                    Base64
                                </TabsTrigger>
                            </TabsList>

                            {/* 上传图片 */}
                            <TabsContent value="upload"
                                         className="flex-1 overflow-hidden min-h-0 mt-0 data-[state=active]:flex data-[state=active]:flex-col">
                                <div
                                    className="border-2 border-dashed border-gray-300 rounded-lg text-center hover:border-gray-400 transition-colors cursor-pointer h-full flex flex-col items-center justify-center overflow-hidden"
                                    onDrop={handleDrop}
                                    onDragOver={handleDragOver}
                                    onClick={() => document.getElementById('fileInput')?.click()}
                                >
                                    {uploadPreviewUrl ? (
                                        <div className="w-full h-full flex items-center justify-center p-4">
                                            <img
                                                src={uploadPreviewUrl}
                                                alt="Preview"
                                                className="max-w-full max-h-full object-contain rounded"
                                            />
                                        </div>
                                    ) : (
                                        <div className="flex flex-col items-center p-8">
                                            <Upload className="w-12 h-12 text-gray-400 mb-4"/>
                                            <p className="text-gray-600 mb-2">点击上传或拖拽图片到此处</p>
                                            <p className="text-sm text-gray-400">支持 JPG、PNG
                                                等格式，大小不超过10MB</p>
                                        </div>
                                    )}
                                    <input
                                        id="fileInput"
                                        type="file"
                                        accept="image/*"
                                        onChange={handleFileChange}
                                        className="hidden"
                                    />
                                </div>
                            </TabsContent>

                            {/* 图片URL */}
                            <TabsContent value="url"
                                         className="flex-1 overflow-hidden min-h-0 mt-0 data-[state=active]:flex data-[state=active]:flex-col">
                                <div className="flex-shrink-0 mb-4">
                                    <Label htmlFor="imageUrl">图片 URL</Label>
                                    <Input
                                        id="imageUrl"
                                        type="url"
                                        placeholder="https://example.com/image.jpg"
                                        value={imageUrl}
                                        onChange={(e) => setImageUrl(e.target.value)}
                                        className="mt-2"
                                    />
                                </div>
                                {imageUrl && (
                                    <div
                                        className="flex-1 border rounded-lg p-4 flex items-center justify-center overflow-hidden min-h-0">
                                        <img
                                            src={imageUrl}
                                            alt="Preview"
                                            className="max-w-full max-h-full object-contain rounded"
                                            onError={() => {
                                                setError('图片加载失败，请检查URL是否正确');
                                            }}
                                        />
                                    </div>
                                )}
                            </TabsContent>

                            {/* 文件ID */}
                            <TabsContent value="fileId"
                                         className="flex-1 overflow-hidden min-h-0 mt-0 data-[state=active]:flex data-[state=active]:flex-col">
                                <div className="flex-shrink-0">
                                    <Label htmlFor="fileId">文件 ID</Label>
                                    <Input
                                        id="fileId"
                                        type="text"
                                        placeholder="输入文件服务中的文件ID"
                                        value={fileId}
                                        onChange={(e) => setFileId(e.target.value)}
                                        className="mt-2"
                                    />
                                </div>
                            </TabsContent>

                            {/* Base64 字符串 */}
                            <TabsContent value="base64"
                                         className="flex-1 overflow-hidden min-h-0 mt-0 data-[state=active]:flex data-[state=active]:flex-col">
                                <div className="flex-shrink-0 mb-4">
                                    <Label htmlFor="imageBase64Input">Base64 字符串</Label>
                                    <Textarea
                                        id="imageBase64Input"
                                        placeholder="粘贴图片的 Base64 编码字符串"
                                        value={imageBase64}
                                        onChange={(e) => {
                                            const value = e.target.value.trim();
                                            setImageBase64(value);
                                            // 实时生成预览
                                            generateBase64Preview(value);
                                        }}
                                        className="mt-2 min-h-32 font-mono text-xs"
                                    />
                                </div>
                                {base64PreviewUrl && (
                                    <div
                                        className="flex-1 border rounded-lg p-4 flex items-center justify-center overflow-hidden min-h-0">
                                        <img
                                            src={base64PreviewUrl}
                                            alt="Preview"
                                            className="max-w-full max-h-full object-contain rounded"
                                            onError={() => {
                                                setError('Base64 解码失败，请检查字符串格式是否正确');
                                            }}
                                        />
                                    </div>
                                )}
                            </TabsContent>
                        </Tabs>

                        {/* 错误提示 */}
                        {error && (
                            <Alert variant="destructive" className="mt-3 flex-shrink-0">
                                <AlertDescription>{error}</AlertDescription>
                            </Alert>
                        )}

                        {/* 按钮区域 - 固定在底部 */}
                        <div className="flex gap-2 mt-3 flex-shrink-0">
                            <Button
                                onClick={recognizeImage}
                                disabled={isLoading}
                                className="flex-1"
                            >
                                {isLoading ? '识别中...' : '开始识别'}
                            </Button>
                            <Button
                                variant="outline"
                                onClick={clearResults}
                                disabled={isLoading}
                            >
                                清除
                            </Button>
                        </div>
                    </CardContent>
                </Card>

                {/* 右侧：识别结果区域 */}
                <Card className="w-full lg:w-1/2 shadow-sm flex flex-col overflow-hidden">
                    <CardContent className="p-3 flex-1 overflow-hidden">
                        <div className="h-full overflow-y-auto p-3 bg-white rounded">
                            {isLoading ? (
                                <div className="flex items-center justify-center h-full text-gray-500">
                                    <p>正在识别图片，请稍候...</p>
                                </div>
                            ) : response ? (
                                renderOCRResult()
                            ) : (
                                <div className="flex items-center justify-center h-full text-gray-500">
                                    <p>选择图片并点击"开始识别"</p>
                                </div>
                            )}
                        </div>
                    </CardContent>
                </Card>
            </div>
        </div>
    );
}
