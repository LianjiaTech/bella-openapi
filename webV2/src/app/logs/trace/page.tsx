'use client'

import { useState, useEffect, useCallback, useRef } from 'react'
import { DateTimeRangePicker } from "@/components/ui/date-time-range-picker"
import { ClientHeader } from "@/components/user/client-header"
import { LogsSidebar } from '@/components/logs/sidebar'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"

interface ServiceConfig {
  serviceId: string;
  index: string;
  fields: string[];
}


export default function TraceLogsPage() {
  const [currentStartDate, setCurrentStartDate] = useState<Date>(() => {
    const date = new Date()
    date.setHours(date.getHours() - 1)
    return date
  })
  const [currentEndDate, setCurrentEndDate] = useState<Date>(new Date())
  const getTimeRangeRef = useRef<(() => { startDate: Date; endDate: Date }) | null>(null)
  const [bellaTraceId, setBellaTraceId] = useState('')
  const [akCode, setAkCode] = useState('')
  const [searchType, setSearchType] = useState<'bellaTraceId' | 'akCode'>('bellaTraceId')
  const [services, setServices] = useState<ServiceConfig[]>([]);
  const [logs, setLogs] = useState<{ [serviceId: string]: any[] }>({})
  const [selectedServiceId, setSelectedServiceId] = useState<string>('')
  const [currentPages, setCurrentPages] = useState<{ [key: string]: number }>({});
  const [loading, setLoading] = useState(false)
  const [isError, setIsError] = useState(false)
  const [errorType, setErrorType] = useState<'validation' | 'api'>('validation')
  const [errorMessage, setErrorMessage] = useState('')
  const [showAkCodeDialog, setShowAkCodeDialog] = useState(false)
  const [expandedLogs, setExpandedLogs] = useState<{ [key: string]: boolean }>({});
  const PAGE_SIZE = 10;

  useEffect(() => {
    async function fetchServices() {
      try {
        const response = await fetch('/api/logs/trace/service');
        const data = await response.json();
        
        if (!response.ok) {
          if (data.error === '功能暂未开放') {
            setIsError(true);
            setErrorType('api');
            setErrorMessage(data.error);
          } else {
            throw new Error('Failed to fetch services');
          }
          return;
        }
        
        setServices(data || []);
      } catch (error) {
        console.error('Error fetching services:', error);
        setIsError(true);
        setErrorType('api');
        setErrorMessage('获取日志失败，请检查配置或稍后重试');
      }
    }
    fetchServices();
  }, []);

  useEffect(() => {
    // 重置分页当选中的服务改变时
    if (selectedServiceId) {
      setCurrentPages(prev => ({
        ...prev,
        [selectedServiceId]: 1
      }));
    }
  }, [selectedServiceId]);

  const handleTimeRangeChange = useCallback((getTimeRangeFunc: () => { startDate: Date; endDate: Date }, isRelative: boolean) => {
    const { startDate, endDate } = getTimeRangeFunc();
    setCurrentStartDate(startDate);
    setCurrentEndDate(endDate);
    getTimeRangeRef.current = getTimeRangeFunc;
  }, [])

  const handleSearch = async () => {
    if (!bellaTraceId && !akCode) {
      setIsError(true);
      setErrorType('validation');
      setErrorMessage(`请输入${searchType === 'bellaTraceId' ? 'BellaTraceID' : 'AKCode'}`);
      return;
    }

    setLoading(true);
    setIsError(false);
    setErrorType('validation');
    setErrorMessage('');
    try {
      const searchParams = new URLSearchParams({
        start: (getTimeRangeRef.current ? getTimeRangeRef.current().startDate : currentStartDate).getTime().toString(),
        end: (getTimeRangeRef.current ? getTimeRangeRef.current().endDate : currentEndDate).getTime().toString(),
      });

      if (searchType === 'bellaTraceId') {
        searchParams.append('traceId', bellaTraceId);
      } else {
        searchParams.append('akCode', akCode);
      }

      // 按顺序查询每个服务
      const results: { [serviceId: string]: any[] } = {};
      let isServiceUnavailable = false;
      let serviceUnavailableMessage = '';

      for (const service of services) {
        const serviceParams = new URLSearchParams(searchParams);
        serviceParams.append('serviceId', service.serviceId);

        const response = await fetch(`/api/logs/trace?${serviceParams.toString()}`);
        const data = await response.json();

        if (!response.ok) {
          if (data.error === '功能暂未开放') {
            isServiceUnavailable = true;
            serviceUnavailableMessage = data.error;
            break;
          } else {
            throw new Error('Failed to fetch logs');
          }
        }

        if (Array.isArray(data)) {
          results[service.serviceId] = data;
        }
      }

      if (isServiceUnavailable) {
        setIsError(true);
        setErrorType('api');
        setErrorMessage(serviceUnavailableMessage);
        setLogs({});
      } else {
        setLogs(results);

        // 自动选择第一个有日志的服务
        const serviceWithLogs = Object.entries(results).find(([_, logs]) => logs && logs.length > 0);
        if (serviceWithLogs) {
          setSelectedServiceId(serviceWithLogs[0]);
        }
      }
    } catch (error) {
      console.error('Error fetching logs:', error);
      setIsError(true);
      setErrorType('api');
      setErrorMessage('获取日志失败，请检查配置或稍后重试');
      // 清空日志数据
      setLogs({});
    } finally {
      setLoading(false);
    }
  };

  const toggleLogExpansion = (contentKey: string) => {
    setExpandedLogs(prev => ({
      ...prev,
      [contentKey]: !prev[contentKey]
    }));
  };

  const getCurrentPageLogs = (serviceId: string, logs: any[]) => {
    const currentPage = currentPages[serviceId] || 1;
    const start = (currentPage - 1) * PAGE_SIZE;
    const end = start + PAGE_SIZE;
    return logs.slice(start, end);
  };

  const handlePageChange = (serviceId: string, page: number) => {
    setCurrentPages(prev => ({
      ...prev,
      [serviceId]: page
    }));
  };

  const renderLogContent = (content: any) => {
    // 处理字符串类型
    if (typeof content === 'string') {
      // 检查内容长度
      const isLongContent = content.length > 100

      // 如果内容不长，直接显示全部
      if (!isLongContent) {
        return <div className="whitespace-pre-wrap break-all">{content}</div>
      }

      // 对于长内容，创建一个唯一的键
      const contentKey = `str_${content.substring(0, 100)}`

      // 根据展开状态决定显示内容
      let displayedContent = content
      if (!expandedLogs[contentKey]) {
        displayedContent = content.substring(0, 100) + '...'
      }

      return (
        <div>
          <div className="whitespace-pre-wrap break-all">{displayedContent}</div>
          <button
            onClick={() => toggleLogExpansion(contentKey)}
            className="mt-1 text-gray-500 hover:text-gray-700 text-sm"
          >
            {expandedLogs[contentKey] ? '收起' : '展开'}
          </button>
        </div>
      )
    }

    // 处理对象类型
    if (typeof content === 'object' && content !== null) {
      return <pre className="whitespace-pre-wrap">{JSON.stringify(content, null, 2)}</pre>;
    }

    // 处理其他类型
    return content?.toString() || '';
  };

  return (
    <div className="min-h-screen bg-white dark:bg-white">
      <ClientHeader title="Bella 链路查询" />
      <div className="flex">
        <LogsSidebar />
        <div className="flex-1">
          <div className="container mx-auto py-8 px-4">
            <div className="bg-white rounded-lg shadow p-6">
              <div className="space-y-6">
                <div className="space-y-2">
                  <div className="flex items-center space-x-4">
                    <span className="text-sm font-medium text-gray-700">查询方式:</span>
                    <Select
                      value={searchType}
                      onValueChange={(value: 'bellaTraceId' | 'akCode') => {
                        setSearchType(value)
                        setBellaTraceId('')
                        setAkCode('')
                        setIsError(false)
                        setErrorType('validation')
                        setErrorMessage('')
                      }}
                    >
                      <SelectTrigger className="w-[180px]">
                        <SelectValue placeholder="选择查询类型" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="bellaTraceId">BellaTraceID</SelectItem>
                        <SelectItem value="akCode">AKCode</SelectItem>
                      </SelectContent>
                    </Select>
                    {searchType === 'akCode' && (
                      <button
                        onClick={() => setShowAkCodeDialog(true)}
                        className="text-sm text-gray-600 hover:text-gray-800"
                      >
                        获取 AK Code
                      </button>
                    )}
                  </div>

                  {searchType === 'bellaTraceId' ? (
                    <input
                      type="text"
                      value={bellaTraceId}
                      onChange={(e) => {
                        setBellaTraceId(e.target.value)
                        setIsError(false)
                        setErrorType('validation')
                        setErrorMessage('')
                      }}
                      className={`w-full p-2 border rounded bg-white text-gray-900 ${
                        isError ? 'border-red-500' : 'border-gray-300'
                      }`}
                      placeholder="请输入BellaTraceID"
                    />
                  ) : (
                    <input
                      type="text"
                      value={akCode}
                      onChange={(e) => {
                        setAkCode(e.target.value)
                        setIsError(false)
                        setErrorType('validation')
                        setErrorMessage('')
                      }}
                      className={`w-full p-2 border rounded bg-white text-gray-900 ${
                        isError ? 'border-red-500' : 'border-gray-300'
                      }`}
                      placeholder="请输入AKCode"
                    />
                  )}

                  {isError && errorType === 'api' && (
                    <div className="mt-4 p-4 border rounded-md bg-blue-50 border-blue-200">
                      <p className="text-sm text-blue-600 font-medium">
                        {errorMessage || '获取日志失败，请检查配置或稍后重试'}
                      </p>
                    </div>
                  )}

                  {isError && errorType === 'validation' && (
                    <div className="mt-4 p-4 border rounded-md bg-red-50 border-red-200">
                      <p className="text-sm text-red-600 font-medium">
                        {errorMessage}
                      </p>
                    </div>
                  )}
                </div>

                <div className="space-y-2">
                  <DateTimeRangePicker
                    onChange={handleTimeRangeChange}
                  />
                </div>

                <button
                  onClick={handleSearch}
                  disabled={loading}
                  className="w-full bg-black text-white px-4 py-2 rounded hover:bg-gray-800 disabled:bg-gray-400"
                >
                  {loading ? '查询中...' : '查询'}
                </button>
              </div>

              {/* 查询结果显示 */}
              {Object.keys(logs).length > 0 && (
                <div className="mt-6">
                  <div className="flex space-x-2 mb-4 flex-wrap gap-2">
                    {services
                      .filter(service => logs[service.serviceId]?.length > 0)
                      .map((service) => (
                        <button
                          key={service.serviceId}
                          onClick={() => setSelectedServiceId(service.serviceId)}
                          className={`px-3 py-1 rounded ${
                            selectedServiceId === service.serviceId
                              ? 'bg-black text-white'
                              : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                          }`}
                        >
                          {service.serviceId}
                          <span className="ml-2 bg-gray-200 text-gray-700 px-2 py-0.5 rounded-full text-xs">
                            {logs[service.serviceId].length}
                          </span>
                        </button>
                      ))}
                  </div>

                  {selectedServiceId && logs[selectedServiceId] && (
                    <div className="space-y-8">
                      {getCurrentPageLogs(selectedServiceId, logs[selectedServiceId]).map((log: any, index: number) => (
                        <div key={index} className={`p-6 rounded-lg shadow-[0_-4px_6px_-1px_rgba(0,0,0,0.1)] ${
                          index % 2 === 0 ? 'bg-white' : 'bg-gray-50'
                        }`}>
                          <div className="space-y-4">
                            {services.find(service => service.serviceId === selectedServiceId)?.fields.map(field => {
                              if (field in log) {
                                return (
                                  <div key={field} className="py-3 border-b border-gray-200 last:border-b-0">
                                    <div className="flex items-start">
                                      <div className="text-gray-500 font-medium min-w-[200px] mr-4">
                                        {field}
                                      </div>
                                      <div className="flex-1 text-gray-900">
                                        {renderLogContent(log[field])}
                                      </div>
                                    </div>
                                  </div>
                                );
                              }
                              return null;
                            })}
                          </div>
                        </div>
                      ))}

                      {/* 分页控制 */}
                      {logs[selectedServiceId].length > PAGE_SIZE && (
                        <div className="flex justify-center mt-8 space-x-2">
                          <button
                            onClick={() => handlePageChange(selectedServiceId, (currentPages[selectedServiceId] || 1) - 1)}
                            disabled={(currentPages[selectedServiceId] || 1) === 1}
                            className="px-4 py-2 rounded bg-white border border-gray-300 text-gray-700 hover:bg-gray-50 disabled:bg-gray-50 disabled:text-gray-400 disabled:border-gray-200"
                          >
                            上一页
                          </button>
                          <span className="px-4 py-2 text-gray-700 bg-white border border-gray-300 rounded">
                            第 {currentPages[selectedServiceId] || 1} 页 / 共 {Math.ceil(logs[selectedServiceId].length / PAGE_SIZE)} 页
                          </span>
                          <button
                            onClick={() => handlePageChange(selectedServiceId, (currentPages[selectedServiceId] || 1) + 1)}
                            disabled={(currentPages[selectedServiceId] || 1) >= Math.ceil(logs[selectedServiceId].length / PAGE_SIZE)}
                            className="px-4 py-2 rounded bg-white border border-gray-300 text-gray-700 hover:bg-gray-50 disabled:bg-gray-50 disabled:text-gray-400 disabled:border-gray-200"
                          >
                            下一页
                          </button>
                        </div>
                      )}
                    </div>
                  )}
                </div>
              )}
            </div>
          </div>
        </div>
      </div>

      {showAkCodeDialog && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white p-6 rounded-lg shadow-lg max-w-md w-full">
            <h3 className="text-lg font-medium mb-4">获取 AK Code</h3>
            <div className="text-gray-600 space-y-2 mb-4">
              <p>
                请前往 API Key 管理页面获取您的 AK Code：
              </p>
              <ol className="list-decimal list-inside space-y-1">
                <li>点击下方链接跳转到 API Key 管理页面</li>
                <li>找到您使用的 API Key（只有 API Key 拥有者才能看到）</li>
                <li>在 API Key 列表中，点击操作栏的复制按钮即可复制 AK Code</li>
              </ol>
            </div>
            <a
              href="/apikey"
              target="_blank"
              rel="noopener noreferrer"
              className="text-blue-600 hover:text-blue-800 underline"
            >
              点击跳转到 API Key 管理页面
            </a>
            <div className="mt-6 flex justify-end">
              <button
                onClick={() => setShowAkCodeDialog(false)}
                className="bg-gray-200 text-gray-800 px-4 py-2 rounded hover:bg-gray-300 focus:outline-none focus:ring-2 focus:ring-gray-400"
              >
                关闭
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
