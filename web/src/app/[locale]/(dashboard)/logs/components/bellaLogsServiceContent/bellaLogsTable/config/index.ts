/**
 * 列配置统一导出
 *
 * 此文件作为配置模块的统一入口，方便其他模块导入使用
 */

// 导出类型定义
export type {
  ColumnConfig,
  ColumnAlign,
  ServiceColumnConfigs,
  UseTableColumnsResult,
} from "./types"

// 导出公共列定义
export {
  timeColumn,
  runIdColumn,
  threadIdColumn,
  modelColumn,
  userIdColumn,
  userIdAltColumn,
  requestIdColumn,
  bellaTraceIdColumn,
  akCodeColumn,
  endpointColumn,
  forwardUrlColumn,
  createActionColumn,
} from "./commonColumns"

// 导出列配置映射和工具函数
export {
  defaultColumns,
  serviceColumnConfigs,
  getColumnsByServiceId,
  hasServiceConfig,
} from "./columnConfigs"
