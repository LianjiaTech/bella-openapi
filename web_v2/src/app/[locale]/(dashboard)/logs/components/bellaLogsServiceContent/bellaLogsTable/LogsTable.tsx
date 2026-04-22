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
import type { LogEntry, KeRagInfo } from "@/lib/types/logs"
import { LogDetailDrawer } from "./components/LogDetailDrawer"
import { useTableColumns } from "./hooks/useTableColumns"

interface LogsTableProps {
  /** 日志数据列表 */
  data: LogEntry[]
  /** 是否正在加载 */
  isLoading?: boolean
  /** 服务 ID - 用于确定表头列配置 */
  serviceId: string
}



/**
 * 日志表格组件
 */
export function LogsTable({ data, isLoading = false, serviceId }: LogsTableProps) {

  // 分页状态
  const [currentPage, setCurrentPage] = React.useState(1)
  const [pageSize, setPageSize] = React.useState(10)

  // 抽屉状态
  const [drawerOpen, setDrawerOpen] = React.useState(false)
  const [selectedLog, setSelectedLog] = React.useState<LogEntry | KeRagInfo | null>(null)

  // 打开详情抽屉
  const handleViewDetail = React.useCallback((log: LogEntry | KeRagInfo) => {
    setSelectedLog(log)
    setDrawerOpen(true)
  }, [])

  // 获取列配置
  const { columns } = useTableColumns(serviceId, handleViewDetail)

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
              {columns.map((column) => (
                <TableHead
                  key={column.key}
                  className={column.headerClassName}
                  style={{
                    textAlign: column.align || "left",
                    width: column.width,
                    minWidth: column.minWidth,
                    maxWidth: column.maxWidth,
                  }}
                >
                  {column.label}
                </TableHead>
              ))}
            </TableRow>
          </TableHeader>
          <TableBody>
            {paginatedData.map((log, index) => {
              return (
                <TableRow key={log.data_info_msg_requestId || index}>
                  {columns.map((column) => {
                    // 使用 accessor 提取数据
                    const value = column.accessor ? column.accessor(log) : undefined

                    return (
                      <TableCell
                        key={column.key}
                        className={column.className}
                        style={{
                          textAlign: column.align || "left",
                          width: column.width,
                          minWidth: column.minWidth,
                          maxWidth: column.maxWidth,
                        }}
                      >
                        {/* 使用自定义渲染函数，如果没有则直接显示值 */}
                        {column.render ? column.render(log, value) : (value ?? "-")}
                      </TableCell>
                    )
                  })}
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
