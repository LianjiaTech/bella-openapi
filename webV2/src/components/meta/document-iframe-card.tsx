import { useState, useEffect } from 'react';
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { ExternalLink, Maximize2, Minimize2, RefreshCw } from "lucide-react";
import Link from "next/link";
import { Endpoint } from "@/lib/types/openapi";

interface DocumentIframeCardProps {
    endpoint: Endpoint;
}

export function DocumentIframeCard({ endpoint }: DocumentIframeCardProps) {
    const [isExpanded, setIsExpanded] = useState(false);
    const [isLoading, setIsLoading] = useState(true);
    const [refreshKey, setRefreshKey] = useState(0);
    const [loadError, setLoadError] = useState(false);
    const [errorMessage, setErrorMessage] = useState<string>("");

    const handleRefresh = () => {
        setIsLoading(true);
        setLoadError(false);
        setErrorMessage("");
        setRefreshKey(prev => prev + 1);
    };

    const toggleExpand = () => {
        setIsExpanded(!isExpanded);
    };

    const handleIframeLoad = () => {
        setIsLoading(false);
    };

    const handleIframeError = (e: React.SyntheticEvent<HTMLIFrameElement, Event>) => {
        setIsLoading(false);
        setLoadError(true);

        // 尝试获取更具体的错误信息
        const iframe = e.currentTarget;
        if (iframe.contentWindow?.location?.href) {
            try {
                // 检查是否是重定向错误
                if (iframe.contentWindow.location.href.includes('error') ||
                    iframe.contentWindow.location.href.includes('too_many_redirects')) {
                    setErrorMessage("文档加载失败：重定向次数过多。可能是由于文档需要登录或存在跨域限制。");
                }
            } catch (err) {
                // 跨域错误会在尝试访问 location.href 时抛出异常
                setErrorMessage("文档加载失败：跨域限制阻止了文档的加载。");
            }
        } else {
            setErrorMessage("文档加载失败：可能需要登录或存在其他访问限制。");
        }
    };

    // 使用 useEffect 监听 iframe 加载状态
    useEffect(() => {
        // 设置超时检测，如果加载时间过长，可能是重定向过多
        const timeoutId = setTimeout(() => {
            if (isLoading) {
                setLoadError(true);
                setIsLoading(false);
                setErrorMessage("文档加载超时：可能存在重定向次数过多的问题。请尝试在新标签页中打开。");
            }
        }, 60000); // 60秒超时

        return () => clearTimeout(timeoutId);
    }, [isLoading, refreshKey]);

    if (!endpoint.documentUrl) {
        return (
            <Card className="overflow-hidden bg-white shadow-lg border-none rounded-xl h-64 flex items-center justify-center">
                <div className="text-center p-6">
                    <p className="text-gray-500 mb-2">该能力点暂无文档</p>
                    <p className="text-sm text-gray-400">请联系维护人员添加文档</p>
                </div>
            </Card>
        );
    }

    return (
        <Card className={`overflow-hidden bg-white shadow-lg hover:shadow-xl transition-shadow duration-300 border-none rounded-xl ${isExpanded ? 'fixed inset-4 z-50' : 'h-[800px] w-full max-w-[1200px] mx-auto'}`}>
            <CardHeader className="bg-gradient-to-r from-blue-100 to-purple-100 pb-4 relative">
                <CardTitle className="flex items-center justify-between z-10 relative">
                    <span className="text-lg font-bold text-gray-800">{endpoint.endpointName} <span className="text-sm font-normal text-gray-500 ml-2">{endpoint.endpoint}</span></span>
                    <div className="flex items-center space-x-2">
                        <Button
                            variant="ghost"
                            size="sm"
                            onClick={handleRefresh}
                            className="text-gray-600 hover:text-gray-800 hover:bg-white/50"
                        >
                            <RefreshCw size={16} />
                        </Button>
                        <Button
                            variant="ghost"
                            size="sm"
                            onClick={toggleExpand}
                            className="text-gray-600 hover:text-gray-800 hover:bg-white/50"
                        >
                            {isExpanded ? <Minimize2 size={16} /> : <Maximize2 size={16} />}
                        </Button>
                        <Link
                            href={endpoint.documentUrl}
                            target="_blank"
                            rel="noopener noreferrer"
                        >
                            <Button variant="ghost" size="sm" className="text-gray-600 hover:text-gray-800 hover:bg-white/50">
                                <ExternalLink size={16} />
                            </Button>
                        </Link>
                    </div>
                </CardTitle>
            </CardHeader>
            <CardContent className="p-0 relative h-[calc(100%-80px)]">
                {isLoading && (
                    <div className="absolute inset-0 flex items-center justify-center bg-gray-50 bg-opacity-75 z-10">
                        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-500"></div>
                    </div>
                )}

                {loadError ? (
                    <div className="p-6 flex flex-col items-center justify-center h-full">
                        <p className="text-gray-700 font-medium mb-2 text-center">
                            {errorMessage || "文档加载失败"}
                        </p>
                        <p className="text-gray-600 mb-4 text-center">
                            请尝试在新标签页中打开文档
                        </p>
                        <div className="flex flex-col space-y-3">
                            <Link href={endpoint.documentUrl} target="_blank" rel="noopener noreferrer">
                                <Button className="bg-blue-600 hover:bg-blue-700 text-white">
                                    <ExternalLink size={16} className="mr-2" />
                                    在新标签页中打开文档
                                </Button>
                            </Link>
                            <Button
                                variant="outline"
                                onClick={handleRefresh}
                                className="border-blue-300 text-blue-600 hover:bg-blue-50"
                            >
                                <RefreshCw size={16} className="mr-2" />
                                重试加载
                            </Button>
                        </div>
                    </div>
                ) : (
                    <iframe
                        key={refreshKey}
                        src={endpoint.documentUrl}
                        className="w-full h-full border-0"
                        onLoad={handleIframeLoad}
                        onError={handleIframeError}
                        title={`${endpoint.endpointName} 文档`}
                        sandbox="allow-scripts allow-same-origin allow-popups allow-forms allow-popups-to-escape-sandbox"
                        referrerPolicy="no-referrer"
                    />
                )}
            </CardContent>
        </Card>
    );
}
