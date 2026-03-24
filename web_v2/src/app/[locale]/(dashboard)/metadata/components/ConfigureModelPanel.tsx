'use client'

import { Model, ModelDetails, } from '@/lib/types/openapi'
import { ChannelDetails } from '@/lib/types/metadata'
import { Button } from '@/components/common/button'
import { ArrowLeft, Plus, AlertCircle } from 'lucide-react'
import { TopBar } from '@/components/layout'
import { useState, useEffect } from 'react'
import { getModelDetails, updateChannelStatus } from '@/lib/api/metadata'
import { ChannelConfigDialog } from './ChannelConfigDialog'
import { ChannelsTable } from './ChannelsTable'
import { useToast } from '@/hooks/use-toast'
import { ModelInfo, ModelInfoSection } from './ModelInfoSection'

interface ConfigureModelPanelProps {
  model: Model
  onBack: () => void
}

/**
 * 模型配置面板组件
 * 在元数据管理页面内原地展示,替代 Sheet 弹窗
 */
export function ConfigureModelPanel({ model, onBack }: ConfigureModelPanelProps) {
  // 状态管理
  const [modelDetails, setModelDetails] = useState<ModelDetails | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [isChannelDialogOpen, setIsChannelDialogOpen] = useState(false)
  const [dialogMode, setDialogMode] = useState<'create' | 'edit'>('create')
  const [editingChannel, setEditingChannel] = useState<ChannelDetails | undefined>(undefined)
  const [loadingChannels, setLoadingChannels] = useState<Set<string>>(new Set())
  const { toast } = useToast()

  // 获取描述信息 - 从 properties 中解析
  const getDescription = (): string | undefined => {
    if (typeof model.properties === 'object' && model.properties !== null) {
      return (model.properties as any)?.description
    }
    return undefined
  }

  const description = getDescription()

  /**
   * 获取模型详情数据
   */
  const fetchModelDetails = async () => {
    if (!model.modelName) return

    try {
      setIsLoading(true)
      setError(null)
      const details = await getModelDetails(model.modelName)
      console.log('---details ====', details)
      setModelDetails(details)
    } catch (err) {
      setError(err instanceof Error ? err.message : '获取模型详情失败')
      console.error('Failed to fetch model details:', err)
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    fetchModelDetails()
  }, [model.modelName])

  /**
   * 重试获取数据
   */
  const handleRetry = () => {
    setError(null)
    setIsLoading(true)
    getModelDetails(model.modelName)
      .then(setModelDetails)
      .catch(err => setError(err instanceof Error ? err.message : '获取模型详情失败'))
      .finally(() => setIsLoading(false))
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
      await updateChannelStatus(channel.channelCode, active)

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

          {/* 模型信息卡片 */}
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

          {/* 配置表单内容区域 */}
          <div className="rounded-lg border bg-card">
            <div className="p-6">
              <div className="flex items-center justify-between mb-4">
                <h2 className="text-base font-medium">渠道配置</h2>
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
                  channels={modelDetails.channels}
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
      />
    </div>
  )
}
