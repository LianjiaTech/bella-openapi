import type { ChannelDetails, JsonSchema } from '@/lib/types/metadata'

/**
 * 表单数据类型
 */
export interface ChannelFormData {
  url: string
  protocol: string
  supplier: string
  dataDestination: string
  priceInfo: string
  priority: string
  channelInfo: string
  trialEnabled: number
  queueMode: number
  queueName: string
}

/**
 * 表单错误类型
 */
export type FormErrors = Partial<Record<keyof ChannelFormData, string>>

/**
 * Dialog 模式
 */
export type DialogMode = 'create' | 'edit'

/**
 * 主 Dialog Props
 */
export interface ChannelConfigDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  mode: DialogMode
  initialData?: Partial<ChannelDetails>
  modelName?: string
  onSuccess?: () => void
}

/**
 * 协议选项
 */
export interface ProtocolOption {
  value: string
  label: string
}

/**
 * Schema 数据状态
 */
export interface SchemaDataState {
  priceInfoSchema: JsonSchema
  priceInfoValues: Record<string, any>
  priceInfoLoading: boolean
  priceInfoError: string | null
  channelInfoSchema: JsonSchema
  channelInfoValues: Record<string, any>
  channelInfoLoading: boolean
  channelInfoError: string | null
}
