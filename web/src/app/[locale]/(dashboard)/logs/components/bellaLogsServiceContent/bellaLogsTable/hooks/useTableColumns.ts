import { useMemo } from "react"
import type { UseTableColumnsResult, ColumnConfig } from "../config/types"
import type { LogEntry, KeRagInfo } from "@/lib/types/logs"
import { getColumnsByServiceId, hasServiceConfig } from "../config/columnConfigs"
import { createActionColumn } from "../config/commonColumns"

/**
 * 列配置管理 Hook
 *
 * @param serviceId - 服务 ID
 * @param onViewDetail - 查看详情回调函数
 * @returns 列配置结果
 *
 * @example
 * const { columns, isDefaultConfig } = useTableColumns(
 *   'service-assistant',
 *   handleViewDetail
 * )
 */
export function useTableColumns(
  serviceId: string,
  onViewDetail: (log: LogEntry | KeRagInfo) => void
): UseTableColumnsResult {
  // 使用 useMemo 缓存列配置，避免不必要的重新渲染
  const { columns, isDefaultConfig } = useMemo(() => {
    // 获取基础列配置（不包含操作列）
    const baseColumns = getColumnsByServiceId(serviceId)

    // 创建操作列
    const actionColumn = createActionColumn(onViewDetail)

    // 将操作列添加到末尾
    const fullColumns: ColumnConfig<LogEntry | KeRagInfo>[] = [...baseColumns, actionColumn]

    // 判断是否使用了默认配置
    const isDefault = !hasServiceConfig(serviceId)

    return {
      columns: fullColumns,
      isDefaultConfig: isDefault,
    }
  }, [serviceId, onViewDetail])

  return {
    columns,
    isDefaultConfig,
    serviceId,
  }
}
