'use client'

import { useState } from 'react'
import { ChannelDetails } from '@/lib/types/metadata'
import { Button } from '@/components/common/button'
import { Popover, PopoverTrigger, PopoverContent } from '@/components/common/popover'
import { Power, PlayCircle } from 'lucide-react'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/common/table'
import { getPriorityLabel } from '@/lib/constants/metadata'

interface ChannelsTableProps {
  channels: ChannelDetails[]
  onToggleStatus?: (channel: ChannelDetails) => void
  onTrial?: (channel: ChannelDetails) => void
  onEdit?: (channel: ChannelDetails) => void
  loadingChannels?: Set<string>
}

/**
 * 渠道列表表格组件
 */
export function ChannelsTable({ channels, onToggleStatus, onTrial, onEdit, loadingChannels }: ChannelsTableProps) {
  /**
   * 当前打开的 Popover ID
   * 设计说明: 用单一 state 管理所有 tooltip 的开关，通过 id 区分不同单元格
   * 避免 re-render: 仅在 openId 变化时触发，每个 Popover 按 id 判断是否 open
   */
  const [openId, setOpenId] = useState<string | null>(null)

  /**
   * 解析 JSON 字符串,如果解析失败返回原始字符串
   */
  const parseJsonSafe = (jsonStr: string): any => {
    try {
      return JSON.parse(jsonStr)
    } catch {
      return jsonStr
    }
  }

  /**
   * 格式化渠道信息
   */
  const formatChannelInfo = (channelInfo: string): string => {
    const parsed = parseJsonSafe(channelInfo)
    if (typeof parsed === 'object' && parsed !== null) {
      // 提取常用配置项
      const parts: string[] = []
      if (parsed.max_queue) parts.push(`max_queue: ${parsed.max_queue}`)
      if (parsed.timeout) parts.push(`timeout: ${parsed.timeout}s`)
      return parts.join(', ') || '无配置'
    }
    return channelInfo || '无配置'
  }

  /**
   * 格式化价格信息
   */
  const formatPriceInfo = (priceInfo: string): string => {
    const parsed = parseJsonSafe(priceInfo)
    if (typeof parsed === 'object' && parsed !== null) {
      // 常见的价格字段
      if (parsed.input !== undefined || parsed.output !== undefined) {
        return `¥${parsed.input || 0}/1K tokens (输入) , ¥${parsed.output || 0}/1K tokens (输出)`
      }
      // 如果有其他格式的价格信息
      return JSON.stringify(parsed)
    }
    return priceInfo || '未设置'
  }

  /**
   * 渲染 JSON 悬浮提示单元格
   * 设计说明:
   * - 使用 Radix Popover，PopoverContent 走 portal 渲染挂到 body，脱离 overflow 容器
   * - 通过 onMouseEnter/Leave 控制 open 状态实现 hover 效果
   * - Radix 自动处理视口边界翻转，不会被裁剪
   */
  const renderJsonTooltip = (rawJson: string, summary: string, id: string) => {
    let pretty = rawJson
    try {
      pretty = JSON.stringify(JSON.parse(rawJson), null, 2)
    } catch {}
    return (
      <Popover open={openId === id} onOpenChange={(o) => { if (!o) setOpenId(null) }}>
        <PopoverTrigger asChild>
          <span
            className="cursor-default"
            onMouseEnter={() => setOpenId(id)}
            onMouseLeave={() => setOpenId(null)}
          >
            {summary}
          </span>
        </PopoverTrigger>
        <PopoverContent className="w-80 text-xs font-mono whitespace-pre-wrap break-all p-3">
          {pretty}
        </PopoverContent>
      </Popover>
    )
  }

  /**
   * 格式化 JSON 摘要:取前两个非空字段拼接展示
   */
  const summarizeJson = (rawJson: string): string => {
    try {
      const obj = JSON.parse(rawJson)
      if (typeof obj !== 'object' || obj === null) return rawJson
      const entries = Object.entries(obj).filter(([, v]) => v !== null && v !== undefined && v !== '')
      if (entries.length === 0) return '无配置'
      const [k, v] = entries[0]
      const preview = `${k}: ${v}`
      return entries.length > 1 ? `${preview} ...` : preview
    } catch {
      return rawJson || '无配置'
    }
  }

  /**
   * 格式化队列配置
   */
  const formatQueueConfig = (channel: ChannelDetails): string => {
    const parts: string[] = []

    // 解析 channelInfo 获取更多配置
    const channelInfo = parseJsonSafe(channel.channelInfo)

    if (channel.priority) parts.push(`${getPriorityLabel(channel.priority)}`)

    if (typeof channelInfo === 'object' && channelInfo !== null) {
      if (channelInfo.max_queue) parts.push(`队列: ${channelInfo.max_queue}`)
      if (channelInfo.timeout) parts.push(`超时: ${channelInfo.timeout}s`)
    }

    return parts.join(', ') || '默认配置'
  }

  return (
    <div className="rounded-md border overflow-x-auto">
      <Table className="table-fixed">
        <TableHeader>
          <TableRow>
            <TableHead className="text-center w-52">渠道ID</TableHead>
            <TableHead className="text-center w-40">转发URL</TableHead>
            <TableHead className="text-center">协议</TableHead>
            <TableHead className="text-center">供应商</TableHead>
            <TableHead className="text-center">渠道信息</TableHead>
            <TableHead className="text-center">价格信息</TableHead>
            <TableHead className="text-center">优先级</TableHead>
            {/* <TableHead className="w-[160px] text-center">队列配置</TableHead> */}
            <TableHead className="text-center">试用状态</TableHead>
            <TableHead className="w-32 text-center">操作</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {channels.length === 0 ? (
            <TableRow>
              <TableCell colSpan={7} className="h-24 text-center text-muted-foreground">
                暂无渠道配置
              </TableCell>
            </TableRow>
          ) : (
            channels.map((channel) => (
              <TableRow key={channel.channelCode}>
                {/* 渠道ID */}
                <TableCell className="text-center break-all">{channel.channelCode}</TableCell>

                {/* 转发URL */}
                <TableCell className="text-center">
                  <Popover open={openId === `${channel.channelCode}-url`} onOpenChange={(o) => { if (!o) setOpenId(null) }}>
                    <PopoverTrigger asChild>
                      <span
                        className="truncate block cursor-default"
                        onMouseEnter={() => setOpenId(`${channel.channelCode}-url`)}
                        onMouseLeave={() => setOpenId(null)}
                      >
                        {channel.url}
                      </span>
                    </PopoverTrigger>
                    <PopoverContent className="w-80 text-xs break-all p-3">
                      {channel.url}
                    </PopoverContent>
                  </Popover>
                </TableCell>

                {/* 协议 */}
                <TableCell className="text-center">{channel.protocol}</TableCell>

                {/* 供应商 */}
                <TableCell className="text-center">{channel.supplier}</TableCell>

                {/* 渠道信息 */}
                <TableCell className="text-center">
                  {renderJsonTooltip(channel.channelInfo, summarizeJson(channel.channelInfo), `${channel.channelCode}-info`)}
                </TableCell>

                {/* 价格信息 */}
                <TableCell className="text-center">
                  {renderJsonTooltip(channel.priceInfo, summarizeJson(channel.priceInfo), `${channel.channelCode}-price`)}
                </TableCell>

                {/* 优先级 队列配置 */}
                <TableCell className="text-center">
                  {formatQueueConfig(channel)}
                </TableCell>
                {/* 试用状态 */}
                <TableCell className="text-center">
                  {channel.trialEnabled === 1 ? '支持试用' : '禁止试用'}
                </TableCell>

                {/* 操作列 */}
                <TableCell className="text-center">
                  <div className="flex items-center justify-end gap-2">
                    {/* 停用/启用按钮 */}
                    <Button
                      variant={channel.status === 'active' ? 'outline' : 'default'}
                      size="sm"
                      onClick={() => onToggleStatus?.(channel)}
                      disabled={loadingChannels?.has(channel.channelCode)}
                      className="h-8 px-3 gap-1 cursor-pointer"
                    >
                      <Power className="h-3 w-3" />
                      {channel.status === 'active' ? '停用' : '启用'}
                    </Button>

                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => onEdit?.(channel)}
                      className="h-8 px-3 gap-1 cursor-pointer"
                    >
                      编辑
                    </Button>

                    {/* 试用按钮 ，因为调用的是更新渠道接口，当停用/启用未返回结果，同时更新试用状态，会导致试用状态不一致*/}
                    {/* {channel.trialEnabled === 1 && (
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => onTrial?.(channel)}
                        className="h-8 px-3 gap-1 cursor-pointer"
                      >
                        <PlayCircle className="h-3 w-3" />
                        {channel.trialEnabled !== 1 ? '试用' : '禁止试用'}
                      </Button>
                    )} */}
                  </div>
                </TableCell>
              </TableRow>
            ))
          )}
        </TableBody>
      </Table>
    </div>
  )
}
