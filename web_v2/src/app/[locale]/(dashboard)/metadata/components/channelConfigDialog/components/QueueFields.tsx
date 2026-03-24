import { Label } from '@/components/common/label'
import { Input } from '@/components/common/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/common/select'
import { QUEUE_MODE_OPTIONS } from '../constants'
import type { ChannelFormData, FormErrors } from '../types'

interface QueueFieldsProps {
  queueMode: number
  queueName: string
  errors: FormErrors
  onQueueModeChange: (value: string) => void
  onQueueNameChange: (e: React.ChangeEvent<HTMLInputElement>) => void
}

/**
 * 队列配置字段组件
 * 包含队列模式选择和条件显示的队列名称输入
 */
export function QueueFields({
  queueMode,
  queueName,
  errors,
  onQueueModeChange,
  onQueueNameChange,
}: QueueFieldsProps) {
  return (
    <div className="space-y-4">
      {/* 队列模式 */}
      <div className="space-y-2">
        <Label htmlFor="queueMode">队列模式</Label>
        <Select
          value={String(queueMode)}
          onValueChange={onQueueModeChange}
        >
          <SelectTrigger id="queueMode">
            <SelectValue placeholder="选择队列模式" />
          </SelectTrigger>
          <SelectContent>
            {QUEUE_MODE_OPTIONS.map((option) => (
              <SelectItem key={option.value} value={option.value}>
                {option.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {/* 队列名称 - 条件显示 */}
      {queueMode !== 0 && (
        <div className="space-y-2">
          <Label htmlFor="queueName">
            队列名称 <span className="text-red-600">*</span>
          </Label>
          <Input
            id="queueName"
            placeholder="输入队列名称"
            value={queueName}
            onChange={onQueueNameChange}
            className={errors.queueName ? 'border-red-600' : ''}
          />
          {errors.queueName && (
            <p className="text-sm text-red-600">{errors.queueName}</p>
          )}
        </div>
      )}
    </div>
  )
}
