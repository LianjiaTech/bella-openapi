'use client';

import { useEffect, useState, useRef } from 'react';
import { ClientHeader } from '@/components/user/client-header';
import { getQpsRanking, QpsRankingItem } from '@/lib/api/apikey';
import Link from 'next/link';

export default function QpsRankingPage() {
  const [ranking, setRanking] = useState<QpsRankingItem[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [autoRefresh, setAutoRefresh] = useState(false);
  const [topN, setTopN] = useState(50);
  const [refreshInterval, setRefreshInterval] = useState(10);
  const [countdown, setCountdown] = useState(0);
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const intervalRef = useRef<NodeJS.Timeout | null>(null);
  const countdownRef = useRef<NodeJS.Timeout | null>(null);
  const isRequestingRef = useRef<boolean>(false); // 请求锁

  const fetchRanking = async (showLoading: boolean = true) => {
    // 如果正在请求中，跳过本次请求
    if (isRequestingRef.current) {
      console.log('[防重复请求] 上一次请求尚未完成，跳过本次请求');
      return;
    }

    // 设置请求锁
    isRequestingRef.current = true;

    if (showLoading) {
      setIsLoading(true);
    }
    setError(null);
    try {
      const data = await getQpsRanking(topN);
      // 只有数据成功获取后才更新，防止闪动
      if (data) {
        setRanking(data);
        setCurrentPage(1); // 重新加载数据后回到第一页
      }
    } catch (e: any) {
      setError(e?.error || e?.message || '加载排名失败');
    } finally {
      if (showLoading) {
        setIsLoading(false);
      }
      // 释放请求锁
      isRequestingRef.current = false;
    }
  };

  // 初始加载
  useEffect(() => {
    fetchRanking();
  }, []);

  // 自动刷新和倒计时逻辑
  useEffect(() => {
    if (autoRefresh) {
      // 重置倒计时
      setCountdown(refreshInterval);

      // 倒计时定时器（每秒更新）
      countdownRef.current = setInterval(() => {
        setCountdown((prev) => {
          if (prev <= 1) {
            return refreshInterval; // 重置倒计时
          }
          return prev - 1;
        });
      }, 1000);

      // 自动刷新定时器
      intervalRef.current = setInterval(() => {
        fetchRanking(false); // 自动刷新时不显示loading状态
      }, refreshInterval * 1000);
    } else {
      // 清除定时器
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
      if (countdownRef.current) {
        clearInterval(countdownRef.current);
        countdownRef.current = null;
      }
      setCountdown(0);
    }

    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
      }
      if (countdownRef.current) {
        clearInterval(countdownRef.current);
      }
    };
  }, [autoRefresh, refreshInterval]);

  const handleManualRefresh = () => {
    fetchRanking(true);
    // 如果自动刷新开启，重置倒计时
    if (autoRefresh) {
      setCountdown(refreshInterval);
    }
  };

  const toggleAutoRefresh = () => {
    setAutoRefresh(!autoRefresh);
  };

  const handleTopNChange = (value: string) => {
    const num = parseInt(value, 10);
    // 验证：必须是正整数且大于0
    if (!isNaN(num) && num > 0 && Number.isInteger(num)) {
      setTopN(num);
    } else if (value === '') {
      setTopN(0); // 允许清空输入框
    }
  };

  const handleRefreshIntervalChange = (value: string) => {
    const num = parseInt(value, 10);
    // 验证：必须是正整数且大于0
    if (!isNaN(num) && num > 0 && Number.isInteger(num)) {
      setRefreshInterval(num);
    } else if (value === '') {
      setRefreshInterval(0); // 允许清空输入框
    }
  };

  const handleApplyTopN = () => {
    // 应用前验证topN是否有效
    if (topN > 0) {
      fetchRanking(true);
    }
  };

  const getUsageColor = (currentQps: number, limitQps: number) => {
    const usage = (currentQps / limitQps) * 100;
    if (usage >= 90) return 'text-red-600 font-bold';
    if (usage >= 70) return 'text-orange-600 font-semibold';
    return 'text-gray-700';
  };

  // 分页计算
  const totalPages = Math.ceil(ranking.length / pageSize);
  const startIndex = (currentPage - 1) * pageSize;
  const endIndex = startIndex + pageSize;
  const currentPageData = ranking.slice(startIndex, endIndex);

  const goToPage = (page: number) => {
    if (page >= 1 && page <= totalPages) {
      setCurrentPage(page);
    }
  };

  const handlePageSizeChange = (size: number) => {
    setPageSize(size);
    setCurrentPage(1);
  };

  return (
    <div className="min-h-screen bg-white dark:bg-white">
      <ClientHeader title="QPS监控排行榜" />
      <main className="container mx-auto p-6">
        <div className="max-w-7xl mx-auto">
            {error && (
              <div className="bg-red-50 border-l-4 border-red-500 p-4 mb-4">
                <div className="text-sm text-red-700">{error}</div>
              </div>
            )}
            {/* 页面标题 */}
            <div className="mb-4 flex items-center justify-between">
              <div>
                <h1 className="text-2xl font-bold text-gray-900">QPS监控排行榜</h1>
                <p className="mt-1 text-sm text-gray-600">实时监控当前QPS最高的API Key（仅显示有流量的Key）</p>
              </div>
              <Link
                href="/admin/limiter/qps/config"
                className="px-4 py-2 rounded bg-green-600 text-white hover:bg-green-700"
              >
                QPS配置管理
              </Link>
            </div>

            {/* 参数设置 */}
            <div className="bg-white p-4 rounded-lg shadow-sm border border-gray-200 mb-4">
              <div className="flex items-center gap-6 flex-wrap">
                {/* Top N 设置 */}
                <div className="flex items-center gap-2">
                  <label className="text-sm font-medium text-gray-700">显示Top:</label>
                  <input
                    type="number"
                    value={topN || ''}
                    onChange={(e) => handleTopNChange(e.target.value)}
                    onKeyDown={(e) => e.key === 'Enter' && handleApplyTopN()}
                    min={1}
                    max={500}
                    placeholder="1-500"
                    className="w-20 border border-gray-300 rounded px-3 py-1 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  />
                  <button
                    onClick={handleApplyTopN}
                    disabled={isLoading || topN <= 0}
                    className="px-4 py-1 rounded bg-indigo-600 text-white text-sm hover:bg-indigo-700 disabled:bg-gray-400 disabled:cursor-not-allowed"
                  >
                    应用
                  </button>
                </div>

                {/* 刷新间隔设置 */}
                <div className="flex items-center gap-2">
                  <label className="text-sm font-medium text-gray-700">刷新间隔(秒):</label>
                  <input
                    type="number"
                    value={refreshInterval || ''}
                    onChange={(e) => handleRefreshIntervalChange(e.target.value)}
                    min={1}
                    max={300}
                    placeholder="1-300"
                    disabled={autoRefresh}
                    className="w-20 border border-gray-300 rounded px-3 py-1 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 disabled:bg-gray-100 disabled:cursor-not-allowed"
                  />
                  {autoRefresh && (
                    <span className="text-xs text-amber-600">自动刷新开启时不可编辑</span>
                  )}
                </div>

                {/* 状态信息 */}
                <div className="flex items-center gap-4 ml-auto">
                  <span className="text-xs text-gray-500">
                    当前加载: {ranking.length} 条
                  </span>
                  {autoRefresh && (
                    <span className="text-xs text-green-600 font-medium">
                      每 {refreshInterval} 秒自动刷新
                    </span>
                  )}
                </div>
              </div>
            </div>

            <div className="bg-white p-4 rounded-lg shadow-sm border border-gray-200">
              <div className="flex items-center justify-between mb-4">
                <h2 className="text-lg font-semibold">当前流量排行（全局聚合）</h2>
                <div className="flex items-center gap-3">
                  {/* 自动刷新按钮 */}
                  <button
                    onClick={toggleAutoRefresh}
                    className={`px-4 py-2 rounded font-medium text-sm transition-colors ${
                      autoRefresh
                        ? 'bg-green-600 text-white hover:bg-green-700'
                        : 'bg-gray-200 text-gray-700 hover:bg-gray-300'
                    }`}
                  >
                    {autoRefresh ? (
                      <span className="flex items-center gap-2">
                        <span className="inline-block w-2 h-2 bg-white rounded-full animate-pulse"></span>
                        自动刷新中 ({countdown}s)
                      </span>
                    ) : (
                      '开启自动刷新'
                    )}
                  </button>

                  {/* 手动刷新按钮 */}
                  <button
                    className="px-4 py-2 rounded bg-indigo-600 text-white hover:bg-indigo-700 disabled:bg-gray-400 disabled:cursor-not-allowed min-w-[100px] font-medium text-sm"
                    onClick={handleManualRefresh}
                    disabled={isLoading || autoRefresh}
                  >
                    {isLoading ? (
                      <span className="flex items-center justify-center gap-2">
                        <span className="inline-block w-3 h-3 border-2 border-white border-t-transparent rounded-full animate-spin"></span>
                        刷新中
                      </span>
                    ) : (
                      '手动刷新'
                    )}
                  </button>
                </div>
              </div>
              <div className="overflow-x-auto">
                <table className="min-w-full divide-y divide-gray-200">
                  <thead className="bg-gray-50">
                    <tr>
                      <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">排名</th>
                      <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">AK Code</th>
                      <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">当前QPS</th>
                      <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">当前RPM</th>
                      <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">限值(QPS)</th>
                      <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">使用率</th>
                    </tr>
                  </thead>
                  <tbody className="bg-white divide-y divide-gray-200">
                    {currentPageData.length === 0 && !isLoading ? (
                      <tr>
                        <td colSpan={6} className="px-4 py-8 text-center text-gray-500">
                          当前没有活跃流量的API Key
                        </td>
                      </tr>
                    ) : (
                      currentPageData.map((item, index) => {
                        const globalIndex = startIndex + index;
                        const usagePercent = ((item.currentQps / item.limitQps) * 100).toFixed(1);
                        return (
                          <tr key={item.akCode} className="hover:bg-gray-50">
                            <td className="px-4 py-2 text-sm text-gray-700 font-medium">#{globalIndex + 1}</td>
                            <td className="px-4 py-2 text-sm text-gray-700 font-mono">{item.akCode}</td>
                            <td className={`px-4 py-2 text-sm ${getUsageColor(item.currentQps, item.limitQps)}`}>
                              {item.currentQps?.toFixed(2)}
                            </td>
                            <td className="px-4 py-2 text-sm text-gray-700">{item.currentRpm}</td>
                            <td className="px-4 py-2 text-sm text-gray-700">{item.limitQps || 100}</td>
                            <td className={`px-4 py-2 text-sm ${getUsageColor(item.currentQps, item.limitQps)}`}>
                              {usagePercent}%
                            </td>
                          </tr>
                        );
                      })
                    )}
                  </tbody>
                </table>
              </div>

              {/* 分页控件 */}
              {ranking.length > 0 && (
                <div className="mt-4 flex items-center justify-between border-t pt-4">
                  <div className="flex items-center gap-4">
                    <span className="text-sm text-gray-700">
                      共 {ranking.length} 条记录，第 {currentPage} / {totalPages} 页
                    </span>
                    <div className="flex items-center gap-2">
                      <label className="text-sm text-gray-600">每页:</label>
                      <select
                        value={pageSize}
                        onChange={(e) => handlePageSizeChange(parseInt(e.target.value, 10))}
                        className="border border-gray-300 rounded px-2 py-1 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                      >
                        <option value={10}>10</option>
                        <option value={20}>20</option>
                        <option value={50}>50</option>
                        <option value={100}>100</option>
                      </select>
                    </div>
                  </div>

                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => goToPage(1)}
                      disabled={currentPage === 1}
                      className="px-3 py-1 rounded border border-gray-300 text-sm hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                      首页
                    </button>
                    <button
                      onClick={() => goToPage(currentPage - 1)}
                      disabled={currentPage === 1}
                      className="px-3 py-1 rounded border border-gray-300 text-sm hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                      上一页
                    </button>

                    {/* 页码按钮 */}
                    <div className="flex gap-1">
                      {Array.from({ length: Math.min(5, totalPages) }, (_, i) => {
                        let pageNum;
                        if (totalPages <= 5) {
                          pageNum = i + 1;
                        } else if (currentPage <= 3) {
                          pageNum = i + 1;
                        } else if (currentPage >= totalPages - 2) {
                          pageNum = totalPages - 4 + i;
                        } else {
                          pageNum = currentPage - 2 + i;
                        }

                        return (
                          <button
                            key={pageNum}
                            onClick={() => goToPage(pageNum)}
                            className={`px-3 py-1 rounded text-sm ${
                              currentPage === pageNum
                                ? 'bg-indigo-600 text-white'
                                : 'border border-gray-300 hover:bg-gray-50'
                            }`}
                          >
                            {pageNum}
                          </button>
                        );
                      })}
                    </div>

                    <button
                      onClick={() => goToPage(currentPage + 1)}
                      disabled={currentPage === totalPages}
                      className="px-3 py-1 rounded border border-gray-300 text-sm hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                      下一页
                    </button>
                    <button
                      onClick={() => goToPage(totalPages)}
                      disabled={currentPage === totalPages}
                      className="px-3 py-1 rounded border border-gray-300 text-sm hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                      末页
                    </button>
                  </div>
                </div>
              )}

              <div className="mt-4 text-xs text-gray-500">
                <p>颜色说明: <span className="text-red-600 font-bold">红色</span> ≥90% | <span className="text-orange-600 font-semibold">橙色</span> ≥70% | <span className="text-gray-700">灰色</span> &lt;70%</p>
              </div>
            </div>
        </div>
      </main>
    </div>
  );
}

