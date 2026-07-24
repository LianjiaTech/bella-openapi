import { useState, useCallback } from 'react'
import type { ChannelFormData, FormErrors, DialogMode } from '../types'
import type { ChannelDetails } from '@/lib/types/metadata'
import { buildFormDataFromInitial } from '../utils/formHelpers'
import { validateChannelForm } from '../utils/validation'
import { INITIAL_FORM_DATA } from '../constants'

interface UseChannelFormProps {
  initialData?: Partial<ChannelDetails>
  mode: DialogMode
}

/**
 * 表单状态管理 Hook
 * 负责管理表单数据、验证错误、字段变更等逻辑
 */
export function useChannelForm({ initialData, mode }: UseChannelFormProps) {
  // 表单数据状态
  const [formData, setFormData] = useState<ChannelFormData>(() =>
    buildFormDataFromInitial(initialData)
  )

  // 验证错误状态
  const [errors, setErrors] = useState<FormErrors>({})

  /**
   * 重置表单到初始状态
   */
  const resetForm = useCallback(() => {
    setFormData({ ...INITIAL_FORM_DATA })
    setErrors({})
  }, [])

  /**
   * 重新初始化表单（用于编辑模式）
   */
  const initializeForm = useCallback((data?: Partial<ChannelDetails>) => {
    setFormData(buildFormDataFromInitial(data))
    setErrors({})
  }, [])

  /**
   * 更新单个字段值
   */
  const updateField = useCallback(
    (field: keyof ChannelFormData, value: any) => {
      setFormData((prev) => ({
        ...prev,
        [field]: field === 'queueMode' ? Number(value) : value,
      }))

      // 清除该字段的错误
      if (errors[field]) {
        setErrors((prev) => ({
          ...prev,
          [field]: undefined,
        }))
      }
    },
    [errors]
  )

  /**
   * 批量更新多个字段
   */
  const updateFields = useCallback(
    (updates: Partial<ChannelFormData>) => {
      setFormData((prev) => ({
        ...prev,
        ...updates,
      }))

      // 清除被更新字段的错误
      const updatedFieldKeys = Object.keys(updates) as Array<
        keyof ChannelFormData
      >
      if (updatedFieldKeys.some((key) => errors[key])) {
        setErrors((prev) => {
          const newErrors = { ...prev }
          updatedFieldKeys.forEach((key) => {
            delete newErrors[key]
          })
          return newErrors
        })
      }
    },
    [errors]
  )

  /**
   * 验证表单
   * @returns 是否通过验证
   */
  const validate = useCallback((): boolean => {
    const newErrors = validateChannelForm(formData, mode)
    setErrors(newErrors)
    return Object.keys(newErrors).length === 0
  }, [formData, mode])

  /**
   * 清除指定字段的错误
   */
  const clearError = useCallback((field: keyof ChannelFormData) => {
    setErrors((prev) => {
      const newErrors = { ...prev }
      delete newErrors[field]
      return newErrors
    })
  }, [])

  /**
   * 清除所有错误
   */
  const clearAllErrors = useCallback(() => {
    setErrors({})
  }, [])

  return {
    formData,
    errors,
    resetForm,
    initializeForm,
    updateField,
    updateFields,
    validate,
    clearError,
    clearAllErrors,
  }
}
