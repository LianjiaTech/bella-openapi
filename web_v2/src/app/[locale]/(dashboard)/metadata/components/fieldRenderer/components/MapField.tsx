import React, { useState } from 'react'
import { Textarea } from '@/components/common/textarea'
import type { TypeSchema } from '@/lib/types/metadata'

interface MapFieldProps {
  schema: TypeSchema
  value: any
  onChange: (value: any) => void
  error?: string
}

export const MapField = ({ schema, value, onChange, error }: MapFieldProps) => {
  const [textValue, setTextValue] = useState<string>(() =>
    value ? JSON.stringify(value, null, 2) : ''
  )
  const [jsonError, setJsonError] = useState<string>('')

  // 当外部 value 变化时同步更新 textValue
  React.useEffect(() => {
    if (value !== undefined && value !== null) {
      try {
        const newTextValue = JSON.stringify(value, null, 2)
        if (newTextValue !== textValue) {
          setTextValue(newTextValue)
          setJsonError('')
        }
      } catch (err) {
        // 外部 value 无法序列化,保持当前输入
      }
    }
  }, [value])

  const handleChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    const inputValue = e.target.value
    setTextValue(inputValue)

    // 如果输入为空,清除错误并重置值
    if (inputValue.trim() === '') {
      setJsonError('')
      onChange(undefined)
      return
    }

    // 尝试解析 JSON,但不阻止输入
    try {
      const parsed = JSON.parse(inputValue)
      onChange(parsed)
      setJsonError('')
    } catch (err: any) {
      // 只设置错误提示,不阻止用户继续输入
      setJsonError(`JSON 格式错误: ${err.message}`)
    }
  }

  const handleBlur = () => {
    // 失焦时再次校验,确保最终状态是正确的
    if (textValue.trim() === '') {
      setJsonError('')
      return
    }

    try {
      const parsed = JSON.parse(textValue)
      onChange(parsed)
      setJsonError('')
      // 格式化显示
      setTextValue(JSON.stringify(parsed, null, 2))
    } catch (err: any) {
      setJsonError(`JSON 格式错误: ${err.message}`)
    }
  }

  return (
    <div className="text-left">
      <Textarea
        className={`w-full placeholder:text-sm ${jsonError ? 'border-red-600' : ''}`}
        value={textValue}
        onChange={handleChange}
        onBlur={handleBlur}
        placeholder={`输入 ${schema.name} 的 JSON (支持逐行输入)`}
        rows={5}
      />
      {(error || jsonError) && (
        <p className="mt-1 text-xs text-red-600">{error || jsonError}</p>
      )}
    </div>
  )
}
