'use client'

import { ChannelDetails } from '@/lib/types/metadata'
import { Button } from '@/components/common/button'
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
    <div className="rounded-md border">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="w-[250px]">转发URL</TableHead>
            <TableHead className="w-[100px]">协议</TableHead>
            <TableHead className="w-[120px]">供应商</TableHead>
            <TableHead className="w-[150px]">渠道信息</TableHead>
            <TableHead className="w-[200px]">价格信息</TableHead>
            <TableHead className="w-[80px]">优先级</TableHead>
            <TableHead className="w-[100px] ">试用状态</TableHead>
            {/* <TableHead className="w-[160px] text-center">队列配置</TableHead> */}
            <TableHead className="w-[20px] text-center">操作</TableHead>
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
                {/* 转发URL */}
                <TableCell>
                  <div className="max-w-[160px] truncate" title={channel.url}>
                    {channel.url}
                  </div>
                </TableCell>

                {/* 协议 */}
                <TableCell>{channel.protocol}</TableCell>

                {/* 供应商 */}
                <TableCell>{channel.supplier}</TableCell>

                {/* 渠道信息 */}
                <TableCell>
                  <div className='w-20 whitespace-nowrap'>
                  {formatChannelInfo(channel.channelInfo)}
                  </div>
                </TableCell>

                {/* 价格信息 */}
                <TableCell>
                  {formatPriceInfo(channel.priceInfo)}
                </TableCell>

                {/* 优先级 队列配置 */}
                <TableCell>
                  <div className='w-20 whitespace-nowrap'>
                  {formatQueueConfig(channel)}
                  </div>
                </TableCell>
                <TableCell>
                  <div className='w-20 whitespace-nowrap'>
                    {channel.trialEnabled === 1 ? '支持试用' : '禁止试用'}
                  </div>
                </TableCell>

                {/* 操作列 */}
                <TableCell className="text-right">
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
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => onEdit?.(channel)}
                      className="h-8 px-3 gap-1 cursor-pointer"
                    >
                      编辑
                    </Button>
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
