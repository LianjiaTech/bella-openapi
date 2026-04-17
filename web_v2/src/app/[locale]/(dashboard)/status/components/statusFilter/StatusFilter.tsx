"use client"

import * as React from "react"
import { DateRangePicker } from "@/components/ui/date-range-picker"
import { ModelSelect } from "./ModelSelect"
import { ChannelSelect } from "./ChannelSelect"
import { CategorySelect } from "./CategorySelect"
import { useStatusFilter } from "../../context/StatusFilterContext"

/**
 * 状态筛选器组件
 *
 * 从 StatusFilterContext 读取所有状态和数据，无需 Props
 * 提供能力分类、日期范围、模型和渠道的筛选功能
 */
export function StatusFilter() {
  // 从 Context 获取所有需要的状态、数据和方法
  const { filters, data, loading, actions } = useStatusFilter()

  return (
    <div className="filter-card">
      <div className="flex flex-col gap-4">
        {/* 标题 */}
        <div className="flex items-center justify-between">
          <h3 className="text-base font-medium">筛选条件</h3>
        </div>

        {/* 能力分类选择 */}
        <div className="flex flex-col gap-2">
          <label className="text-sm text-muted-foreground">能力分类</label>
          <CategorySelect
            value={filters.endpoint}
            onValueChange={actions.updateEndpoint}
          />
        </div>

        {/* 筛选字段 */}
        <div className="flex flex-row items-end gap-6 max-w-full overflow-x-auto">
          {/* 时间范围 */}
          <div className="flex flex-row items-center gap-2 flex-shrink-0">
            <label className="text-sm text-foreground whitespace-nowrap">
              时间范围
            </label>
            <DateRangePicker
              value={filters.dateRange}
              onChange={actions.updateDateRange}
              placeholder="选择时间范围"
            />
          </div>

          {/* 模型选择 */}
          <div className="flex flex-row items-center gap-2 flex-shrink-0">
            <label className="text-sm text-foreground whitespace-nowrap">
              模型
            </label>
            <ModelSelect
              value={filters.modelName}
              onValueChange={actions.updateModel}
              endpoint={filters.endpoint}
              placeholder="选择模型"
              autoSelectFirst={true}
            />
          </div>

          {/* 渠道选择 */}
          <div className="flex flex-row items-center gap-2 flex-shrink-0">
            <label className="text-sm text-foreground whitespace-nowrap">
              渠道
            </label>
            <ChannelSelect
              value={filters.channelCode}
              onValueChange={actions.updateChannel}
              metricsData={data.metricsData}
              loading={loading}
            />
          </div>
        </div>
      </div>
    </div>
  )
}
