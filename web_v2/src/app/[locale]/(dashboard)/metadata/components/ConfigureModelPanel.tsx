'use client'

import { Model, ModelDetails, } from '@/lib/types/openapi'
import { ChannelDetails } from '@/lib/types/metadata'
import { Button } from '@/components/common/button'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/common/select'
import { Input } from '@/components/common/input'
import { Search } from 'lucide-react'
import { ArrowLeft, Plus, AlertCircle } from 'lucide-react'
import { TopBar } from '@/components/layout'
import { useState, useEffect } from 'react'
import { getModelDetails, updateChannelStatus, listPrivateChannels, updatePrivateChannelStatus } from '@/lib/api/metadata'
import { ChannelConfigDialog } from './ChannelConfigDialog'
import { ChannelsTable } from './ChannelsTable'
import { useToast } from '@/hooks/use-toast'
import { ModelInfo, ModelInfoSection } from './ModelInfoSection'

interface ConfigureModelPanelProps {
  model: Model
  onBack: () => void
  isPrivate?: boolean
}

/**
 * 模型配置面板组件
 * 在元数据管理页面内原地展示,替代 Sheet 弹窗
 */
export function ConfigureModelPanel({ model, onBack, isPrivate = false }: ConfigureModelPanelProps) {
  // 状态管理
  const [modelDetails, setModelDetails] = useState<ModelDetails | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [isChannelDialogOpen, setIsChannelDialogOpen] = useState(false)
  const [dialogMode, setDialogMode] = useState<'create' | 'edit'>('create')
  // 渠道状态筛选: all=全部 active=启用 inactive=停用
  const [statusFilter, setStatusFilter] = useState<'all' | 'active' | 'inactive'>('all')
  // 渠道ID搜索
  const [channelSearch, setChannelSearch] = useState('')
  const [editingChannel, setEditingChannel] = useState<ChannelDetails | undefined>(undefined)
  const [loadingChannels, setLoadingChannels] = useState<Set<string>>(new Set())
  const { toast } = useToast()

  // 获取描述信息 - 从 properties 中解析（仅管理员模式使用）
  const getDescription = (): string | undefined => {
    if (isPrivate) return undefined
    if (typeof model.properties === 'object' && model.properties !== null) {
      return (model.properties as any)?.description
    }
    return undefined
  }

  const description = getDescription()

  /**
   * 获取渠道数据
   */
  const fetchModelDetails = async () => {
    if (!model.modelName) return

    try {
      setIsLoading(true)
      setError(null)
      if (isPrivate) {
        const channels = await listPrivateChannels('model', model.modelName)
        setModelDetails({ model, channels })
      } else {
        const details = await getModelDetails(model.modelName)
        setModelDetails(details)
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : '获取渠道数据失败')
      console.error('Failed to fetch channel data:', err)
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    fetchModelDetails()
  }, [model.modelName, isPrivate])

  /**
   * 重试获取数据
   */
  const handleRetry = () => {
    fetchModelDetails()
  }

  /**
   * 处理新增渠道操作
   */
  const handleAddChannel = () => {
    setDialogMode('create')
    setEditingChannel(undefined)
    setIsChannelDialogOpen(true)
  }

  /**
   * 处理编辑渠道操作
   */
  const handleEditChannel = (channel: ChannelDetails) => {
    setDialogMode('edit')
    setEditingChannel(channel)
    setIsChannelDialogOpen(true)
  }

  /**
   * 处理渠道启用/停用切换
   */
  const handleToggleChannelStatus = async (channel: ChannelDetails) => {
    // 1. 添加到 loading 状态
    setLoadingChannels(prev => new Set(prev).add(channel.channelCode))

    try {
      // 2. 判断当前状态并调用对应接口
      // active → 停用 (active: false)
      // inactive → 启用 (active: true)
      const active = channel.status === 'inactive'
      if (isPrivate) {
        await updatePrivateChannelStatus(channel.channelCode, active)
      } else {
        await updateChannelStatus(channel.channelCode, active)
      }

      // 3. 接口成功 - 前端直接更新状态
      setModelDetails(prev => {
        if (!prev) return prev
        return {
          ...prev,
          channels: prev.channels.map(ch =>
            ch.channelCode === channel.channelCode
              ? { ...ch, status: ch.status === 'active' ? 'inactive' : 'active' }
              : ch
          )
        }
      })
    } catch (error) {
      // 4. 接口失败 - 显示 toast 提示
      toast({
        title: '操作失败',
        description: error instanceof Error ? error.message : '渠道状态切换失败，请重试',
        variant: 'destructive'
      })
    } finally {
      // 5. 移除 loading 状态
      setLoadingChannels(prev => {
        const next = new Set(prev)
        next.delete(channel.channelCode)
        return next
      })
    }
  }

  /**
   * 处理渠道试用
   */
  const handleChannelTrial = (channel: ChannelDetails) => {

  }

  /**
   * 处理模型信息更新
   * 当子组件 ModelInfoSection 触发状态变更时,同步更新父组件的 modelDetails
   */
  const handleModelInfoUpdate = (updates: Partial<ModelInfo>) => {
    console.log('---updates ====', updates)
    setModelDetails(prev => {
      if (!prev) return prev
      return {
        ...prev,
        model: {
          ...prev.model,
          ...updates
        }
      }
    })
  }


  return (
    <div className="flex h-[calc(100vh-4rem)] flex-col">
      {/* 顶部导航栏 */}
      <TopBar
        leftAction={
          <Button variant="ghost" size="default" onClick={onBack} className="gap-2">
            <ArrowLeft className="h-4 w-4" />
            返回模型列表
          </Button>
        }
      />

      {/* 配置内容区域 */}
      <div className="flex-1 overflow-y-auto bg-muted/30">
        <div className="container px-6 py-8">

          {/* 错误提示 */}
          {error && (
            <div className="mb-6 rounded-lg border border-red-500/20 bg-red-500/10 p-4">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <AlertCircle className="h-5 w-5 flex-shrink-0 text-red-500" />
                  <p className="text-sm text-red-500">{error}</p>
                </div>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={handleRetry}
                  className="ml-4 flex-shrink-0"
                >
                  重试
                </Button>
              </div>
            </div>
          )}

          {/* 模型信息区域：私有渠道模式下不展示 */}
          {!isPrivate && (
            <>
              <div className="mb-6 rounded-lg">
                <h1 className="text-xl font-semibold text-foreground">
                  模型：{model.modelName}
                </h1>
                {description && (
                  <p className="text-sm text-muted-foreground mt-2">
                    {description}
                  </p>
                )}
              </div>
              <ModelInfoSection modelInfo={modelDetails?.model} onUpdate={handleModelInfoUpdate} />
            </>
          )}

          {/* 配置表单内容区域 */}
          <div className="rounded-lg border bg-card">
            <div className="p-6">
              <div className="flex items-center justify-between mb-4">
                <h2 className="text-base font-medium">{isPrivate ? '私有渠道配置' : '渠道配置'}</h2>
                <div className="flex items-center gap-2">
                  {/* 渠道ID搜索框 */}
                  <div className="relative">
                    <Search className="absolute left-2 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
                    <Input
                      value={channelSearch}
                      onChange={(e) => setChannelSearch(e.target.value)}
                      placeholder="搜索渠道ID"
                      className="h-8 pl-7 w-72 text-sm"
                    />
                  </div>
                  {/* 状态筛选下拉 */}
                  <div className="flex items-center gap-1">
                    <span className="text-sm text-muted-foreground whitespace-nowrap">状态:</span>
                    <Select value={statusFilter} onValueChange={(v) => setStatusFilter(v as 'all' | 'active' | 'inactive')}>
                      <SelectTrigger className="h-8 w-20 text-sm border-none shadow-none">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="all">全部</SelectItem>
                        <SelectItem value="active">启用</SelectItem>
                        <SelectItem value="inactive">停用</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>
                  <Button
                    onClick={handleAddChannel}
                    size="sm"
                    className="gap-2 cursor-pointer"
                    disabled={isLoading}
                  >
                    <Plus className="h-4 w-4" />
                    新增渠道
                  </Button>
                </div>
              </div>

              {/* 加载状态 */}
              {isLoading && (
                <div className="space-y-4">
                  <div className="rounded-lg border p-8 animate-pulse">
                    <div className="space-y-3">
                      <div className="h-4 w-full bg-muted rounded"></div>
                      <div className="h-4 w-full bg-muted rounded"></div>
                      <div className="h-4 w-3/4 bg-muted rounded"></div>
                    </div>
                  </div>
                </div>
              )}

              {/* 渠道列表表格 */}
              {!isLoading && !error && modelDetails?.channels && (
                <ChannelsTable
                  channels={modelDetails.channels.filter(c =>
                    (statusFilter === 'all' || c.status === statusFilter) &&
                    (!channelSearch.trim() || c.channelCode.toLowerCase().includes(channelSearch.trim().toLowerCase()))
                  )}
                  onToggleStatus={handleToggleChannelStatus}
                  onTrial={handleChannelTrial}
                  onEdit={handleEditChannel}
                  loadingChannels={loadingChannels}
                />
              )}
            </div>
          </div>

        </div>
      </div>

      {/* 渠道配置弹窗 */}
      <ChannelConfigDialog
        mode={dialogMode}
        open={isChannelDialogOpen}
        onOpenChange={setIsChannelDialogOpen}
        modelName={model.modelName}
        initialData={editingChannel}
        onSuccess={fetchModelDetails}
        isPrivate={isPrivate}
      />
    </div>
  )
}
