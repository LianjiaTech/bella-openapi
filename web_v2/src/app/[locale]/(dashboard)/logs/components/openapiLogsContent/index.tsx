'use client'

import * as React from "react"
import { LogsFilter } from "./components/logsFilter"
import { LogsTable } from "./components/logsTable"
import { QueryType } from "../common/QueryTypeSelector/types"
import { QUERY_TYPE_TO_FIELD_MAP } from "../common/QueryTypeSelector/constants"
import { useDateRangeFilter } from "@/components/ui/date-range-picker"
import { dateToTimestamp } from "@/lib/utils/date"
import type { LogsApiResponse } from "@/lib/types/logs"
import { useLanguage } from "@/components/providers/language-provider"
import axios from "axios"

export function OpenapiLogsContent() {
  const { t } = useLanguage()

  // 查询类型和查询值状态管理
  const [queryType, setQueryType] = React.useState<QueryType>("AK Code")
  const [queryValue, setQueryValue] = React.useState<string>("")
  const [queryValueError, setQueryValueError] = React.useState<string>("")

  // 能力点代码状态管理
  const [endpointCode, setEndpointCode] = React.useState<string>("")

  // 模型名称状态管理
  const [modelName, setModelName] = React.useState<string>("")

  // HTTP 状态码状态管理
  const [httpCode, setHttpCode] = React.useState<string>("")

  // 查询条数限制状态管理
  const [limit, setLimit] = React.useState<number>(100)

  // 日期范围管理（使用公共 Hook）
  const { dateRange, handleDateRangeChange } = useDateRangeFilter()

  // 日志数据状态管理
  const [logsData, setLogsData] = React.useState<LogsApiResponse | null>(null)
  const [isLoading, setIsLoading] = React.useState<boolean>(false)
  const [error, setError] = React.useState<string>("")

  // 当能力点改变时，自动清空已选择的模型
  React.useEffect(() => {
    setModelName("")
  }, [endpointCode])

  // 当查询值改变时，清除错误状态
  React.useEffect(() => {
    if (queryValueError && queryValue.trim()) {
      setQueryValueError("")
    }
  }, [queryValue, queryValueError])

  // 搜索处理函数
  const handleSearch = React.useCallback(async () => {
    // 验证查询值是否为空
    if (!queryValue.trim()) {
      setQueryValueError(t("logs.filter.errors.enterQueryValue"))
      return
    }

    // 验证日期范围是否存在
    if (!dateRange?.from || !dateRange?.to) {
      setError(t("logs.filter.errors.selectDateRange"))
      return
    }

    // 清除错误状态
    setQueryValueError("")
    setError("")
    setIsLoading(true)

    try {
      // 转换为13位毫秒时间戳
      const startTime = dateToTimestamp(dateRange.from)
      const endTime = dateToTimestamp(dateRange.to)

      // 根据查询类型构建查询字符串
      const fieldName = QUERY_TYPE_TO_FIELD_MAP[queryType]
      let queryString = `${fieldName}:"${queryValue}"`

      // 添加可选的筛选条件
      const conditions: string[] = [queryString]

      // 添加 HTTP 状态码筛选
      if (httpCode && httpCode.trim()) {
        conditions.push(`data_info_msg_response: (\"\\\"httpCode\\\"\\: ${httpCode.trim()}\")`)
      }

      // 添加能力点筛选
      if (endpointCode && endpointCode.trim()) {
        conditions.push(`data_info_msg_endpoint:"${endpointCode.trim()}"`)
      }

      // 添加模型筛选
      if (modelName && modelName.trim()) {
        conditions.push(`data_info_msg_model:"${modelName.trim()}"`)
      }

      // 使用 AND 连接所有条件
      const finalQuery = conditions.join(' AND ')

      // 调用 API 获取日志数据
      const response = await axios.post(`/api/logs`, {
        startTime: startTime,
        endTime: endTime,
        limit: limit,
        query: finalQuery,
      })
      if(response.status === 200){
        setLogsData(response.data)
      } else {
        setError("查询日志失败")
        setLogsData(null)
      }

      // 保存日志数据
    } catch (err) {
      // 错误处理
      const errorMessage = err instanceof Error ? err.message : '查询日志失败'
      setError(errorMessage)
    } finally {
      setIsLoading(false)
    }
  }, [
    queryType,
    queryValue,
    httpCode,
    endpointCode,
    modelName,
    dateRange,
    limit,
    t,
  ])

  // 重置处理函数
  const handleReset = React.useCallback(() => {
    setQueryType("AK Code")
    setQueryValue("")
    setEndpointCode("")
    setModelName("")
    setHttpCode("")
    setLimit(100)

    // 重置为最近5分钟
    const end = new Date()
    const start = new Date()
    start.setMinutes(start.getMinutes() - 5)
    handleDateRangeChange({ from: start, to: end })

  }, [handleDateRangeChange])

  return (
    <div className="space-y-4">
      {/* 筛选条件模块 */}
      <LogsFilter
        queryType={queryType}
        queryValue={queryValue}
        queryValueError={queryValueError}
        onQueryTypeChange={setQueryType}
        onQueryValueChange={setQueryValue}
        endpointCode={endpointCode}
        onEndpointCodeChange={setEndpointCode}
        modelName={modelName}
        onModelNameChange={setModelName}
        httpCode={httpCode}
        onHttpCodeChange={setHttpCode}
        limit={limit}
        onLimitChange={setLimit}
        dateRange={dateRange}
        onDateRangeChange={handleDateRangeChange}
        onSearch={handleSearch}
        onReset={handleReset}
      />

      {/* 日志内容展示区域 */}
      <div className="space-y-4">
        {/* 错误提示 */}
        {error && !isLoading && (
          <div className="rounded-lg border border-red-300 bg-red-50 p-4 text-red-600">
            {error}
          </div>
        )}

        {/* 日志表格展示 */}
        {logsData && !isLoading && !error && (
          <LogsTable data={logsData.data} isLoading={isLoading} />
        )}

        {/* 无数据提示 */}
        {!logsData && !isLoading && !error && (
          <div className="rounded-lg border p-4 text-center text-muted-foreground bg-card">
            {t("logs.filter.noDataPrompt")}
          </div>
        )}

        {/* 加载状态 */}
        {isLoading && (
          <LogsTable data={[]} isLoading={true} />
        )}
      </div>
    </div>
  )
}
