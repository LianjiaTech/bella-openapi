"use client"

import { useState, useEffect } from "react"
import { Badge } from "@/components/common/badge"
import { Button } from "@/components/common/button"
import { Card, CardContent } from "@/components/common/card"
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/common/collapsible"
import { ChevronDown, ChevronUp, Pencil } from "lucide-react"
import { updateModelStatus, updateModelVisibility, updateModel, linkModel } from "@/lib/api/metadata"
import type { Model } from "@/lib/types/openapi"
import { toast } from "sonner"

export type ModelInfo = Model

interface ModelInfoSectionProps {
  modelInfo: ModelInfo | undefined
  onUpdate: (updates: Partial<ModelInfo>) => void
}

interface InfoFieldProps {
  label: string
  value: string
  isEditing?: boolean
  onChange?: (value: string) => void
}

/**
 * InfoField 组件
 * 职责:展示单个信息字段,支持编辑模式
 * - isEditing=false: 静态文本展示
 * - isEditing=true: 可编辑输入框
 */
function InfoField({ label, value, isEditing = false, onChange }: InfoFieldProps) {
  return (
    <div className="py-4 px-4 bg-muted/30 rounded-lg">
      <p className="text-sm font-medium text-muted-foreground mb-1">{label}</p>
      {isEditing ? (
        <input
          type="text"
          value={value || ""}
          onChange={(e) => onChange?.(e.target.value)}
          className="w-full text-sm text-foreground bg-white border border-input rounded-md px-3 py-1.5 focus:outline-none focus:ring-2 focus:ring-ring"
        />
      ) : (
        <p className="text-sm text-foreground break-all">{value || "-"}</p>
      )}
    </div>
  )
}

/**
 * ModelInfoSection 组件
 * 职责:展示和编辑模型信息
 * - 管理编辑状态(isEditing)
 * - 存储编辑中的数据(editedData)
 * - 协调子组件(InfoField)的编辑行为
 */
export function ModelInfoSection({ modelInfo, onUpdate }: ModelInfoSectionProps) {
  const [isOpen, setIsOpen] = useState(true)
  const [status, setStatus] = useState(modelInfo?.status)
  const [visibility, setVisibility] = useState(modelInfo?.visibility)
  const [isEditing, setIsEditing] = useState(false)
  const [editedData, setEditedData] = useState<Partial<ModelInfo>>({})

  useEffect(() => {
    setStatus(modelInfo?.status)
    setVisibility(modelInfo?.visibility)
  }, [modelInfo])

  // 进入编辑模式:初始化编辑数据
  const handleStartEdit = () => {
    setEditedData({
      linkedTo: modelInfo?.linkedTo ?? '',
      documentUrl: modelInfo?.documentUrl ?? '',
      properties: modelInfo?.properties ?? '',
      features: modelInfo?.features ?? '',
      ownerType: modelInfo?.ownerType ?? '',
      ownerCode: modelInfo?.ownerCode ?? '',
      ownerName: modelInfo?.ownerName ?? '',
    })
    setIsEditing(true)
  }

  // 更新单个字段
  const handleFieldChange = (field: keyof ModelInfo, value: string) => {
    setEditedData(prev => ({ ...prev, [field]: value }))
  }

  // 保存编辑:调用 onUpdate 通知父组件
  const handleSave = async () => {
    try {
      if (editedData.linkedTo !== modelInfo?.linkedTo) {
        const res = await linkModel(modelInfo?.modelName ?? '', editedData?.linkedTo ?? '')
        if (res) {
          toast.success("模型软链已更新")
          onUpdate({ ...editedData, linkedTo: editedData?.linkedTo ?? '' })
        }
      }
      const res = await updateModel(modelInfo?.modelName ?? '', editedData)
      if (res) {
        toast.success("模型信息已更新")
        onUpdate(editedData)
      }
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "模型信息更新失败")
    }
    setIsEditing(false)
    setEditedData({})
  }

  // 取消编辑:丢弃修改并退出编辑模式
  const handleCancel = () => {
    setIsEditing(false)
    setEditedData({})
  }

  return (
    <>
      <Collapsible open={isOpen} onOpenChange={setIsOpen}>
        <Card className="border-0 shadow-sm bg-gradient-to-br from-background via-background to-muted/20">
          <div className="p-6 bg-white rounded-lg mb-6">
            {/* Header */}
            <div className="flex items-center justify-between mb-6">
              <div className="flex items-center gap-4">
                <h2 className="text-lg font-semibold text-foreground">{modelInfo?.modelName}</h2>
                <div className="flex items-center gap-2">
                  <Badge variant={status === 'active' ? "default" : "secondary"} className="text-xs">
                    {status === 'active' ? "已启用" : "已禁用"}
                  </Badge>
                  <Badge variant={visibility === 'public' ? "default" : "outline"} className="text-xs">
                    {visibility === 'public' ? "公开" : "私有"}
                  </Badge>
                </div>
              </div>
              <div className="flex items-center gap-2">
                {!isEditing && (
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={handleStartEdit}
                    className="gap-1 cursor-pointer"
                  >
                    <Pencil className="h-3.5 w-3.5" />
                    编辑模型信息
                  </Button>
                )}
                <Button
                  variant={status === 'active' ? "destructive" : "default"}
                  size="sm"
                  onClick={async () => {
                    const targetStatus = status !== 'active' // 目标状态
                    const res = await updateModelStatus(modelInfo?.modelName ?? '', targetStatus)
                    if (res) {
                      setStatus(status === 'active' ? 'inactive' : 'active')
                    } else {
                      toast("模型状态更新失败")
                    }
                  }
                  }
                  className="cursor-pointer"
                >
                  {status === 'active' ? "停用" : "启用"}
                </Button>
                <Button
                  className="cursor-pointer"
                  variant="outline"
                  size="sm"
                  onClick={async () => {
                    const targetIsPublic = visibility !== 'public' // 目标状态
                    const res = await updateModelVisibility(modelInfo?.modelName ?? '', targetIsPublic)
                    if (res) {
                      setVisibility(visibility === 'public' ? 'private' : 'public')
                    } else {
                      toast("模型公开状态更新失败")
                    }
                  }}
                >
                  {visibility === 'public' ? "取消公开" : "公开"}
                </Button>
                <CollapsibleTrigger asChild>
                  <Button variant="ghost" size="icon">
                    {isOpen ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
                  </Button>
                </CollapsibleTrigger>
              </div>
            </div>

            <CollapsibleContent>
              <CardContent className="p-0 space-y-3">
                <InfoField
                  label="模型软链"
                  value={isEditing ? (editedData.linkedTo ?? '') : (modelInfo?.linkedTo ?? '')}
                  isEditing={isEditing}
                  onChange={(value) => handleFieldChange('linkedTo', value)}
                />
                <InfoField
                  label="文档 URL"
                  value={isEditing ? (editedData.documentUrl ?? '') : (modelInfo?.documentUrl ?? '')}
                  isEditing={isEditing}
                  onChange={(value) => handleFieldChange('documentUrl', value)}
                />
                <InfoField
                  label="属性"
                  value={isEditing ? (editedData.properties ?? '') : (modelInfo?.properties ?? '')}
                  isEditing={isEditing}
                  onChange={(value) => handleFieldChange('properties', value)}
                />
                <InfoField
                  label="特性"
                  value={isEditing ? (editedData.features ?? '') : (modelInfo?.features ?? '')}
                  isEditing={isEditing}
                  onChange={(value) => handleFieldChange('features', value)}
                />
                <InfoField
                  label="所有者类型"
                  value={isEditing ? (editedData.ownerType ?? '') : (modelInfo?.ownerType ?? '')}
                  isEditing={isEditing}
                  onChange={(value) => handleFieldChange('ownerType', value)}
                />
                <InfoField
                  label="所有者编码"
                  value={isEditing ? (editedData.ownerCode ?? '') : (modelInfo?.ownerCode ?? '')}
                  isEditing={isEditing}
                  onChange={(value) => handleFieldChange('ownerCode', value)}
                />
                <InfoField
                  label="所有者名称"
                  value={isEditing ? (editedData.ownerName ?? '') : (modelInfo?.ownerName ?? '')}
                  isEditing={isEditing}
                  onChange={(value) => handleFieldChange('ownerName', value)}
                />

                {/* 表单底部操作按钮 */}
                {isEditing && (
                  <div className="flex items-center justify-end gap-2 pt-4 mt-4 border-t border-border">
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={handleCancel}
                      className="cursor-pointer"
                    >
                      取消
                    </Button>
                    <Button
                      variant="default"
                      size="sm"
                      onClick={handleSave}
                      className="cursor-pointer"
                    >
                      保存
                    </Button>
                  </div>
                )}
              </CardContent>
            </CollapsibleContent>
          </div>
        </Card>
      </Collapsible>
    </>
  )
}
