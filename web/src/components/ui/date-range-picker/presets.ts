import { DateRangePreset } from "./types"

/**
 * 默认日期范围预设选项
 *
 * 提供常用的时间范围快速选择选项
 * 各模块可以直接使用或根据业务需求自定义
 */
export const DEFAULT_DATE_RANGE_PRESETS: DateRangePreset[] = [
  {
    label: "最近15分钟",
    getValue: () => {
      const end = new Date()
      const start = new Date()
      start.setMinutes(start.getMinutes() - 15)
      return { from: start, to: end }
    },
  },
  {
    label: "最近30分钟",
    getValue: () => {
      const end = new Date()
      const start = new Date()
      start.setMinutes(start.getMinutes() - 30)
      return { from: start, to: end }
    },
  },
  {
    label: "最近1小时",
    getValue: () => {
      const end = new Date()
      const start = new Date()
      start.setMinutes(start.getMinutes() - 60)
      return { from: start, to: end }
    },
  },
  {
    label: "最近6小时",
    getValue: () => {
      const end = new Date()
      const start = new Date()
      start.setMinutes(start.getMinutes() - 360)
      return { from: start, to: end }
    },
  },
  {
    label: "最近12小时",
    getValue: () => {
      const end = new Date()
      const start = new Date()
      start.setMinutes(start.getMinutes() - 12 * 60)
      return { from: start, to: end }
    },
  },
  {
    label: "最近24小时",
    getValue: () => {
      const end = new Date()
      const start = new Date()
      start.setMinutes(start.getMinutes() - 24 * 60)
      return { from: start, to: end }
    },
  },
]
