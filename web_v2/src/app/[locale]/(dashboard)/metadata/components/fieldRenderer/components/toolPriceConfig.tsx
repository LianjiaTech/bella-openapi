"use client"

import { useState } from "react"
import { Plus, Trash2 } from "lucide-react"
import { Button } from "@/components/common/button"
import { Input } from "@/components/common/input"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/common/card"
import { Label } from "@/components/common/label"

/**
 * 内部列表项类型，用于 UI 渲染
 * 职责：为每个工具价格条目提供唯一标识和编辑能力
 */
interface ToolPriceItem {
  id: string
  toolName?: string // 可选，新增时允许为空
  unitPrice?: number  // 可选，新增时允许为空
}

/**
 * 工具价格配置组件 Props
 * 职责：接收和输出扁平对象格式的工具价格映射 {"web_search": 0.05}
 */
interface ToolPriceConfigProps {
  toolPrices?: Record<string, number>
  onToolPricesChange?: (toolPrices: Record<string, number>) => void
}

/**
 * 工具价格配置组件
 * 职责：提供工具调用价格的动态配置界面
 * 设计说明：
 * 1. 外部使用对象格式 {toolName: price}，内部转为列表渲染
 * 2. 使用 useMemo 缓存转换结果，避免不必要的 re-render
 * 3. 所有更新操作直接计算新对象并通知父组件
 * 避免 re-render：
 * - useMemo 缓存对象→列表转换
 * - 使用 toolName 作为唯一 key，避免列表项重建
 */
export function ToolPriceConfig({
  toolPrices: initialToolPrices = {},
  onToolPricesChange,
}: ToolPriceConfigProps) {
  // 职责：管理内部列表状态，id 用于 React key，toolName 可为空
  // 避免 re-render：直接使用列表状态，避免对象↔列表的重复转换
  const [toolPricesList, setToolPricesList] = useState<ToolPriceItem[]>(() => {
    // 初始化：将外部对象格式转为内部列表格式
    return Object.entries(initialToolPrices).map(([toolName, unitPrice]) => ({
      id: `${toolName}_${Date.now()}`, // 唯一 ID（避免 toolName 重复导致 key 冲突）
      toolName,
      unitPrice,
    }))
  })

  // 将列表格式转换回对象格式（用于同步到父组件）
  const listToObject = (items: ToolPriceItem[]): Record<string, number> => {
    const result: Record<string, number> = {}
    items.forEach(item => {
      // 只输出有效的工具价格映射（toolName 非空且 unitPrice 有值）
      if (item.toolName?.trim() && item.unitPrice !== undefined) {
        result[item.toolName] = item.unitPrice
      }
    })
    return result
  }

  // 同步更新父组件
  const syncToParent = (newList: ToolPriceItem[]) => {
    onToolPricesChange?.(listToObject(newList))
  }

  const addToolPrice = () => {
    // 生成唯一 ID（用于 React key），toolName 默认为空字符串
    const tempId = `temp_${Date.now()}`
    const newItem: ToolPriceItem = {
      id: tempId,
      toolName: "",  // 默认为空，用户可输入
      unitPrice: undefined,  // 不设置默认值
    }
    const newList = [...toolPricesList, newItem]
    setToolPricesList(newList)
    syncToParent(newList)
  }

  const removeToolPrice = (id: string) => {
    const newList = toolPricesList.filter((t) => t.id !== id)
    setToolPricesList(newList)
    syncToParent(newList)
  }

  const updateToolPrice = (oldId: string, updates: Partial<ToolPriceItem>) => {
    const newList = toolPricesList.map((t) => {
      // 直接更新匹配项，不修改 id（id 仅用于 React key，与 toolName 无关）
      return t.id === oldId ? { ...t, ...updates } : t
    })
    setToolPricesList(newList)
    syncToParent(newList)
  }

  return (
    <Card className="border-border/60">
      <CardHeader className="pb-4">
        <div className="flex items-center justify-between">
          <CardTitle className="text-base font-medium">
            工具调用价格配置（可选）
          </CardTitle>
          <Button
            variant="outline"
            size="sm"
            onClick={addToolPrice}
            className="gap-1.5"
          >
            <Plus className="h-4 w-4" />
            添加工具价格
          </Button>
        </div>
      </CardHeader>

      <CardContent>
        {toolPricesList.length === 0 ? (
          <div className="flex items-center justify-center py-8 text-sm text-muted-foreground">
            暂无工具价格配置
          </div>
        ) : (
          <div className="space-y-4">
            {toolPricesList.map((toolPrice) => (
              <div
                key={toolPrice.id}
                className="rounded-lg border border-border/60 bg-background p-4"
              >
                <div className="flex items-end gap-4">
                  <div className="flex-1 space-y-1.5">
                    <Label className="text-xs text-muted-foreground">
                      工具名称
                    </Label>
                    <Input
                      value={toolPrice.toolName ?? ""}
                      onChange={(e) =>
                        updateToolPrice(toolPrice.id, {
                          toolName: e.target.value,
                        })
                      }
                      placeholder="如: web_search"
                    />
                  </div>
                  <div className="flex-1 space-y-1.5">
                    <Label className="text-xs text-muted-foreground">
                      单价（分/次）
                    </Label>
                    <Input
                      type="number"
                      min={0}
                      step={0.01}
                      value={toolPrice.unitPrice ?? ""}
                      onChange={(e) =>
                        updateToolPrice(toolPrice.id, {
                          unitPrice: e.target.value ? Number(e.target.value) : undefined,
                        })
                      }
                      placeholder=""
                    />
                  </div>
                  <Button
                    variant="ghost"
                    size="icon"
                    onClick={() => removeToolPrice(toolPrice.id)}
                    className="h-9 w-9 text-destructive hover:text-destructive hover:bg-destructive/10 shrink-0"
                  >
                    <Trash2 className="h-4 w-4" />
                  </Button>
                </div>
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  )
}
