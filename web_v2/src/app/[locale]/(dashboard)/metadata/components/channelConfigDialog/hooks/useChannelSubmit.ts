import { useState, useCallback } from 'react'
import { createPrivateChannel, updatePrivateChannel } from '@/lib/api/metadata'
import { buildSubmitData } from '../utils/formHelpers'
import type { ChannelFormData, DialogMode } from '../types'

interface UseChannelSubmitProps {
  mode: DialogMode
  modelName?: string
  channelCode?: string
  onSuccess?: () => void
  onError?: (error: Error) => void
}

/**
 * 提交逻辑 Hook
 * 负责处理渠道创建和更新的提交逻辑
 */
export function useChannelSubmit({
  mode,
  modelName,
  channelCode,
  onSuccess,
  onError,
}: UseChannelSubmitProps) {
  // 提交加载状态
  const [isSubmitting, setIsSubmitting] = useState(false)

  // 提交错误
  const [submitError, setSubmitError] = useState<string | null>(null)

  /**
   * 提交表单
   */
  const submit = useCallback(
    async (formData: ChannelFormData): Promise<boolean> => {
      setIsSubmitting(true)
      setSubmitError(null)
      try {
        if (mode === 'create') {
          // 新增渠道
          const submitData = buildSubmitData(formData, 'create', modelName)
          await createPrivateChannel(submitData)
          console.log('渠道创建成功')
        } else {
          // 更新渠道
          if (!channelCode) {
            throw new Error('缺少渠道编码')
          }

          const submitData = buildSubmitData(
            formData,
            'edit',
            modelName,
            channelCode
          )
          await updatePrivateChannel(channelCode, submitData)
          console.log('渠道更新成功')
        }

        // 成功回调
        onSuccess?.()
        return true
      } catch (error) {
        console.error('操作失败:', error)

        const errorMessage =
          error instanceof Error ? error.message : '操作失败，请重试'
        setSubmitError(errorMessage)

        // 错误回调
        if (error instanceof Error) {
          onError?.(error)
        } else {
          onError?.(new Error(errorMessage))
        }

        return false
      } finally {
        setIsSubmitting(false)
      }
    },
    [mode, modelName, channelCode, onSuccess, onError]
  )

  /**
   * 清除提交错误
   */
  const clearSubmitError = useCallback(() => {
    setSubmitError(null)
  }, [])

  return {
    isSubmitting,
    submitError,
    submit,
    clearSubmitError,
  }
}
