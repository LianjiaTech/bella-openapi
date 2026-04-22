import type { LogEntry,KeRagInfo } from "@/lib/types/logs"

/**
 * 列对齐方式
 */
export type ColumnAlign = "left" | "center" | "right"

/**
 * 列配置接口
 * @template T - 数据类型，默认为 LogEntry
 */
export interface ColumnConfig<T = LogEntry> {
  /** 列的唯一标识 */
  key: string

  /** 列的显示标题 */
  label: string

  /**
   * 数据访问器 - 从数据对象中提取值
   * @param row - 数据行对象
   * @returns 提取的值
   */
  accessor?: (row: T) => any

  /**
   * 自定义渲染函数
   * @param row - 数据行对象
   * @param value - 通过 accessor 提取的值
   * @returns 渲染的 React 节点
   */
  render?: (row: T, value?: any) => React.ReactNode

  /** 列的对齐方式，默认 left */
  align?: ColumnAlign

  /** 列的最小宽度 */
  minWidth?: string

  /** 列的最大宽度 */
  maxWidth?: string

  /** 列的固定宽度 */
  width?: string

  /** 是否可排序 */
  sortable?: boolean

  /**
   * 排序比较函数
   * @param a - 第一个数据行
   * @param b - 第二个数据行
   * @returns 排序结果：负数表示 a < b，0 表示相等，正数表示 a > b
   */
  sortComparator?: (a: T, b: T) => number

  /** 列的自定义类名 */
  className?: string

  /** 表头单元格的自定义类名 */
  headerClassName?: string

  /** 是否默认隐藏 */
  hidden?: boolean
}

/**
 * 服务列配置映射类型
 * key 为 serviceId，value 为该服务的列配置数组
 */
export type ServiceColumnConfigs = Record<string, ColumnConfig<LogEntry | KeRagInfo>[]>

/**
 * 列配置获取函数的返回类型
 */
export interface UseTableColumnsResult {
  /** 当前服务的列配置 */
  columns: ColumnConfig<LogEntry|KeRagInfo>[]

  /** 是否使用默认配置 */
  isDefaultConfig: boolean

  /** 当前 serviceId */
  serviceId: string
}
