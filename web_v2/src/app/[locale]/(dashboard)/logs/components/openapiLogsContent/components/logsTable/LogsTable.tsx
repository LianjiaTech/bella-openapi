'use client'

import * as React from "react"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/common/table"
import { Pagination } from "@/components/ui/pagination"
import type { LogEntry, LogMetrics } from "@/lib/types/logs"
import { LogDetailDrawer } from "./components/LogDetailDrawer"
import { safeParseJSON, formatTimestamp } from "./utils"

interface LogsTableProps {
  /** 日志数据列表 */
  data: LogEntry[]
  /** 是否正在加载 */
  isLoading?: boolean
}



/**
 * 日志表格组件
 */
export function LogsTable({ data, isLoading = false }: LogsTableProps) {

  // 分页状态
  const [currentPage, setCurrentPage] = React.useState(1)
  const [pageSize, setPageSize] = React.useState(10)

  // 抽屉状态
  const [drawerOpen, setDrawerOpen] = React.useState(false)
  const [selectedLog, setSelectedLog] = React.useState<LogEntry | null>(null)

  // 计算分页数据
  const totalItems = data?.length || 0
  const totalPages = Math.ceil(totalItems / pageSize)
  const startIndex = (currentPage - 1) * pageSize
  const endIndex = startIndex + pageSize
  const paginatedData = data?.slice(startIndex, endIndex) || []

  // 当数据变化时重置到第一页
  React.useEffect(() => {
    setCurrentPage(1)
  }, [data])

  // 当每页条数变化时重置到第一页
  const handlePageSizeChange = (newSize: number) => {
    setPageSize(newSize)
    setCurrentPage(1)
  }

  // 页码变化处理
  const handlePageChange = (page: number) => {
    setCurrentPage(page)
  }

  // 上一页
  const handlePrevPage = () => {
    if (currentPage > 1) {
      setCurrentPage(currentPage - 1)
    }
  }

  // 下一页
  const handleNextPage = () => {
    if (currentPage < totalPages) {
      setCurrentPage(currentPage + 1)
    }
  }

  // 打开详情抽屉
  const handleViewDetail = (log: LogEntry) => {
    setSelectedLog(log)
    setDrawerOpen(true)
  }

  // 关闭详情抽屉
  const handleCloseDrawer = () => {
    setDrawerOpen(false)
    // 延迟清空选中的日志数据，等待动画结束
    setTimeout(() => {
      setSelectedLog(null)
    }, 300)
  }

  // 如果正在加载,显示加载状态
  if (isLoading) {
    return (
      <div className="rounded-lg border bg-card p-8 text-center">
        <p className="text-muted-foreground">正在加载日志数据...</p>
      </div>
    )
  }

  // 如果没有数据,显示空状态
  if (!data || data.length === 0) {
    return (
      <div className="rounded-lg border bg-card p-8 text-center">
        <p className="text-muted-foreground">暂无日志数据</p>
      </div>
    )
  }

  return (
    <>
      <div className="rounded-lg border bg-card">
        <div className="p-4">
          <div className="flex items-center gap-2 mb-2">

            <h3 className="text-sm font-medium">请求日志</h3>
          </div>
        </div>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>时间</TableHead>
              <TableHead>能力点</TableHead>
              <TableHead>模型</TableHead>
              <TableHead>延迟(ms)</TableHead>
              <TableHead>消耗Token</TableHead>
              <TableHead className="text-right">操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {paginatedData.map((log, index) => {
              // 解析 metrics JSON 字符串
              const metrics = safeParseJSON<LogMetrics>(log.data_info_msg_metrics)

              return (
                <TableRow key={log.data_info_msg_requestId || index}>
                  {/* 时间列 */}
                  <TableCell className="font-mono text-sm">
                    {formatTimestamp(log.data_info_msg_requestTime)}
                  </TableCell>

                  {/* 能力点列 */}
                  <TableCell>
                    {log?.data_info_msg_endpoint || '-'}
                  </TableCell>

                  {/* 模型列 */}
                  <TableCell>
                    {log?.data_info_msg_model || '-'}
                  </TableCell>

                  {/* 延迟列 */}
                  <TableCell>
                    {metrics?.ttlt !== undefined ? metrics.ttlt.toFixed(2) : '-'}
                  </TableCell>

                  {/* 消耗Token列 */}
                  <TableCell>
                    {metrics?.token !== undefined ? metrics.token.toLocaleString() : '-'}
                  </TableCell>

                  {/* 操作列 */}
                  <TableCell className="text-right">
                    <button
                      className="text-sm text-blue-600 hover:text-blue-800 hover:underline"
                      onClick={() => handleViewDetail(log)}
                    >
                      查看详情
                    </button>
                  </TableCell>
                </TableRow>
              )
            })}
          </TableBody>
        </Table>
      </div>
      {/* 分页组件 */}
      <Pagination
        mode="frontend"
        currentPage={currentPage}
        totalPages={totalPages}
        totalItems={totalItems}
        pageSize={pageSize}
        pageSizeOptions={[10, 20, 50, 100]}
        hasMore={currentPage < totalPages}
        onPrevPage={handlePrevPage}
        onNextPage={handleNextPage}
        onPageChange={handlePageChange}
        onPageSizeChange={handlePageSizeChange}
      />

      {/* 日志详情抽屉 */}
      <LogDetailDrawer
        open={drawerOpen}
        onClose={handleCloseDrawer}
        data={selectedLog}
      />
    </>
  )
}
