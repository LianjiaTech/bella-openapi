'use client'

import { useState, useEffect } from 'react'
import { DateTimeRangePicker } from "@/components/ui/date-time-range-picker"
import { ModelSelect } from "@/components/ui/model-select"
import { EndpointSelect } from "@/components/ui/endpoint-select"
import { listModels, listEndpoints } from '@/lib/api/meta'
import type { Endpoint } from '@/lib/types/openapi'
import type { Model } from '@/lib/types/openapi'
import { ClientHeader } from "@/components/user/client-header"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { LogsSidebar } from '@/components/logs/sidebar'
import { agent_url } from '@/config'

const LogsPage = () => {
  const [startDate, setStartDate] = useState<Date>(() => {
    const date = new Date()
    date.setHours(date.getHours() - 1)
    return date
  })
  const [endDate, setEndDate] = useState<Date>(new Date())
  const [akCode, setAkCode] = useState('')
  const [httpCode, setHttpCode] = useState('')
  const [model, setModel] = useState('')
  const [endpoint, setEndpoint] = useState('')
  const [endpoints, setEndpoints] = useState<Endpoint[]>([])
  const [filteredEndpoints, setFilteredEndpoints] = useState<Endpoint[]>([])
  const [endpointSearchQuery, setEndpointSearchQuery] = useState('')
  const [models, setModels] = useState<Model[]>([])
  const [filteredModels, setFilteredModels] = useState<Model[]>([])
  const [modelSearchQuery, setModelSearchQuery] = useState('')
  const [logs, setLogs] = useState<any[]>([])
  const [loading, setLoading] = useState(false)
  const [isAkCodeError, setIsAkCodeError] = useState(false)
  const [isError, setIsError] = useState(false)
  const [errorMessage, setErrorMessage] = useState('')
  const [currentPage, setCurrentPage] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [totalLogs, setTotalLogs] = useState(0)
  const [expandedRequests, setExpandedRequests] = useState<{ [key: string]: boolean }>({})
  const [expandedResponses, setExpandedResponses] = useState<{ [key: string]: boolean }>({})
  const [requestId, setRequestId] = useState('')
  const [showConsultDialog, setShowConsultDialog] = useState(false)
  const [showAkCodeDialog, setShowAkCodeDialog] = useState(false)
  const [limit, setLimit] = useState(100)
  const [searchType, setSearchType] = useState<'akCode' | 'requestId' | 'bellaTraceId'>('akCode')
  const [bellaTraceId, setBellaTraceId] = useState('')

  const pageSizeOptions = [10, 20, 50, 100]
  const totalPages = Math.ceil(totalLogs / pageSize)
  const startIndex = (currentPage - 1) * pageSize
  const endIndex = startIndex + pageSize
  const currentLogs = logs.slice(startIndex, endIndex)

  useEffect(() => {
    setCurrentPage(1)
  }, [pageSize])

  useEffect(() => {
    async function fetchEndpoints() {
      try {
        const response = await listEndpoints('active')
        setEndpoints(response)
        setFilteredEndpoints(response)
      } catch (error) {
        console.error('Error fetching endpoints:', error)
        setEndpoints([])
        setFilteredEndpoints([])
      }
    }
    fetchEndpoints()
  }, [])

  useEffect(() => {
    async function fetchModels() {
      try {
        if (endpoint) {
          const response = await listModels(endpoint)
          setModels(response || [])
          setFilteredModels(response || [])
        } else {
          setModels([])
          setFilteredModels([])
        }
      } catch (error) {
        console.error('Error fetching models:', error)
        setModels([])
        setFilteredModels([])
      }
    }
    fetchModels()
  }, [endpoint])

  useEffect(() => {
    const filtered = endpoints.filter((e) =>
      e.endpoint.toLowerCase().includes(endpointSearchQuery.toLowerCase())
    )
    setFilteredEndpoints(filtered)
  }, [endpointSearchQuery, endpoints])

  useEffect(() => {
    const filtered = models.filter((m) =>
      m.modelName?.toLowerCase().includes(modelSearchQuery.toLowerCase())
    )
    setFilteredModels(filtered)
  }, [modelSearchQuery, models])

  const handleSearch = async () => {
    if (!akCode.trim() && !requestId.trim() && !bellaTraceId.trim()) {
      setIsAkCodeError(true)
      return
    }
    setIsAkCodeError(false)
    setIsError(false)
    setErrorMessage('')

    setLoading(true)
    try {
      let queryParts = []

      if (httpCode) {
        queryParts.push(`data_info_msg_response: (\"\\\"httpCode\\\"\\:${httpCode}\")`)
      }

      if (requestId) {
        queryParts.push(`data_info_msg_requestId:\"${requestId}\"`)
      } else if (akCode) {
        queryParts.push(`data_info_msg_akCode : \"${akCode}\"`)
      } else if (bellaTraceId) {
        queryParts.push(`data_info_msg_bellaTraceId:\"${bellaTraceId}\"`)
      }

      if (model) {
        queryParts.push(`data_info_msg_model:\"${model}\"`)
      }

      if (endpoint) {
        queryParts.push(`data_info_msg_endpoint:\"${endpoint}\"`)
      }

      const queryString = queryParts.length > 0 ? queryParts.join(' AND ') : '*'

      const response = await fetch('/api/logs', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          startTime: startDate.getTime(),
          endTime: endDate.getTime(),
          query: queryString,
          limit,
        }),
      })

      const data = await response.json()

      if (!response.ok) {
        if (data.error === '功能暂未开放') {
          setIsError(true)
          setErrorMessage(data.error)
          setLogs([])
          setTotalLogs(0)
          return
        } else {
          throw new Error('Failed to fetch logs')
        }
      }
      const logs = data.data || []
      setLogs(logs)
      setTotalLogs(logs.length)
      setCurrentPage(1) // 重置到第一页
    } catch (error) {
      console.error('Failed to fetch logs:', error)
      setIsError(true)
      setErrorMessage('获取日志失败，请检查配置或稍后重试')
      setLogs([])
      setTotalLogs(0)
    } finally {
      setLoading(false)
    }
  }

  const toggleRequest = (index: number) => {
    setExpandedRequests(prev => ({
      ...prev,
      [index]: !prev[index]
    }))
  }

  const toggleResponse = (index: number) => {
    setExpandedResponses(prev => ({
      ...prev,
      [index]: !prev[index]
    }))
  }

  const handlePageInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const value = parseInt(e.target.value)
    if (!isNaN(value) && value >= 1 && value <= totalPages) {
      setCurrentPage(value)
    }
  }

  const getDisplayedLines = (text: string, expanded: boolean) => {
    // 检查是否有换行符
    const lines = text.split('\n')
    const hasNewlines = lines.length > 1

    // 检查内容长度
    const isLongContent = text.length > 100

    // 如果已展开或者内容既没有多行也不长，直接返回全部内容
    if (expanded || (!hasNewlines && !isLongContent)) {
      return {
        text,
        hasMore: false
      }
    }

    // 否则返回截断的内容
    let truncatedText = ''
    if (hasNewlines) {
      truncatedText = lines.slice(0, 3).join('\n')
    } else {
      truncatedText = text.substring(0, 100) // 只显示前100个字符
    }

    return {
      text: truncatedText,
      hasMore: true
    }
  }

  return (
    <div className="min-h-screen bg-white dark:bg-white">
      <ClientHeader title="日志查询" />
      <div className="flex">
        <LogsSidebar />
        <div className="flex-1">
          <div className="container mx-auto py-8 px-4">
            <div className="bg-white p-6 rounded-lg shadow-sm mb-6">
              <div className="grid grid-cols-1 gap-4">
                <div className="space-y-2">
                  <label className="text-sm font-medium text-gray-700">时间范围</label>
                  <DateTimeRangePicker
                    startDate={startDate}
                    endDate={endDate}
                    onChange={(start, end) => {
                      setStartDate(start)
                      setEndDate(end)
                    }}
                  />
                </div>

                <div className="space-y-2">
                  <div className="flex items-center gap-4">
                    <label className="text-sm font-medium text-gray-700">
                      查询方式 <span className="text-red-500">*</span>
                    </label>
                    {searchType === 'akCode' && (
                      <button
                        onClick={() => setShowAkCodeDialog(true)}
                        className="text-sm text-blue-600 hover:text-blue-800"
                      >
                        获取 AK Code
                      </button>
                    )}
                  </div>
                  <div className="flex gap-2">
                    <Select
                      value={searchType}
                      onValueChange={(value: 'akCode' | 'requestId' | 'bellaTraceId') => {
                        setSearchType(value)
                        // Clear all fields when switching
                        setAkCode('')
                        setRequestId('')
                        setBellaTraceId('')
                        setIsAkCodeError(false)
                      }}
                    >
                      <SelectTrigger className="w-[120px] p-2 border border-gray-300 rounded bg-white focus:outline-none focus:ring-2 focus:ring-blue-500">
                        <SelectValue placeholder="选择查询方式" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="akCode">AK Code</SelectItem>
                        <SelectItem value="requestId">Request ID</SelectItem>
                        <SelectItem value="bellaTraceId">Bella TraceID</SelectItem>
                      </SelectContent>
                    </Select>

                    {searchType === 'akCode' ? (
                      <div className="flex-1">
                        <input
                          type="text"
                          className={`w-full p-2 border rounded bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 ${
                            isAkCodeError ? 'border-red-500 focus:ring-red-500' : 'border-gray-300'
                          }`}
                          placeholder="请输入 AK Code"
                          value={akCode}
                          onChange={(e) => {
                            setAkCode(e.target.value)
                            if (isAkCodeError) {
                              setIsAkCodeError(false)
                            }
                          }}
                        />
                      </div>
                    ) : searchType === 'requestId' ? (
                      <div className="flex-1">
                        <input
                          type="text"
                          className={`w-full p-2 border rounded bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 ${
                            isAkCodeError ? 'border-red-500 focus:ring-red-500' : 'border-gray-300'
                          }`}
                          placeholder="请输入 Request ID"
                          value={requestId}
                          onChange={(e) => {
                            setRequestId(e.target.value)
                            if (isAkCodeError) {
                              setIsAkCodeError(false)
                            }
                          }}
                        />
                      </div>
                    ) : (
                      <div className="flex-1">
                        <input
                          type="text"
                          className={`w-full p-2 border rounded bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 ${
                            isAkCodeError ? 'border-red-500 focus:ring-red-500' : 'border-gray-300'
                          }`}
                          placeholder="请输入 Bella TraceID"
                          value={bellaTraceId}
                          onChange={(e) => {
                            setBellaTraceId(e.target.value)
                            if (isAkCodeError) {
                              setIsAkCodeError(false)
                            }
                          }}
                        />
                      </div>
                    )}
                  </div>

                  {isAkCodeError && (
                    <p className="text-sm text-red-500 mt-1">
                      请输入{searchType === 'akCode' ? 'AK Code' : searchType === 'requestId' ? 'Request ID' : 'Bella TraceID'}
                    </p>
                  )}
                </div>

                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-2">
                    <label className="text-sm font-medium text-gray-700">HTTP 异常状态码</label>
                    <input
                      type="text"
                      className="w-full p-2 border border-gray-300 rounded bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
                      placeholder="请输入异常状态码"
                      value={httpCode}
                      onChange={(e) => setHttpCode(e.target.value)}
                    />
                  </div>

                  <div className="space-y-2">
                    <label className="text-sm font-medium text-gray-700">能力点</label>
                    <EndpointSelect
                      value={endpoint}
                      onChange={(value) => setEndpoint(value || '')}
                      endpoints={endpoints.map(e => e.endpoint)}
                      onSearch={setEndpointSearchQuery}
                      searchValue={endpointSearchQuery}
                      filteredEndpoints={filteredEndpoints.map(e => e.endpoint)}
                      allowClear={true}
                    />
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-2">
                    <label className="text-sm font-medium text-gray-700">模型</label>
                    <ModelSelect
                      value={model}
                      onChange={(value) => setModel(value || '')}
                      models={models.map(m => m.modelName || '')}
                      className="w-full"
                      allowClear={true}
                    />
                  </div>

                  <div className="space-y-2">
                    <label className="text-sm font-medium text-gray-700">返回条数</label>
                    <input
                      type="number"
                      className="w-full p-2 border border-gray-300 rounded bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
                      placeholder="默认 100 条"
                      value={limit}
                      min={1}
                      onChange={(e) => setLimit(parseInt(e.target.value) || 100)}
                    />
                  </div>
                </div>

                <div className="space-y-2 mt-8">
                  <button
                    onClick={handleSearch}
                    disabled={loading}
                    className="w-full bg-gray-800 text-white px-4 py-2 rounded hover:bg-gray-700 focus:outline-none focus:ring-2 focus:ring-gray-500"
                  >
                    {loading ? '搜索中...' : '搜索'}
                  </button>
                  <div className="flex justify-center mt-2">
                    <button
                      onClick={() => setShowConsultDialog(true)}
                      className="text-sm text-blue-600 hover:text-blue-800"
                    >
                      异常响应咨询
                    </button>
                  </div>
                </div>

                {showConsultDialog && (
                  <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
                    <div className="bg-white p-6 rounded-lg shadow-lg max-w-md w-full">
                      <h3 className="text-lg font-medium mb-4">异常响应咨询</h3>
                      <p className="text-gray-600 mb-4">
                        请复制异常响应内容，前往以下链接进行咨询：
                      </p>
                      <a
                        href={decodeURIComponent(agent_url || '')}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="text-blue-600 hover:text-blue-800 underline"
                      >
                        点击跳转到咨询页面
                      </a>
                      <div className="mt-6 flex justify-end">
                        <button
                          onClick={() => setShowConsultDialog(false)}
                          className="bg-gray-200 text-gray-800 px-4 py-2 rounded hover:bg-gray-300 focus:outline-none focus:ring-2 focus:ring-gray-400"
                        >
                          关闭
                        </button>
                      </div>
                    </div>
                  </div>
                )}
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
                {isError && (
                  <div className="mt-4 p-4 border rounded-md bg-blue-50 border-blue-200">
                    <p className="text-sm text-blue-600 font-medium">
                      {errorMessage || '获取日志失败，请检查配置或稍后重试'}
                    </p>
                  </div>
                )}
                {logs.length > 0 ? (
                  <>
                    <div className="bg-white p-4 rounded-lg shadow-sm mb-4 flex justify-between items-center">
                      <div className="text-sm text-gray-600">
                        总共 {totalLogs} 条记录，第 {currentPage} / {totalPages} 页
                      </div>
                      <div className="flex items-center space-x-4">
                        <div className="flex items-center space-x-2">
                          <span className="text-sm text-gray-600">每页显示：</span>
                          <Select
                            value={pageSize.toString()}
                            onValueChange={(value) => setPageSize(Number(value))}
                          >
                            <SelectTrigger className="w-[100px] p-2 border border-gray-300 rounded bg-white focus:outline-none focus:ring-2 focus:ring-blue-500">
                              <SelectValue placeholder="选择条数" />
                            </SelectTrigger>
                            <SelectContent>
                              {pageSizeOptions.map((size) => (
                                <SelectItem key={size} value={size.toString()}>
                                  {size} 条
                                </SelectItem>
                              ))}
                            </SelectContent>
                          </Select>
                        </div>
                        <div className="flex items-center space-x-2">
                          <button
                            onClick={() => setCurrentPage(prev => Math.max(prev - 1, 1))}
                            disabled={currentPage === 1}
                            className="px-3 py-1 border rounded hover:bg-gray-50 disabled:opacity-50"
                          >
                            上一页
                          </button>
                          <input
                            type="number"
                            min={1}
                            max={totalPages}
                            value={currentPage}
                            onChange={handlePageInputChange}
                            className="w-16 px-2 py-1 border rounded text-center bg-white text-black"
                          />
                          <span className="text-sm text-gray-600">
                            / {totalPages}
                          </span>
                          <button
                            onClick={() => setCurrentPage(prev => Math.min(prev + 1, totalPages))}
                            disabled={currentPage === totalPages}
                            className="px-3 py-1 border rounded hover:bg-gray-50 disabled:opacity-50"
                          >
                            下一页
                          </button>
                        </div>
                      </div>
                    </div>

                    <div className="space-y-4">
                      {currentLogs.map((log, index) => (
                        <div
                          key={index}
                          className={`p-6 rounded-lg shadow-sm ${
                            index % 2 === 0 ? 'bg-white' : 'bg-gray-100'
                          }`}
                        >
                          <div className="grid grid-cols-2 gap-4 mb-4">
                            <div>
                              <span className="font-medium text-gray-700">请求 ID：</span>{" "}
                              <span className="text-gray-900">{log.data_info_msg_requestId}</span>
                            </div>
                            <div>
                              <span className="font-medium text-gray-700">时间：</span>{" "}
                              <span className="text-gray-900">
                                {new Date(parseInt(log.data_info_msg_requestTime) * 1000).toLocaleString()}
                              </span>
                            </div>
                            <div>
                              <span className="font-medium text-gray-700">AK Code：</span>{" "}
                              <span className="text-gray-900">{log.data_info_msg_akCode}</span>
                            </div>
                            <div>
                              <span className="font-medium text-gray-700">Bella TraceID：</span>{" "}
                              <span className="text-gray-900">{log.data_info_msg_bellaTraceId}</span>
                            </div>
                            <div>
                              <span className="font-medium text-gray-700">模型：</span>{" "}
                              <span className="text-gray-900">{log.data_info_msg_model}</span>
                            </div>
                            <div>
                              <span className="font-medium text-gray-700">能力点：</span>{" "}
                              <span className="text-gray-900">{log.data_info_msg_endpoint}</span>
                            </div>
                            <div>
                              <span className="font-medium text-gray-700">用户：</span>{" "}
                              <span className="text-gray-900">{log.data_info_msg_user}</span>
                            </div>
                          </div>

                          <div className="mb-4">
                            <span className="font-medium text-gray-700">转发 URL：</span>
                            <div className="mt-1 text-gray-900 break-all">
                              {log.data_info_msg_forwardUrl}
                            </div>
                          </div>

                          <div>
                            <span className="font-medium text-gray-700">请求：</span>
                            <pre className="mt-1 p-2 bg-gray-50 border rounded text-sm overflow-x-auto text-gray-900">
                              {getDisplayedLines(log.data_info_msg_request, expandedRequests[index]).text}
                            </pre>
                            {(log.data_info_msg_request.split('\n').length > 3 || log.data_info_msg_request.length > 200) && (
                              <button
                                onClick={() => toggleRequest(index)}
                                className="mt-1 text-sm text-blue-600 hover:text-blue-800"
                              >
                                {expandedRequests[index] ? '收起' : '展开更多'}
                              </button>
                            )}
                          </div>

                          {log.data_info_msg_response && (
                            <div className="mt-4">
                              <span className="font-medium text-gray-700">响应：</span>
                              <pre className="mt-1 p-2 bg-gray-50 border rounded text-sm overflow-x-auto text-gray-900">
                                {getDisplayedLines(log.data_info_msg_response, expandedResponses[index]).text}
                              </pre>
                              {(log.data_info_msg_response.split('\n').length > 3 || log.data_info_msg_response.length > 200) && (
                                <button
                                  onClick={() => toggleResponse(index)}
                                  className="mt-1 text-sm text-blue-600 hover:text-blue-800"
                                >
                                  {expandedResponses[index] ? '收起' : '展开更多'}
                                </button>
                              )}
                            </div>
                          )}

                          {log.data_info_msg_metrics && (
                              <div className="mb-4">
                                <span className="font-medium text-gray-700">请求指标：</span>
                                <div className="mt-1 text-gray-900 break-all">
                                  {log.data_info_msg_metrics}
                                </div>
                              </div>
                          )}
                        </div>
                      ))}
                    </div>
                  </>
                ) : (
                    <div className="mt-8 text-center p-8 bg-white rounded-lg shadow-sm">
                      <p className="text-gray-600">没有日志，请确认查询条件</p>
                    </div>
                )}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

export default LogsPage
