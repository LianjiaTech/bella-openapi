import type { ServiceColumnConfigs, ColumnConfig } from "./types"
import type { LogEntry, KeRagInfo } from "@/lib/types/logs"
import {
  timeColumn,
  runIdColumn,
  threadIdColumn,
  modelColumn,
  userIdColumn,
  keRagColumn,
  keRagErrorCodeColumn,
  keRagLogLevelColumn,
  keRagStepColumn,
  openapiForwardUrlColumn,
} from "./commonColumns"

/**
 * 默认列配置
 * 当 serviceId 没有对应配置时使用
 */
export const defaultColumns: ColumnConfig<LogEntry | KeRagInfo>[] = [
  timeColumn,
  runIdColumn,
  threadIdColumn,
  modelColumn,
  userIdColumn,
  // 注意：操作列需要在组件中动态添加，因为需要传入回调函数
]

/**
 * 服务列配置映射
 *
 * 使用说明：
 * 1. key 为 serviceId（必须与后端返回的 serviceId 完全匹配）
 * 2. value 为该服务对应的列配置数组
 * 3. 列配置数组中可以使用 commonColumns 中定义的公共列，也可以自定义
 * 4. 操作列会在组件中自动添加，无需在此配置
 *
 * 示例：
 * 'service-assistant': [timeColumn, runIdColumn, modelColumn, userIdColumn],
 * 'service-embedding': [timeColumn, threadIdColumn, userIdColumn],
 */
export const serviceColumnConfigs: ServiceColumnConfigs = {
  "ke-rag": [
    keRagColumn,
    keRagErrorCodeColumn,
    keRagLogLevelColumn,
    keRagStepColumn,
  ],
  "openapi": [
    timeColumn,
    modelColumn,
    userIdColumn,
    openapiForwardUrlColumn,
  ],
}

/**
 * 获取指定 serviceId 的列配置
 * @param serviceId - 服务 ID
 * @returns 对应的列配置数组，如果没有找到则返回默认配置
 */
export function getColumnsByServiceId(serviceId: string) {
  return serviceColumnConfigs[serviceId] || defaultColumns
}

/**
 * 检查是否存在指定 serviceId 的配置
 * @param serviceId - 服务 ID
 * @returns 是否存在配置
 */
export function hasServiceConfig(serviceId: string): boolean {
  return serviceId in serviceColumnConfigs
}
