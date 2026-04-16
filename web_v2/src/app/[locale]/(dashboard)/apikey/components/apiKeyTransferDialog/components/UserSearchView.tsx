'use client'

import { Button } from "@/components/common/button";
import { Search } from "lucide-react";
import { Badge } from "@/components/common/badge";
import { useState, useEffect, useDeferredValue } from "react";
import { searchUserInfo } from "@/lib/api/apiKeys";
import { UserSearchResult } from "@/lib/types/apikeys";

interface TransferResultData {
  status: 'success' | 'error';
  targetUserName?: string;
  targetUserEmail?: string;
  errorMessage?: string;
}

interface UserSearchViewProps {
  apiKeyCode: string;
  akDisplay: string;
  onViewHistory: () => void;
  onShowResult: (data: any) => void;
}

export function UserSearchView({
  apiKeyCode,
  akDisplay,
  onViewHistory,
  onShowResult,
}: UserSearchViewProps) {
  const [searchQuery, setSearchQuery] = useState("");
  const [searchResults, setSearchResults] = useState<UserSearchResult[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string>("");

  // 使用 useDeferredValue 优化性能,降低高频输入时的渲染压力
  const deferredSearchQuery = useDeferredValue(searchQuery);

  // 执行搜索的函数
  const performSearch = async (query: string) => {
    setIsLoading(true);
    setError("");

    try {
      const results = await searchUserInfo(query);
      setSearchResults(results);
    } catch (err) {
      setError("搜索失败,请重试");
      console.error('搜索用户失败:', err);
    } finally {
      setIsLoading(false);
    }
  };

  // 使用 useEffect 实现自动搜索,带防抖功能
  useEffect(() => {
    // 如果搜索内容为空,清空结果
    if (deferredSearchQuery.trim().length < 1) {
      setSearchResults([]);
      setIsLoading(false);
      return;
    }

    // 500ms 防抖延迟
    const timer = setTimeout(() => {
      performSearch(deferredSearchQuery);
    }, 500);

    return () => clearTimeout(timer);
  }, [deferredSearchQuery]);

  return (
    <div className="max-h-[60vh] overflow-y-auto pr-2 space-y-4">
      <div className="flex items-center justify-between">
        <span className="text-sm font-medium">搜索用户</span>
        <Button
          variant="link"
          onClick={onViewHistory}
          className="text-blue-500 text-xs h-auto p-0 cursor-pointer"
        >
          查看转交历史
        </Button>
      </div>

      {/* 搜索输入框 */}
      <div className="relative">
        <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-gray-400" />
        <input
          type="text"
          placeholder="输入用户名、邮箱或ID"
          className="w-full pl-10 pr-4 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
        />
      </div>

      {/* 搜索结果区域 */}
      <div className="max-h-[200px] overflow-y-auto border border-gray-200 rounded-md p-4">
        {isLoading ? (
          <div className="flex items-center justify-center text-sm text-gray-400">
            搜索中...
          </div>
        ) : error ? (
          <div className="flex items-center justify-center text-sm text-red-500">
            {error}
          </div>
        ) : searchResults.length > 0 ? (
          <div className="space-y-2">
            {searchResults.map((user) => (
              <div
                key={user.id}
                className="flex justify-between items-center p-2 hover:bg-gray-50 rounded cursor-pointer border border-gray-100"
              >
                <div><div className="text-sm font-medium">{user.userName}</div>
                <div className="text-xs text-gray-500">{user.email}</div>
                <div className="text-xs text-gray-400">ID: {user.sourceId}</div></div>
                <a
                  className="text-blue-500 cursor-pointer text-xs"
                  onClick={() => {
                    // 模拟成功场景，实际应调用转交API
                    onShowResult(user);
                  }}
                >
                  确认转交
                </a>
              </div>
            ))}
          </div>
        ) : deferredSearchQuery.trim().length >= 1 ? (
          <div className="flex items-center justify-center text-sm text-gray-400">
            未找到匹配的用户
          </div>
        ) : searchQuery.trim().length > 0 ? (
          <div className="flex items-center justify-center text-sm text-gray-400">
            输入中...
          </div>
        ) : (
          <div className="flex items-center justify-center text-sm text-gray-400">
            请输入关键词开始搜索
          </div>
        )}
      </div>
    </div>
  );
}
