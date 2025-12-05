import React, { useState, useEffect, useRef } from 'react';

interface TokenResponse {
  token: string;
}

const GitHubStarButton: React.FC = () => {
  const [isAuthenticated, setIsAuthenticated] = useState<boolean>(false);
  const [isStarred, setIsStarred] = useState<boolean>(false);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [isProcessingOAuth, setIsProcessingOAuth] = useState<boolean>(false);
  const [shouldAutoStar, setShouldAutoStar] = useState<boolean>(false);

  const hasProcessedOAuth = useRef<boolean>(false);
  const errorStr = '点赞操作失败，请刷新后重试';

  // 当组件加载时检查认证状态和是否已点赞
  useEffect(() => {
    // 检查是否已登录（本地存储的token）
    const token = localStorage.getItem('github_token');
    if (token) {
      setIsAuthenticated(true);
      // 检查用户是否已经为仓库点赞
      checkIfStarred(token);
    }
    
    // 检查URL中是否有授权码
    if (typeof window !== 'undefined') {
      const urlParams = new URLSearchParams(window.location.search);
      const code = urlParams.get('code');
      
      if (code && !hasProcessedOAuth.current) {
        hasProcessedOAuth.current = true;
        // 有授权码，通过Next.js API交换获取token并自动点赞
        exchangeCodeForToken(code);
      }
    }
  }, []);

  // 自动点赞效果：当OAuth完成且用户未点赞时自动点赞
  useEffect(() => {
    if (shouldAutoStar && isAuthenticated && !isStarred && !isLoading) {
      const token = localStorage.getItem('github_token');
      if (token) {
        setShouldAutoStar(false); // 重置标志
        performStar(token);
      }
    }
  }, [shouldAutoStar, isAuthenticated, isStarred, isLoading]);

  // 检查用户是否已经为仓库点赞
  const checkIfStarred = async (token: string): Promise<void> => {
    try {
      const response = await fetch('/api/github/star', {
        headers: {
          Authorization: `Bearer ${token}`,
        },
      });
      
      if (response.ok) {
        const data = await response.json();
        setIsStarred(data.isStarred);
      } else {
        setIsStarred(false);
      }
    } catch (err) {
      console.error('检查星标状态失败:', err);
      setIsStarred(false);
    }
  };

  // 使用授权码通过Next.js API交换token
  const exchangeCodeForToken = async (code: string): Promise<void> => {
    if (isProcessingOAuth) return; // 防止重复处理
    
    setIsProcessingOAuth(true);
    setIsLoading(true);
    setError(null);
    
    try {
      const response = await fetch('/api/github/oauth', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ code }),
      });
      
      if (response.ok) {
        const data = await response.json() as TokenResponse;
        const token = data.token;
        
        if (token) {
          // 存储token到本地
          localStorage.setItem('github_token', token);
          setIsAuthenticated(true);
          
          // 清除URL中的code参数
          if (typeof window !== 'undefined' && window.history.replaceState) {
            const newUrl = window.location.pathname + 
                          (window.location.search ? window.location.search.replace(/[?&]code=[^&]+/, '') : '');
            window.history.replaceState({}, document.title, newUrl);
          }
          
          // 检查是否已点赞
          await checkIfStarred(token);
          // 设置自动点赞标志
          setShouldAutoStar(true);
        } else {
          throw new Error('响应中没有token');
        }
      } else {
        const errorData = await response.json();
        throw new Error(errorData.error || '获取token失败');
      }
    } catch (err) {
      setError(errorStr);
      console.error('Token交换失败:', err);
    } finally {
      setIsLoading(false);
      setIsProcessingOAuth(false);
    }
  };

  // 执行点赞操作
  const performStar = async (token: string): Promise<void> => {
    // 如果已经点赞了，什么都不做
    if (isStarred) {
      return;
    }
    
    try {
      const response = await fetch('/api/github/star', {
        method: 'PUT',
        headers: {
          Authorization: `Bearer ${token}`,
        },
      });
      
      if (response.ok) {
        setIsStarred(true);
      } else {
        const errorData = await response.json();
        throw new Error(errorData.error || '点赞操作失败');
      }
    } catch (err) {
      setError(errorStr);
      console.error('Star操作失败:', err);
    }
  };

  // 处理登录
  const handleLogin = async (): Promise<void> => {
    try {
      // 获取GitHub Client ID
      const configResponse = await fetch('/api/github/config');
      if (!configResponse.ok) {
        throw new Error('GitHub配置未正确设置');
      }
      
      const { clientId } = await configResponse.json();
      const redirectUri = window.location.href.split('?')[0]; // 移除现有的查询参数
      const authUrl = `https://github.com/login/oauth/authorize?client_id=${clientId}&redirect_uri=${encodeURIComponent(redirectUri)}&scope=public_repo`;
      
      // 跳转到GitHub授权页面
      window.location.href = authUrl;
    } catch (err) {
      setError('GitHub配置错误，请联系管理员');
      console.error('获取GitHub配置失败:', err);
    }
  };

  // 处理点赞操作
  const handleStar = async (): Promise<void> => {
    if (!isAuthenticated) {
      await handleLogin();
      return;
    }
    
    // 如果已经点赞了或正在处理中，什么都不做
    if (isStarred || isLoading || isProcessingOAuth) {
      return;
    }
    
    setIsLoading(true);
    const token = localStorage.getItem('github_token');
    
    if (!token) {
      setError(errorStr);
      setIsLoading(false);
      return;
    }
    
    try {
      await performStar(token);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="relative">
      {error && <div className="absolute top-full left-0 mt-1 text-red-500 text-xs whitespace-nowrap">{error}</div>}
      
      <button 
        onClick={handleStar}
        className={`
          px-3 py-1.5 rounded-md border transition-all duration-200 flex items-center gap-1.5 text-sm
          ${isStarred 
            ? 'bg-yellow-50 border-yellow-300 text-yellow-700 hover:bg-yellow-100' 
            : 'bg-white border-gray-300 text-gray-600 hover:bg-gray-50 hover:border-gray-400'
          }
          ${isLoading ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}
        `}
        disabled={isLoading}
      >
        <svg className="w-3.5 h-3.5" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
          <path fillRule="evenodd" d="M8 .25a.75.75 0 01.673.418l1.882 3.815 4.21.612a.75.75 0 01.416 1.279l-3.046 2.97.719 4.192a.75.75 0 01-1.088.791L8 12.347l-3.766 1.98a.75.75 0 01-1.088-.79l.72-4.194L.818 6.374a.75.75 0 01.416-1.28l4.21-.611L7.327.668A.75.75 0 018 .25zm0 2.445L6.615 5.5a.75.75 0 01-.564.41l-3.097.45 2.24 2.184a.75.75 0 01.216.664l-.528 3.084 2.769-1.456a.75.75 0 01.698 0l2.77 1.456-.53-3.084a.75.75 0 01.216-.664l2.24-2.183-3.096-.45a.75.75 0 01-.564-.41L8 2.694v.001z"></path>
        </svg>
        {isStarred ? '已点赞' : '点赞'}
      </button>
    </div>
  );
};

export default GitHubStarButton;