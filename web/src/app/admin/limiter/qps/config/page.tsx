'use client';

import { useState } from 'react';
import { ClientHeader } from '@/components/user/client-header';
import { getQpsConfig, updateQpsConfig, QpsConfig } from '@/lib/api/apikey';
import Link from 'next/link';

export default function QpsConfigPage() {
  const [akCode, setAkCode] = useState('');
  const [config, setConfig] = useState<QpsConfig | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [newLimit, setNewLimit] = useState<number | null>(null);

  const handleSearch = async () => {
    if (!akCode.trim()) {
      setError('请输入API Key Code');
      return;
    }

    setIsLoading(true);
    setError(null);
    setSuccess(null);

    try {
      const data = await getQpsConfig(akCode.trim());
      // 只有数据成功获取后才更新，防止闪动
      if (data) {
        setConfig(data);
        setNewLimit(data.limitQps);
      } else {
        setConfig(null);
        setError('未找到该API Key的配置');
      }
    } catch (e: any) {
      setConfig(null);
      setError(e?.error || e?.message || '查询失败');
    } finally {
      setIsLoading(false);
    }
  };

  const handleUpdate = async () => {
    if (!config || newLimit === null || newLimit <= 0) {
      setError('请输入有效的QPS限值（必须大于0）');
      return;
    }

    setIsSaving(true);
    setError(null);
    setSuccess(null);

    try {
      await updateQpsConfig(config.akCode, newLimit);
      setSuccess(`成功更新 ${config.akCode} 的QPS限值为 ${newLimit}`);
      // 重新查询以获取最新状态，但不清空当前显示，防止闪动
      const updatedConfig = await getQpsConfig(config.akCode);
      if (updatedConfig) {
        setConfig(updatedConfig);
        setNewLimit(updatedConfig.limitQps);
      }
    } catch (e: any) {
      setError(e?.error || e?.message || '更新失败');
    } finally {
      setIsSaving(false);
    }
  };

  const getUsageColor = (currentQps: number, limitQps: number) => {
    const usage = (currentQps / limitQps) * 100;
    if (usage >= 90) return 'text-red-600 font-bold';
    if (usage >= 70) return 'text-orange-600 font-semibold';
    return 'text-green-600';
  };

  return (
    <div className="min-h-screen bg-white dark:bg-white">
      <ClientHeader title="QPS配置管理" />
      <main className="container mx-auto p-6 max-w-4xl">
        {/* 页面标题 */}
        <div className="mb-6 flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">QPS配置管理</h1>
            <p className="mt-1 text-sm text-gray-600">查询和配置任意API Key的全局QPS限值</p>
          </div>
          <Link
            href="/admin/limiter/qps"
            className="px-4 py-2 rounded bg-indigo-600 text-white hover:bg-indigo-700"
          >
            返回排行榜
          </Link>
        </div>

        {/* 消息提示 */}
        {error && (
          <div className="bg-red-50 border-l-4 border-red-500 p-4 mb-4">
            <div className="text-sm text-red-700">{error}</div>
          </div>
        )}
        {success && (
          <div className="bg-green-50 border-l-4 border-green-500 p-4 mb-4">
            <div className="text-sm text-green-700">{success}</div>
          </div>
        )}

        {/* 搜索框 */}
        <div className="bg-white p-6 rounded-lg shadow-sm border border-gray-200 mb-6">
          <h2 className="text-lg font-semibold mb-4">查询API Key</h2>
          <div className="flex gap-3">
            <input
              type="text"
              value={akCode}
              onChange={(e) => setAkCode(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
              placeholder="输入API Key Code..."
              className="flex-1 border border-gray-300 rounded px-4 py-2 focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
            <button
              onClick={handleSearch}
              disabled={isLoading}
              className="px-6 py-2 rounded bg-indigo-600 text-white hover:bg-indigo-700 disabled:bg-gray-400 min-w-[100px]"
            >
              {isLoading ? '查询中...' : '查询'}
            </button>
          </div>
          <p className="mt-2 text-xs text-gray-500">
            提示: 无论该API Key当前是否有流量，都可以查询和配置
          </p>
        </div>

        {/* 配置详情和编辑 */}
        {config && (
          <div className="bg-white p-6 rounded-lg shadow-sm border border-gray-200">
            <h2 className="text-lg font-semibold mb-4">配置详情</h2>

            {/* 当前状态 */}
            <div className="grid grid-cols-2 gap-4 mb-6 p-4 bg-gray-50 rounded">
              <div>
                <div className="text-xs text-gray-500 uppercase">API Key Code</div>
                <div className="text-lg font-mono font-semibold text-gray-900">{config.akCode}</div>
              </div>
              <div>
                <div className="text-xs text-gray-500 uppercase">流量状态</div>
                <div className="text-lg">
                  {config.hasTraffic ? (
                    <span className="text-green-600 font-semibold">有活跃流量</span>
                  ) : (
                    <span className="text-gray-500">暂无流量</span>
                  )}
                </div>
              </div>
              <div>
                <div className="text-xs text-gray-500 uppercase">当前QPS</div>
                <div className={`text-lg font-semibold ${getUsageColor(config.currentQps, config.limitQps)}`}>
                  {config.currentQps.toFixed(2)}
                </div>
              </div>
              <div>
                <div className="text-xs text-gray-500 uppercase">当前RPM</div>
                <div className="text-lg font-semibold text-gray-900">{config.currentRpm}</div>
              </div>
              <div>
                <div className="text-xs text-gray-500 uppercase">当前QPS限值</div>
                <div className="text-lg font-semibold text-gray-900">{config.limitQps}</div>
              </div>
              <div>
                <div className="text-xs text-gray-500 uppercase">使用率</div>
                <div className={`text-lg font-semibold ${getUsageColor(config.currentQps, config.limitQps)}`}>
                  {((config.currentQps / config.limitQps) * 100).toFixed(1)}%
                </div>
              </div>
            </div>

            {/* 编辑限值 */}
            <div className="border-t pt-4">
              <h3 className="text-md font-semibold mb-3">修改QPS限值</h3>
              <div className="flex gap-3 items-center">
                <div className="flex-1">
                  <label className="block text-sm text-gray-600 mb-1">新的QPS限值</label>
                  <input
                    type="number"
                    value={newLimit || ''}
                    onChange={(e) => setNewLimit(parseInt(e.target.value, 10) || null)}
                    min={1}
                    className="w-full border border-gray-300 rounded px-4 py-2 focus:outline-none focus:ring-2 focus:ring-indigo-500"
                    placeholder="请输入新的QPS限值（必须大于0）"
                  />
                </div>
                <div className="flex gap-2 pt-6">
                  <button
                    onClick={() => setNewLimit(config.limitQps)}
                    className="px-4 py-2 rounded bg-gray-200 text-gray-700 hover:bg-gray-300"
                  >
                    重置
                  </button>
                  <button
                    onClick={handleUpdate}
                    disabled={isSaving || newLimit === null || newLimit <= 0 || newLimit === config.limitQps}
                    className="px-6 py-2 rounded bg-green-600 text-white hover:bg-green-700 disabled:bg-gray-400 min-w-[100px]"
                  >
                    {isSaving ? '保存中...' : '保存'}
                  </button>
                </div>
              </div>
              <p className="mt-2 text-xs text-gray-500">
                当前限值: {config.limitQps} QPS | 修改后立即生效
              </p>
            </div>
          </div>
        )}

        {/* 使用说明 */}
        <div className="mt-6 p-4 bg-blue-50 border border-blue-200 rounded">
          <h3 className="text-sm font-semibold text-blue-900 mb-2">使用说明</h3>
          <ul className="text-xs text-blue-800 space-y-1">
            <li>• 在搜索框输入API Key Code进行查询</li>
            <li>• 可以查询和配置任意API Key，无论是否有当前流量</li>
            <li>• QPS限值必须大于0，修改后立即生效</li>
            <li>• 建议根据实际业务需求合理设置限值，避免过高或过低</li>
            <li>• 使用率颜色说明: <span className="text-red-600 font-bold">红色</span> ≥90% | <span className="text-orange-600 font-semibold">橙色</span> ≥70% | <span className="text-green-600">绿色</span> &lt;70%</li>
          </ul>
        </div>
      </main>
    </div>
  );
}