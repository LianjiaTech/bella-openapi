"use client"

import { useState, useEffect, useRef } from 'react';
import {
  getGitHubConfig,
  exchangeOAuthCode,
  checkStarStatus,
  starRepository
} from '@/lib/api/github';

interface UseGitHubStarReturn {
  isAuthenticated: boolean;
  isStarred: boolean;
  isLoading: boolean;
  error: string | null;
  handleStar: () => Promise<void>;
  checkStarStatus: () => Promise<void>;
  clearError: () => void;
}

const ERROR_MESSAGE = '点赞操作失败，请刷新后重试';
const TOKEN_KEY = 'github_token';

export function useGitHubStar(): UseGitHubStarReturn {
  const [isAuthenticated, setIsAuthenticated] = useState<boolean>(false);
  const [isStarred, setIsStarred] = useState<boolean>(false);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [isProcessingOAuth, setIsProcessingOAuth] = useState<boolean>(false);
  const [shouldAutoStar, setShouldAutoStar] = useState<boolean>(false);

  const hasProcessedOAuth = useRef<boolean>(false);

  // Initialize: check for existing token and OAuth code in URL
  useEffect(() => {
    const token = localStorage.getItem(TOKEN_KEY);
    if (token) {
      setIsAuthenticated(true);
      checkIfStarred(token);
    }

    // Check URL for OAuth authorization code
    if (typeof window !== 'undefined') {
      const urlParams = new URLSearchParams(window.location.search);
      const code = urlParams.get('code');

      if (code && !hasProcessedOAuth.current) {
        hasProcessedOAuth.current = true;
        handleOAuthCodeExchange(code);
      }
    }
  }, []);

  // Auto-star effect: trigger auto-star when OAuth completes and user hasn't starred
  useEffect(() => {
    if (shouldAutoStar && isAuthenticated && !isStarred && !isLoading) {
      const token = localStorage.getItem(TOKEN_KEY);
      if (token) {
        setShouldAutoStar(false);
        performStar(token);
      }
    }
  }, [shouldAutoStar, isAuthenticated, isStarred, isLoading]);

  // Check if user has starred the repository
  const checkIfStarred = async (token: string): Promise<void> => {
    try {
      const data = await checkStarStatus(token);
      setIsStarred(data.isStarred);
    } catch (err) {
      console.error('检查星标状态失败:', err);
      setIsStarred(false);
    }
  };

  // Exchange OAuth code for access token
  const handleOAuthCodeExchange = async (code: string): Promise<void> => {
    if (isProcessingOAuth) return;

    setIsProcessingOAuth(true);
    setIsLoading(true);
    setError(null);

    try {
      const data = await exchangeOAuthCode(code);
      const token = data.token;

      if (token) {
        // Store token to localStorage
        localStorage.setItem(TOKEN_KEY, token);
        setIsAuthenticated(true);

        // Clear code parameter from URL
        if (typeof window !== 'undefined' && window.history.replaceState) {
          const newUrl = window.location.pathname +
                        (window.location.search ? window.location.search.replace(/[?&]code=[^&]+/, '') : '');
          window.history.replaceState({}, document.title, newUrl);
        }

        // Check if already starred
        await checkIfStarred(token);
        // Set auto-star flag
        setShouldAutoStar(true);
      } else {
        throw new Error('响应中没有token');
      }
    } catch (err) {
      setError(ERROR_MESSAGE);
      console.error('Token交换失败:', err);
    } finally {
      setIsLoading(false);
      setIsProcessingOAuth(false);
    }
  };

  // Perform star operation
  const performStar = async (token: string): Promise<void> => {
    if (isStarred) {
      return;
    }

    try {
      await starRepository(token);
      setIsStarred(true);
    } catch (err) {
      setError(ERROR_MESSAGE);
      console.error('Star操作失败:', err);
    }
  };

  // Initiate GitHub login
  const handleLogin = async (): Promise<void> => {
    try {
      const { clientId } = await getGitHubConfig();
      const redirectUri = window.location.href.split('?')[0];
      const authUrl = `https://github.com/login/oauth/authorize?client_id=${clientId}&redirect_uri=${encodeURIComponent(redirectUri)}&scope=public_repo`;

      window.location.href = authUrl;
    } catch (err) {
      setError('GitHub配置错误，请联系管理员');
      console.error('获取GitHub配置失败:', err);
    }
  };

  // Handle star button click
  const handleStar = async (): Promise<void> => {
    if (!isAuthenticated) {
      await handleLogin();
      return;
    }

    if (isStarred || isLoading || isProcessingOAuth) {
      return;
    }

    setIsLoading(true);
    const token = localStorage.getItem(TOKEN_KEY);

    if (!token) {
      setError(ERROR_MESSAGE);
      setIsLoading(false);
      return;
    }

    try {
      await performStar(token);
    } finally {
      setIsLoading(false);
    }
  };

  // Public method to check star status
  const checkStarStatusPublic = async (): Promise<void> => {
    const token = localStorage.getItem(TOKEN_KEY);
    if (token) {
      await checkIfStarred(token);
    }
  };

  // Clear error message
  const clearError = (): void => {
    setError(null);
  };

  return {
    isAuthenticated,
    isStarred,
    isLoading,
    error,
    handleStar,
    checkStarStatus: checkStarStatusPublic,
    clearError,
  };
}
