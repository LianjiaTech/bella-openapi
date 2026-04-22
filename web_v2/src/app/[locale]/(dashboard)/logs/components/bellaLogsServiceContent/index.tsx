'use client'

import * as React from "react"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/common/tabs"
import { LogsFilterBella } from "./bellaLogsFilter"
import { QueryType } from "../common/QueryTypeSelector/types"
import { LogsTable as BellaLogsTable } from "./bellaLogsTable"
import { useDateRangeFilter } from "@/components/ui/date-range-picker"
import { useServiceData } from "./bellaLogsFilter/hooks/useServiceData"
import { useTabDataManager } from "../../hooks/useTabDataManager"
import { dateToTimestamp } from "@/lib/utils/date"
import { useLanguage } from "@/components/providers/language-provider"
import axios from "axios"

export function BellaLogsServiceContent() {
  const { t } = useLanguage()

  // Tab 数据管理
  const { getTabData, setTabData, isTabLoaded, setTabLoading, clearAllCache } = useTabDataManager()

  // 查询状态管理
  const [queryType, setQueryType] = React.useState<QueryType>("AK Code")
  const [queryValue, setQueryValue] = React.useState<string>("")
  const [queryValueError, setQueryValueError] = React.useState<string>("")

  // 日期范围管理
  const { dateRange, handleDateRangeChange } = useDateRangeFilter()

  // 服务列表数据
  const { fetchServiceData, serviceData } = useServiceData()
  const [selectedServiceId, setSelectedServiceId] = React.useState<string>("")

  // 使用 useRef 保存上一次的筛选条件，用于检测条件变化
  const prevFiltersRef = React.useRef<{
    queryType: QueryType
    queryValue: string
    dateRangeFrom: number
    dateRangeTo: number
  } | null>(null)

  // 组件挂载时加载服务列表数据
  React.useEffect(() => {
    fetchServiceData()
  }, [fetchServiceData])

  // 当 serviceData 加载完成后，默认选中第一个服务
  React.useEffect(() => {
    if (serviceData.length > 0 && !selectedServiceId) {
      setSelectedServiceId(serviceData[0].serviceId)
    }
  }, [serviceData, selectedServiceId])

  // 当切换服务 Tab 时，如果该 tab 未加载过且查询条件不为空，自动触发查询
  React.useEffect(() => {
    // 确保有选中的服务、查询条件不为空、日期范围存在
    if (selectedServiceId && queryValue.trim() && dateRange?.from && dateRange?.to) {
      // 只有当该 tab 未加载过时，才自动请求
      if (!isTabLoaded(selectedServiceId)) {
        handleSearch()
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedServiceId, queryValue, dateRange, isTabLoaded])

  // Bella Tab 的搜索函数
  const handleSearch = React.useCallback(async () => {
    // 验证查询值是否为空
    if (!queryValue.trim()) {
      setQueryValueError(t("logs.filter.errors.enterQueryValue"))
      return
    }

    // 验证日期范围是否存在
    if (!dateRange?.from || !dateRange?.to) {
      console.error(t("logs.filter.errors.selectDateRange"))
      return
    }

    // 验证是否选中了服务
    if (!selectedServiceId) {
      console.error("请选择服务")
      return
    }

    // 检测筛选条件是否变化，如果变化则清空所有缓存
    const currentFilters = {
      queryType,
      queryValue: queryValue.trim(),
      dateRangeFrom: dateRange.from.getTime(),
      dateRangeTo: dateRange.to.getTime()
    }

    if (prevFiltersRef.current) {
      const isFiltersChanged =
        prevFiltersRef.current.queryType !== currentFilters.queryType ||
        prevFiltersRef.current.queryValue !== currentFilters.queryValue ||
        prevFiltersRef.current.dateRangeFrom !== currentFilters.dateRangeFrom ||
        prevFiltersRef.current.dateRangeTo !== currentFilters.dateRangeTo

      if (isFiltersChanged) {
        // 筛选条件变化，清空所有 tab 的缓存
        clearAllCache()
      }
    }

    // 保存当前筛选条件
    prevFiltersRef.current = currentFilters

    // 清除错误状态
    setQueryValueError("")

    // 设置当前 tab 的加载状态
    setTabLoading(selectedServiceId, true)

    try {
      const start = dateToTimestamp(dateRange.from)
      const end = dateToTimestamp(dateRange.to)

      // 构建查询字符串参数
      const queryParams = new URLSearchParams({
        start: start.toString(),
        end: end.toString(),
        serviceId: selectedServiceId,
      })

      if (queryType === "Bella TraceID") {
        queryParams.append('traceId', queryValue.trim())
      } else if (queryType === "AK Code") {
        queryParams.append('akCode', queryValue.trim())
      }

      // 调用 API 获取链路日志数据（使用 GET 请求）
      const response = await axios.get(`/api/logs/trace?${queryParams.toString()}`)

      if(response.status === 200){
        setTabData(selectedServiceId, response.data || [])
      } else {
        setTabData(selectedServiceId, [])
      }

    } catch (err) {
      // 错误处理
      const errorMessage = err instanceof Error ? err.message : '查询链路日志失败'
      console.error(errorMessage, err)
    } finally {
      // 无论成功或失败，都要重置加载状态
      setTabLoading(selectedServiceId, false)
    }
  }, [queryType, queryValue, dateRange, selectedServiceId, t, setTabData, setTabLoading, clearAllCache, setQueryValueError])

  // 重置处理函数
  const handleReset = React.useCallback(() => {
    setQueryType("AK Code")
    setQueryValue("")
    setQueryValueError("")
    const end = new Date()
    const start = new Date()
    start.setMinutes(start.getMinutes() - 5)
    handleDateRangeChange({ from: start, to: end })

    // 重置时清空所有 tab 的缓存
    clearAllCache()
  }, [handleDateRangeChange, clearAllCache])

  return (
    <div className="space-y-4">
      {/* 筛选条件模块 */}
      <LogsFilterBella
        queryType={queryType}
        queryValue={queryValue}
        queryValueError={queryValueError}
        onQueryTypeChange={setQueryType}
        onQueryValueChange={setQueryValue}
        dateRange={dateRange}
        onDateRangeChange={handleDateRangeChange}
        onSearch={handleSearch}
        onReset={handleReset}
      />

      {/* 服务选择 Tabs */}
      {serviceData.length > 0 && (
        <Tabs value={selectedServiceId} onValueChange={setSelectedServiceId} className="w-full">
          <TabsList>
            {serviceData.map((service) => (
              <TabsTrigger
                key={service.serviceId}
                value={service.serviceId}
                className="cursor-pointer"
              >
                {service.serviceId}
              </TabsTrigger>
            ))}
          </TabsList>

          {serviceData.map((service) => {
            // 获取当前 tab 的数据状态
            const currentTabData = getTabData(service.serviceId)

            return (
              <TabsContent key={service.serviceId} value={service.serviceId} className="space-y-4">
                {/* 显示该服务的日志查询结果 */}
                <BellaLogsTable
                  data={currentTabData.data}
                  isLoading={currentTabData.isLoading}
                  serviceId={service.serviceId}
                />
              </TabsContent>
            )
          })}
        </Tabs>
      )}
    </div>
  )
}
