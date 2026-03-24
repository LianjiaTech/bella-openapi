import React, { useState } from 'react'
import { Textarea } from '@/components/common/textarea'
import { Label } from '@/components/common/label'
import { Card, CardContent } from '@/components/common/card'
import { Button } from '@/components/common/button'
import type { TypeSchema } from '@/lib/types/metadata'
import { ChevronDown, ChevronRight, Plus, Trash2 } from 'lucide-react'
import { FieldRenderer } from '../FieldRenderer'

interface ArrayFieldProps {
  mode: 'create' | 'edit'  // 操作模式
  schema: TypeSchema
  value: any
  onChange: (value: any) => void
  error?: string
}

export const ArrayField = ({ mode, schema, value, onChange, error }: ArrayFieldProps) => {
  const [expandedItems, setExpandedItems] = useState<Set<number>>(new Set())
  const arrayValue = Array.isArray(value) ? value : []

  const addItem = () => {
    const newItem = schema.child ? {} : ''
    const newArray = [...arrayValue, newItem]
    onChange(newArray)
  }

  const removeItem = (index: number) => {
    const newArray = arrayValue.filter((_, i) => i !== index)
    onChange(newArray)
  }

  const updateItem = (index: number, newValue: any) => {
    const newArray = [...arrayValue]
    newArray[index] = newValue
    onChange(newArray)
  }

  const toggleExpanded = (index: number) => {
    const newExpanded = new Set(expandedItems)
    if (newExpanded.has(index)) {
      newExpanded.delete(index)
    } else {
      newExpanded.add(index)
    }
    setExpandedItems(newExpanded)
  }

  if (schema.child?.params) {
    return (
      <div className="space-y-2">
        <div className="flex items-center justify-between">
          <Label className="text-sm font-medium">
            {schema.name} ({arrayValue.length} 项)
          </Label>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={addItem}
            className="flex items-center gap-1"
          >
            <Plus className="h-4 w-4" />
            添加项目
          </Button>
        </div>

        {arrayValue.map((item, index) => {
          const isExpanded = expandedItems.has(index)
          return (
            <Card key={index} className="border border-gray-200">
              <CardContent className="p-3">
                <div className="mb-2 flex items-center justify-between">
                  <Button
                    type="button"
                    variant="ghost"
                    size="default"
                    onClick={() => toggleExpanded(index)}
                    className="flex items-center gap-1 p-1 h-auto"
                  >
                    {isExpanded ? (
                      <ChevronDown className="h-4 w-4" />
                    ) : (
                      <ChevronRight className="h-4 w-4" />
                    )}
                    第 {index + 1} 项
                  </Button>
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    onClick={() => removeItem(index)}
                    className="text-red-500 hover:text-red-700 p-1 h-auto"
                  >
                    <Trash2 className="h-4 w-4" />
                  </Button>
                </div>

                {isExpanded && (
                  <div className="space-y-3 pt-2 border-t border-gray-200">
                    {schema.child!.params.map((param) => (
                      <div key={param.code} className="space-y-2">
                        <Label className="text-sm font-medium text-gray-700 block">{param.name}</Label>
                        {FieldRenderer({
                          mode,
                          schema: param,
                          value: item?.[param.code],
                          onChange: (nestedValue) => {
                            const newItem = { ...item, [param.code]: nestedValue }
                            updateItem(index, newItem)
                          },
                        })}
                      </div>
                    ))}
                  </div>
                )}
              </CardContent>
            </Card>
          )
        })}

        {/* {arrayValue.length === 0 && (
          <div className="text-center py-8 text-gray-500 border-2 border-dashed border-gray-300 rounded-lg bg-gray-50">
            <p className="mb-3 text-sm">暂无项目</p>
            <Button
              type="button"
              variant="outline"
              onClick={addItem}
              className="flex items-center gap-1 mx-auto"
            >
              <Plus className="h-4 w-4" />
              添加第一个项目
            </Button>
          </div>
        )} */}
        {error && <p className="mt-1 text-xs text-red-600">{error}</p>}
      </div>
    )
  }

  return (
    <div className="text-left">
      <Textarea
        className={`w-full text-sm placeholder:text-sm ${error ? 'border-red-600' : ''}`}
        value={arrayValue.join(', ')}
        onChange={(e) =>
          onChange(
            e.target.value.split(',').map((item) => item.trim())
          )
        }
        placeholder={`输入 ${schema.name} (用逗号分隔)`}
        rows={3}
      />
      {error && <p className="mt-1 text-sm text-red-600">{error}</p>}
    </div>
  )
}
