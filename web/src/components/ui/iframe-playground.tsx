"use client";

import { useState, useEffect } from "react";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/common/card";
import { Button } from "@/components/common/button";
import { ExternalLink, Maximize2, Minimize2, RefreshCw } from "lucide-react";
import Link from "next/link";

interface IframePlaygroundProps {

  title: string;
  
  url?: string;
 
  height?: string;
 
  unavailableMessage?: string;
}


enum LoadingState {
  LOADING = 'loading',
  LOADED = 'loaded',
  ERROR = 'error',
  TIMEOUT = 'timeout',
}

export function IframePlayground({
  title,
  url,
  height = "800px",
  unavailableMessage = "功能暂未开放"
}: IframePlaygroundProps) {
  
  const [isExpanded, setIsExpanded] = useState(false);
  const [loadingState, setLoadingState] = useState<LoadingState>(LoadingState.LOADING);
  const [refreshKey, setRefreshKey] = useState(0);
  const [errorMessage, setErrorMessage] = useState<string>("");

  
  const handleRefresh = () => {
    setLoadingState(LoadingState.LOADING);
    setErrorMessage("");
    setRefreshKey(prev => prev + 1);
  };

  const toggleExpand = () => {
    setIsExpanded(!isExpanded);
  };

  const handleIframeLoad = () => {
    setLoadingState(LoadingState.LOADED);
  };


  const handleIframeError = (e: React.SyntheticEvent<HTMLIFrameElement, Event>) => {
    setLoadingState(LoadingState.ERROR);

    const iframe = e.currentTarget;
    if (iframe.contentWindow?.location?.href) {
      try {

        if (iframe.contentWindow.location.href.includes('error') ||
            iframe.contentWindow.location.href.includes('too_many_redirects')) {
          setErrorMessage("页面加载失败：重定向次数过多。可能是由于页面需要登录或存在跨域限制。");
        }
      } catch (err) {
       
        setErrorMessage("页面加载失败：跨域限制阻止了页面的加载。");
      }
    } else {
      setErrorMessage("页面加载失败：可能需要登录或存在其他访问限制。");
    }
  };


  useEffect(() => {
    if (!url) return;

    
    const timeoutId = setTimeout(() => {
      if (loadingState === LoadingState.LOADING) {
        setLoadingState(LoadingState.TIMEOUT);
        setErrorMessage("页面加载超时：可能存在重定向次数过多的问题。请尝试在新标签页中打开。");
      }
    }, 60000); 

    return () => clearTimeout(timeoutId);
  }, [loadingState, refreshKey, url]);


  if (!url) {
    return (
      <Card className="overflow-hidden bg-card border border-border/50 rounded-lg h-64 flex items-center justify-center">
        <div className="text-center p-6">
          <p className="text-muted-foreground mb-2 text-lg">{unavailableMessage}</p>
          <p className="text-sm text-muted-foreground">即将推出</p>
        </div>
      </Card>
    );
  }

  return (
    <Card
      className={`overflow-hidden bg-card border border-border/50 hover:shadow-lg transition-shadow duration-300 rounded-lg ${
        isExpanded ? 'fixed inset-4 z-50' : 'w-full'
      }`}
      style={{ height: isExpanded ? 'auto' : height }}
    >
      {      }
      <CardHeader className="bg-gradient-to-r from-blue-50 to-purple-50 dark:from-blue-950 dark:to-purple-950 pb-4 relative border-b border-border/50">
        <CardTitle className="flex items-center justify-between z-10 relative text-lg">
          <span className="font-semibold text-foreground">{title}</span>
          <div className="flex items-center space-x-2">

            <Button
              variant="ghost"
              size="sm"
              onClick={handleRefresh}
              className="text-muted-foreground hover:text-foreground hover:bg-accent"
              title="刷新"
            >
              <RefreshCw size={16} />
            </Button>

            <Button
              variant="ghost"
              size="sm"
              onClick={toggleExpand}
              className="text-muted-foreground hover:text-foreground hover:bg-accent"
              title={isExpanded ? "退出全屏" : "全屏"}
            >
              {isExpanded ? <Minimize2 size={16} /> : <Maximize2 size={16} />}
            </Button>

            {     }
            <Link
              href={url}
              target="_blank"
              rel="noopener noreferrer"
              title="在新标签页中打开"
            >
              <Button
                variant="ghost"
                size="sm"
                className="text-muted-foreground hover:text-foreground hover:bg-accent"
              >
                <ExternalLink size={16} />
              </Button>
            </Link>
          </div>
        </CardTitle>
      </CardHeader>

      {    }
      <CardContent className="p-0 relative h-[calc(100%-80px)]">
        {   }
        {loadingState === LoadingState.LOADING && (
          <div className="absolute inset-0 flex items-center justify-center bg-muted/50 z-10">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
          </div>
        )}

  
        {(loadingState === LoadingState.ERROR || loadingState === LoadingState.TIMEOUT) ? (
          <div className="p-6 flex flex-col items-center justify-center h-full">
            <p className="text-foreground font-medium mb-2 text-center">
              {errorMessage || "页面加载失败"}
            </p>
            <p className="text-muted-foreground mb-4 text-center">
              请尝试在新标签页中打开页面
            </p>
            <div className="flex flex-col space-y-3">
              <Link href={url} target="_blank" rel="noopener noreferrer">
                <Button className="bg-primary text-primary-foreground hover:bg-primary/90">
                  <ExternalLink size={16} className="mr-2" />
                  在新标签页中打开
                </Button>
              </Link>
              <Button
                variant="outline"
                onClick={handleRefresh}
                className="border-border hover:bg-accent"
              >
                <RefreshCw size={16} className="mr-2" />
                重试加载
              </Button>
            </div>
          </div>
        ) : (
          
          <iframe
            key={refreshKey}
            src={url}
            className="w-full h-full border-0"
            onLoad={handleIframeLoad}
            onError={handleIframeError}
            title={title}
            sandbox="allow-scripts allow-same-origin allow-popups allow-forms allow-popups-to-escape-sandbox"
            referrerPolicy="no-referrer"
          />
        )}
      </CardContent>
    </Card>
  );
}
