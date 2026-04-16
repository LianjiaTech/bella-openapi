/**
 * 优先级选项
 */
export const PRIORITY_OPTIONS = [
  { value: 'high', label: '高优先级' },
  { value: 'normal', label: '中等优先级' },
  { value: 'low', label: '低优先级' },
] as const

/**
 * 队列模式选项
 */
export const QUEUE_MODE_OPTIONS = [
  { value: '0', label: 'None无队列' },
  { value: '1', label: 'Pull模式' },
  { value: '2', label: 'Route模式' },
  { value: '3', label: 'Pull+Route模式' },
] as const

/**
 * 数据流向选项
 */
export const DATA_DESTINATION_OPTIONS = [
  { value: 'protected', label: '内部已备案' },
  { value: 'inner', label: '内部' },
  { value: 'mainland', label: '国内' },
  { value: 'overseas', label: '海外' },
] as const

/**
 * 表单初始值
 */
export const INITIAL_FORM_DATA = {
  url: '',
  protocol: '',
  supplier: '',
  dataDestination: '',
  priceInfo: '',
  priority: '',
  channelInfo: '',
  trialEnabled: 0,
  queueMode: 0,
  queueName: '',
} as const
