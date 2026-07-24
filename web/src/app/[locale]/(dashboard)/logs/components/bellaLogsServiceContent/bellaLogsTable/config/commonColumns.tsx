import type { ColumnConfig } from "./types"
import type { LogEntry,KeRagInfo } from "@/lib/types/logs"
import { formatTimestamp } from "../utils"

/**
 * 公共列定义 - 可在不同 serviceId 配置中复用
 */

/**
 * 类型守卫：检查是否为 LogEntry
 */
function isLogEntry(row: LogEntry | KeRagInfo): row is LogEntry {
  return 'data_info_msg_requestTime' in row
}

/**
 * 时间列
 */
export const timeColumn: ColumnConfig<LogEntry | KeRagInfo> = {
  key: "time",
  label: "时间",
  accessor: (row) => isLogEntry(row) ? row.data_info_msg_requestTime : undefined,
  render: (row, value) => (
    <span className="font-mono text-sm">
      {value ? formatTimestamp(value) : "-"}
    </span>
  ),
  minWidth: "180px",
}

/**
 * Run ID 列
 */
export const runIdColumn: ColumnConfig<LogEntry | KeRagInfo> = {
  key: "runId",
  label: "run ID",
  accessor: (row) => isLogEntry(row) ? row.data_info_msg_runId : undefined,
  render: (row, value) => (
    <span className="truncate">{value || "-"}</span>
  ),
}

/**
 * Thread ID 列
 */
export const threadIdColumn: ColumnConfig<LogEntry | KeRagInfo> = {
  key: "threadId",
  label: "thread ID",
  accessor: (row) => isLogEntry(row) ? row.data_info_msg_threadId : undefined,
  render: (row, value) => (
    <span className="truncate">{value || "-"}</span>
  ),
}

/**
 * 模型列
 */
export const modelColumn: ColumnConfig<LogEntry | KeRagInfo> = {
  key: "model",
  label: "模型",
  accessor: (row) => isLogEntry(row) ? row.data_info_msg_model : undefined,
  render: (row, value) => (
    <p className="truncate w-[200px]">{value || "-"}</p>
  ),
}

/**
 * 用户 ID 列
 */
export const userIdColumn: ColumnConfig<LogEntry | KeRagInfo> = {
  key: "userId",
  label: "用户 ID",
  accessor: (row) => isLogEntry(row) ? row.data_info_msg_user : undefined,
  render: (row, value) => (
    <span className="truncate">{value || "-"}</span>
  ),
}

/**
 * User ID 列 (别名，使用 data_info_msg_userId 字段)
 */
export const userIdAltColumn: ColumnConfig<LogEntry | KeRagInfo> = {
  key: "userIdAlt",
  label: "用户 ID",
  accessor: (row) => isLogEntry(row) ? row.data_info_msg_userId : undefined,
  render: (row, value) => (
    <span className="truncate">{value || "-"}</span>
  ),
}

/**
 * Request ID 列
 */
export const requestIdColumn: ColumnConfig<LogEntry | KeRagInfo> = {
  key: "requestId",
  label: "请求 ID",
  accessor: (row) => isLogEntry(row) ? row.data_info_msg_requestId : undefined,
  render: (row, value) => (
    <span className="font-mono text-sm truncate">{value || "-"}</span>
  ),
}

/**
 * Bella Trace ID 列
 */
export const bellaTraceIdColumn: ColumnConfig<LogEntry | KeRagInfo> = {
  key: "bellaTraceId",
  label: "Bella Trace ID",
  accessor: (row) => row.data_info_msg_bellaTraceId,
  render: (row, value) => (
    <span className="font-mono text-sm truncate">{value || "-"}</span>
  ),
}

/**
 * AK Code 列
 */
export const akCodeColumn: ColumnConfig<LogEntry | KeRagInfo> = {
  key: "akCode",
  label: "AK Code",
  accessor: (row) => isLogEntry(row) ? row.data_info_msg_akCode : undefined,
  render: (row, value) => (
    <span className="font-mono text-sm truncate">{value || "-"}</span>
  ),
}

/**
 * 能力点列
 */
export const endpointColumn: ColumnConfig<LogEntry | KeRagInfo> = {
  key: "endpoint",
  label: "能力点",
  accessor: (row) => isLogEntry(row) ? row.data_info_msg_endpoint : undefined,
  render: (row, value) => (
    <span className="truncate">{value || "-"}</span>
  ),
}

/**
 * 转发 URL 列
 */
export const forwardUrlColumn: ColumnConfig<LogEntry | KeRagInfo> = {
  key: "forwardUrl",
  label: "转发 URL",
  accessor: (row) => isLogEntry(row) ? row.data_info_msg_forwardUrl : undefined,
  render: (row, value) => (
    <span className="truncate text-xs" title={value}>
      {value || "-"}
    </span>
  ),
  maxWidth: "200px",
}
/**
 * 类型守卫：检查是否为 KeRagInfo
 */
function isKeRagInfo(row: LogEntry | KeRagInfo): row is KeRagInfo {
  return 'data_cost' in row
}

export const keRagColumn: ColumnConfig<LogEntry | KeRagInfo> = {
  key: "keRag",
  label: "data_cost",
  accessor: (row) => isKeRagInfo(row) ? row.data_cost : undefined,
  render: (row, value) => (
    <span className="truncate">{value || "-"}</span>
  ),
}
export const keRagErrorCodeColumn: ColumnConfig<LogEntry | KeRagInfo> = {
  key: "keRagErrorCode",
  label: "data_errorCode",
  accessor: (row) => isKeRagInfo(row) ? row.data_errorCode : undefined,
  render: (row, value) => (
    <span className="truncate">{value || "-"}</span>
  )
}
export const keRagLogLevelColumn: ColumnConfig<LogEntry | KeRagInfo> = {
  key: "keRagLogLevel",
  label: "data_loglevel",
  accessor: (row) => isKeRagInfo(row) ? row.data_loglevel : undefined,
  render: (row, value) => (
    <span className="truncate">{value || "-"}</span>
  )
}
export const keRagStepColumn: ColumnConfig<LogEntry | KeRagInfo> = {
  key: "keRagStep",
  label: "data_step",
  accessor: (row) => isKeRagInfo(row) ? row.data_step : undefined,
  render: (row, value) => (
    <span className="truncate">{value || "-"}</span>
  )
}

export const openapiForwardUrlColumn: ColumnConfig<LogEntry | KeRagInfo> = {
  key: "openapi",
  label: "转发URL",
  accessor: (row) => isLogEntry(row) ? row.data_info_msg_forwardUrl : undefined,
  render: (row, value) => (
    <p className="truncate w-[200px]">{value || "-"}</p>
  )
}

/**
 * 操作列 - 需要传入点击处理函数
 */
export const createActionColumn = (
  onViewDetail: (log: LogEntry | KeRagInfo) => void
): ColumnConfig<LogEntry | KeRagInfo> => ({
  key: "action",
  label: "操作",
  align: "right",
  render: (row) => {
    return (
      <button
        className="text-sm text-blue-600 hover:text-blue-800 hover:underline"
        onClick={() => onViewDetail(row)}
      >
        查看详情
      </button>
    )
  },
  width: "100px",
})
