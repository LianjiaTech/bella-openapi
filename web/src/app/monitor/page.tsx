'use client';

import {Suspense, useEffect, useState} from 'react';
import {MetricsLineChart} from '@/components/ui/metrics-line-chart';
import {ClientHeader} from "@/components/user/client-header";
import {DateTimeRangePicker} from "@/components/ui/date-time-range-picker";
import {ModelSelect} from "@/components/ui/model-select";
import {format, subDays, subMinutes} from 'date-fns';
import {Sidebar} from '@/components/meta/sidebar';
import {getAllCategoryTrees, listModels} from '@/lib/api/meta';
import {CategoryTree, Model, MonitorData} from "@/lib/types/openapi";
import { useSearchParams } from 'next/navigation';

// 预定义的颜色数组
const colors = [
  '#6366f1', // Indigo
  '#ec4899', // Pink
  '#10b981', // Emerald
  '#f59e0b', // Amber
  '#3b82f6', // Blue
  '#8b5cf6', // Purple
  '#ef4444', // Red
  '#14b8a6', // Teal
  '#f97316', // Orange
  '#84cc16', // Lime
  '#06b6d4', // Cyan
  '#d946ef', // Fuchsia
  '#0ea5e9', // Light Blue
  '#22c55e', // Green
  '#eab308', // Yellow
  '#a855f7', // Purple
  '#f43f5e', // Rose
  '#64748b', // Slate
  '#6b7280', // Gray
  '#78716c', // Stone
];

// 动态生成颜色映射
const getChannelColors = (channels: string[]) => {
  if (!Array.isArray(channels) || channels.length === 0) {
    return {};
  }

  const colorMap: { [key: string]: string } = {};
  // 对 channels 进行排序，确保相同的 channel 总是获得相同的颜色
  const sortedChannels = [...channels].sort();
  sortedChannels.forEach((channel, index) => {
    colorMap[channel] = colors[index % colors.length];
  });
  return colorMap;
};

// 转换数据格式
const transformData = (data: MonitorData[], metricType: keyof MonitorData['metrics']): { time: string; channels: { [key: string]: { value: number; status: number; rawData?: number[] } } }[] => {
  // 确保 data 是数组
  if (!Array.isArray(data) || data.length === 0) {
    return [];
  }

  // 按时间分组
  const timeGroups = data.reduce((acc, item) => {
    // 确保时间格式为 YYYYMMDDHHMM
    const time = item.time.replace(/[-: ]/g, '').slice(0, 12);
    const timeGroup = acc.get(time) || [];
    timeGroup.push(item);
    acc.set(time, timeGroup);
    return acc;
  }, new Map<string, MonitorData[]>());

  // 转换为 MetricsData 格式
  return Array.from<[string, MonitorData[]]>(timeGroups.entries()).map(([time, items]) => {
    const channels: { [key: string]: { value: number; status: number; rawData?: number[] } } = {};

    items.forEach(item => {
      const value = item.metrics[metricType];
      channels[item.channel_code] = {
        value: metricType === 'status' ? (value === 1 ? 0 : 1) : value,
        status: item.metrics.status === 0 ? 0 : 1,
        rawData: [metricType === 'status' ? (value === 1 ? 0 : 1) : value]
      };
    });

    return {
      time,
      channels
    };
  }).sort((a, b) => a.time.localeCompare(b.time));
};

// 获取所有唯一的渠道
const getUniqueChannels = (data: MonitorData[]) => {
  if (!Array.isArray(data) || data.length === 0) {
    return [];
  }
  return Array.from(new Set(data.map(item => item.channel_code))).sort();
};

function MonitorPageContent({ params }: { params: { model: string } }) {
  const searchParams = useSearchParams();
  const endpointParam = searchParams.get('endpoint');
  const [selectedEndpoint, setSelectedEndpoint] = useState<string>(endpointParam || '/v1/chat/completions');
  const [models, setModels] = useState<Model[]>([]);
  const [filteredModels, setFilteredModels] = useState<Model[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedModel, setSelectedModel] = useState<string>(params.model);
  const [selectedDisplayModel, setSelectedDisplayModel] = useState<string>(params.model);
  const [categoryTrees, setCategoryTrees] = useState<CategoryTree[]>([]);
  const [startDate, setStartDate] = useState(subMinutes(new Date(), 30));
  const [endDate, setEndDate] = useState(new Date());
  const [currentData, setCurrentData] = useState<MonitorData[]>([]);
  const [channels, setChannels] = useState<string[]>([]);
  const [selectedChannels, setSelectedChannels] = useState<string[]>([]);
  const [channelColors, setChannelColors] = useState<{ [key: string]: string }>({});
  const [intervalMinutes, setIntervalMinutes] = useState<number>(1);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isServiceUnavailable, setIsServiceUnavailable] = useState(false);

  useEffect(() => {
    async function fetchCategoryTrees() {
      const trees = await getAllCategoryTrees();
      setCategoryTrees(trees);
    }
    fetchCategoryTrees();
  }, []);

  useEffect(() => {
    async function fetchModels() {
      try {
        const models = await listModels(selectedEndpoint);
        setModels(models || []);
        const validModel = models?.find(m => m.modelName === selectedModel) || models?.[0];
        if (validModel) {
          setSelectedModel(validModel.terminalModel ? validModel.terminalModel : validModel.modelName);
          setSelectedDisplayModel(validModel.modelName);
        }
      } catch (error) {
        console.error('Error fetching models:', error);
        setModels([]);
      }
    }
    fetchModels();
  }, [selectedEndpoint]);

  useEffect(() => {
    const filtered = models.filter((model) =>
      model.modelName.toLowerCase().includes(searchQuery.toLowerCase())
    );
    setFilteredModels(filtered);
  }, [searchQuery, models]);

  useEffect(() => {
    if (!selectedModel) return;  // 如果没有选中的模型，不获取数据

    async function fetchData() {
      setIsLoading(true);
      setError(null);
      setIsServiceUnavailable(false);
      try {
        const response = await fetch('/api/metrics?' + new URLSearchParams({
          model: selectedModel,
          endpoint: selectedEndpoint,
          start: format(startDate, "yyyyMMddHHmm"),
          end: format(endDate, "yyyyMMddHHmm")
        }));
        const data = await response.json();

        if (!response.ok) {
          if (data.error === '功能暂未开放') {
            setIsServiceUnavailable(true);
            setError(data.error);
            setCurrentData([]);
            return;
          } else {
            throw new Error('Failed to fetch metrics data');
          }
        }

        setCurrentData(data);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to fetch metrics data');
        console.error('Error fetching metrics:', err);
      } finally {
        setIsLoading(false);
      }
    }
    fetchData();
  }, [selectedModel, selectedEndpoint, startDate, endDate]);

  useEffect(() => {
    const uniqueChannels = getUniqueChannels(currentData || []);
    setChannels(uniqueChannels);
    setSelectedChannels([]); // 默认不选择任何渠道
    setChannelColors(getChannelColors(uniqueChannels || []));
  }, [currentData]);

  const handleDateRangeChange = (newStartDate: Date, newEndDate: Date) => {
    // 确保结束时间不早于开始时间
    if (newEndDate < newStartDate) {
      return;
    }

    // 确保时间范围不超过30天
    const thirtyDaysAgo = subDays(new Date(), 30);
    if (newStartDate < thirtyDaysAgo) {
      return;
    }

    // 确保结束时间不晚于当前时间
    const now = new Date();
    if (newEndDate > now) {
      newEndDate = now;
    }

    // 计算新的时间范围（分钟）
    const minutesDiff = (newEndDate.getTime() - newStartDate.getTime()) / (1000 * 60);

    // 检查时间范围/时间间隔是否大于60
    if (minutesDiff / intervalMinutes > 60 && intervalMinutes < 720) {
      // 如果大于60，调整时间间隔为合法的最小值
      const newIntervalMinutes = Math.ceil(minutesDiff / 60);
      setIntervalMinutes(newIntervalMinutes);
    }

    setStartDate(newStartDate);
    setEndDate(newEndDate);
  };

  // 时间间隔选项
  const intervalOptions = [
    { value: 1, label: '1分钟' },
    { value: 5, label: '5分钟' },
    { value: 15, label: '15分钟' },
    { value: 30, label: '30分钟' },
    { value: 60, label: '1小时' },
    { value: 180, label: '3小时' },
    { value: 360, label: '6小时' },
    { value: 720, label: '12小时' },
    { value: 1440, label: '1天' },
  ];

  // 处理时间间隔变化
  const handleIntervalChange = (newInterval: number) => {
    const minutesDiff = (endDate.getTime() - startDate.getTime()) / (1000 * 60);

    // 如果新的时间间隔会导致时间范围/时间间隔大于60，且不是12小时或1天，则不允许更改
    if (minutesDiff / newInterval > 60 && newInterval < 720) {
      return;
    }

    setIntervalMinutes(newInterval);
  };

  const metrics: any = {
    completed: transformData(currentData || [], 'completed'),
    ttlt: transformData(currentData || [], 'ttlt'),
    ttft: transformData(currentData || [], 'ttft'),
    errors: transformData(currentData || [], 'errors'),
    request_too_many: transformData(currentData || [], 'request_too_many'),
    output_token: transformData(currentData || [], 'output_token'),
    input_token: transformData(currentData || [], 'input_token'),
    status: transformData(currentData || [], 'status'),
  };

  return (
    <div className="min-h-screen bg-white dark:bg-white">
      <ClientHeader title="能力点监控" />
      <div className="flex">
        <Sidebar
          categoryTrees={categoryTrees}
          onEndpointSelect={setSelectedEndpoint}
          defaultEndpoint={endpointParam || '/v1/chat/completions'}
        />
        <main className="flex-1">
          <div className="p-6">
            {isServiceUnavailable ? (
              <div className="bg-blue-50 border-l-4 border-blue-500 p-4 mb-4">
                <div className="flex">
                  <div className="flex-shrink-0">
                    <svg className="h-5 w-5 text-blue-500" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
                      <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2h-1V9z" clipRule="evenodd" />
                    </svg>
                  </div>
                  <div className="ml-3">
                    <p className="text-sm text-blue-700">
                      {error}
                    </p>
                  </div>
                </div>
              </div>
            ) : error ? (
              <div className="bg-red-50 border-l-4 border-red-500 p-4 mb-4">
                <div className="flex">
                  <div className="flex-shrink-0">
                    <svg className="h-5 w-5 text-red-500" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
                      <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
                    </svg>
                  </div>
                  <div className="ml-3">
                    <p className="text-sm text-red-700">
                      {error}
                    </p>
                  </div>
                </div>
              </div>
            ) : null}
            <div className="bg-white p-6 rounded-lg shadow-sm space-y-4">
              <div className="flex flex-col space-y-4">
                <div className="space-y-2">
                  <label className="text-sm font-medium text-gray-700">模型:</label>
                  <ModelSelect
                    value={selectedDisplayModel}
                    onChange={(value) => {
                      const model = models.find(m => m.modelName === value);
                      setSelectedModel(model?.terminalModel || model?.modelName || value);
                      setSelectedDisplayModel(model?.modelName || value);
                    }}
                    models={models.map(m => m.modelName || '')}
                    className="w-full"
                  />
                </div>

                <div className="flex items-center space-x-4">
                  <span className="text-sm font-medium text-gray-700">时间范围:</span>
                  <DateTimeRangePicker
                    startDate={startDate}
                    endDate={endDate}
                    onChange={handleDateRangeChange}
                    maxDate={new Date()}
                    minDate={subDays(new Date(), 30)}
                  />
                </div>

                <div className="flex items-center space-x-4">
                  <span className="text-sm font-medium text-gray-700">时间间隔:</span>
                  <select
                    className="bg-white border border-gray-300 rounded p-2"
                    value={intervalMinutes}
                    onChange={(e) => handleIntervalChange(Number(e.target.value))}
                  >
                    {intervalOptions
                      .filter(option => {
                        const minutesDiff = (endDate.getTime() - startDate.getTime()) / (1000 * 60);
                        // 如果时间间隔小于12小时，只显示不会导致时间范围/时间间隔大于60的选项
                        // 12小时和1天的选项始终显示
                        return (minutesDiff / option.value <= 60) || option.value >= 720;
                      })
                      .map(option => (
                        <option key={option.value} value={option.value}>
                          {option.label}
                        </option>
                      ))}
                  </select>
                </div>

                <div className="flex flex-col space-y-2">
                  <span className="text-sm font-medium text-gray-700">渠道:</span>
                  <div className="flex flex-wrap gap-2">
                    {channels.map(channel => (
                      <div
                        key={channel}
                        className={`flex items-center space-x-2 p-2 rounded-md cursor-pointer ${
                          selectedChannels.includes(channel) ? 'bg-gray-100' : ''
                        }`}
                        onClick={() => {
                          setSelectedChannels(prev =>
                            prev.includes(channel)
                              ? prev.filter(c => c !== channel)
                              : [...prev, channel]
                          );
                        }}
                      >
                        <div
                          className="w-3 h-3 rounded-full"
                          style={{ backgroundColor: channelColors[channel] }}
                        />
                        <span className="text-sm text-gray-600">{channel}</span>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mt-6">
            <Suspense fallback={<div className="bg-white p-4 rounded-lg shadow-sm">Loading chart...</div>}>
                <MetricsLineChart
                  title="渠道状态（0:可用 1: 不可用）"
                  data={metrics.status}
                  channels={selectedChannels.length > 0 ? selectedChannels : channels}
                  channelColors={channelColors}
                  valueFormatter={(value: number) => value.toFixed(2)}
                  aggregationType="average"
                  intervalMinutes={intervalMinutes}
                />
              </Suspense>
              <Suspense fallback={<div className="bg-white p-4 rounded-lg shadow-sm">Loading chart...</div>}>
                <MetricsLineChart
                  title="请求总数"
                  data={metrics.completed}
                  channels={selectedChannels.length > 0 ? selectedChannels : channels}
                  channelColors={channelColors}
                  valueFormatter={(value: number) => Math.round(value).toString()}
                  aggregationType="sum"
                  intervalMinutes={intervalMinutes}
                />
              </Suspense>
              <Suspense fallback={<div className="bg-white p-4 rounded-lg shadow-sm">Loading chart...</div>}>
                <MetricsLineChart
                  title="异常请求数（httpcode >= 500）"
                  data={metrics.errors}
                  channels={selectedChannels.length > 0 ? selectedChannels : channels}
                  channelColors={channelColors}
                  valueFormatter={(value: number) => Math.round(value).toString()}
                  aggregationType="sum"
                  intervalMinutes={intervalMinutes}
                />
              </Suspense>
              <Suspense fallback={<div className="bg-white p-4 rounded-lg shadow-sm">Loading chart...</div>}>
                <MetricsLineChart
                    title="限流请求数（httpcode = 429）"
                    data={metrics.request_too_many}
                    channels={selectedChannels.length > 0 ? selectedChannels : channels}
                    channelColors={channelColors}
                    valueFormatter={(value: number) => Math.round(value).toString()}
                    aggregationType="sum"
                    intervalMinutes={intervalMinutes}
                />
              </Suspense>
              <Suspense fallback={<div className="bg-white p-4 rounded-lg shadow-sm">Loading chart...</div>}>
                <MetricsLineChart
                  title="每分钟输出token"
                  data={metrics.output_token}
                  channels={selectedChannels.length > 0 ? selectedChannels : channels}
                  channelColors={channelColors}
                  valueFormatter={(value: number) => Math.round(value).toString()}
                  aggregationType="average"
                  intervalMinutes={intervalMinutes}
                />
              </Suspense>
              <Suspense fallback={<div className="bg-white p-4 rounded-lg shadow-sm">Loading chart...</div>}>
                <MetricsLineChart
                  title="每分钟输入token"
                  data={metrics.input_token}
                  channels={selectedChannels.length > 0 ? selectedChannels : channels}
                  channelColors={channelColors}
                  valueFormatter={(value: number) => Math.round(value).toString()}
                  aggregationType="average"
                  intervalMinutes={intervalMinutes}
                />
              </Suspense>
              <Suspense fallback={<div className="bg-white p-4 rounded-lg shadow-sm">Loading chart...</div>}>
                <MetricsLineChart
                    title="首包响应时间 (ms)"
                    data={metrics.ttft}
                    channels={selectedChannels.length > 0 ? selectedChannels : channels}
                    channelColors={channelColors}
                    valueFormatter={(value: number) => `${Math.round(value)}ms`}
                    aggregationType="average"
                    intervalMinutes={intervalMinutes}
                />
              </Suspense>
              <Suspense fallback={<div className="bg-white p-4 rounded-lg shadow-sm">Loading chart...</div>}>
                <MetricsLineChart
                  title="响应时间 (s)"
                  data={metrics.ttlt}
                  channels={selectedChannels.length > 0 ? selectedChannels : channels}
                  channelColors={channelColors}
                  valueFormatter={(value: number) => `${Math.round(value)}s`}
                  aggregationType="average"
                  intervalMinutes={intervalMinutes}
                />
              </Suspense>
            </div>
          </div>
        </main>
      </div>
    </div>
  );
}

export default function MonitorPage(props: { params: { model: string } }) {
  return <MonitorPageContent {...props} />;
}
