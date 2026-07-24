import { useReducer, useCallback } from "react"
import { CustomFilter, CustomFilterValues, CustomFilterAction } from "../types"

/**
 * 自定义筛选器 Reducer
 */
function customFilterReducer(
  state: CustomFilterValues,
  action: CustomFilterAction
): CustomFilterValues {
  switch (action.type) {
    case 'SET_VALUE':
      return {
        ...state,
        [action.filterId]: action.value,
      }
    case 'RESET':
      return {}
    case 'INIT':
      // 从 filters 配置中初始化默认值
      return action.filters.reduce<CustomFilterValues>((acc, filter) => {
        if (filter.defaultValue !== undefined) {
          acc[filter.id] = filter.defaultValue
        } else {
          // 根据类型初始化默认值
          acc[filter.id] = filter.type === 'multiple' ? [] : ''
        }
        return acc
      }, {})
    default:
      return state
  }
}

/**
 * 自定义筛选器状态管理 Hook
 * 使用 useReducer 管理自定义筛选器的状态
 */
export function useCustomFilters(filters: CustomFilter[]) {
  const [values, dispatch] = useReducer(
    customFilterReducer,
    filters,
    (initialFilters) => customFilterReducer({}, { type: 'INIT', filters: initialFilters })
  )

  const setValue = useCallback((filterId: string, value: string | string[]) => {
    dispatch({ type: 'SET_VALUE', filterId, value })
  }, [])

  const reset = useCallback(() => {
    dispatch({ type: 'RESET' })
  }, [])

  return {
    values,
    setValue,
    reset,
  }
}
