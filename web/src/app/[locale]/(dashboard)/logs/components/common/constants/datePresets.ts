import { DateRangePreset } from "@/components/ui/date-range-picker"

/**
 * 生成日志模块自定义时间范围预设项
 *
 * 包含:
 * - 分钟级: 5/10/20/30分钟
 * - 小时级: 1/2/6/12小时
 * - 天级: 7天/30天
 * - 相对日期: 今天、昨天、本周、本月
 */
export const getLogDatePresets = (t: (key: string) => string): DateRangePreset[] => [
  // 分钟级预设
  {
    label: t("logs.filter.presets.last5Minutes"),
    getValue: () => {
      const end = new Date()
      const start = new Date()
      start.setMinutes(start.getMinutes() - 5)
      return { from: start, to: end }
    },
  },
  {
    label: t("logs.filter.presets.last10Minutes"),
    getValue: () => {
      const end = new Date()
      const start = new Date()
      start.setMinutes(start.getMinutes() - 10)
      return { from: start, to: end }
    },
  },
  {
    label: t("logs.filter.presets.last20Minutes"),
    getValue: () => {
      const end = new Date()
      const start = new Date()
      start.setMinutes(start.getMinutes() - 20)
      return { from: start, to: end }
    },
  },
  {
    label: t("logs.filter.presets.last30Minutes"),
    getValue: () => {
      const end = new Date()
      const start = new Date()
      start.setMinutes(start.getMinutes() - 30)
      return { from: start, to: end }
    },
  },
  // 小时级预设
  {
    label: t("logs.filter.presets.last1Hour"),
    getValue: () => {
      const end = new Date()
      const start = new Date()
      start.setHours(start.getHours() - 1)
      return { from: start, to: end }
    },
  },
  {
    label: t("logs.filter.presets.last2Hours"),
    getValue: () => {
      const end = new Date()
      const start = new Date()
      start.setHours(start.getHours() - 2)
      return { from: start, to: end }
    },
  },
  {
    label: t("logs.filter.presets.last6Hours"),
    getValue: () => {
      const end = new Date()
      const start = new Date()
      start.setHours(start.getHours() - 6)
      return { from: start, to: end }
    },
  },
  {
    label: t("logs.filter.presets.last12Hours"),
    getValue: () => {
      const end = new Date()
      const start = new Date()
      start.setHours(start.getHours() - 12)
      return { from: start, to: end }
    },
  },
  // 天级预设
  {
    label: t("logs.filter.presets.last7Days"),
    getValue: () => {
      const end = new Date()
      const start = new Date()
      start.setDate(start.getDate() - 7)
      return { from: start, to: end }
    },
  },
  {
    label: t("logs.filter.presets.last30Days"),
    getValue: () => {
      const end = new Date()
      const start = new Date()
      start.setDate(start.getDate() - 30)
      return { from: start, to: end }
    },
  },
  // 相对日期预设
  {
    label: t("logs.filter.presets.today"),
    getValue: () => {
      const end = new Date()
      const start = new Date()
      start.setHours(0, 0, 0, 0)
      return { from: start, to: end }
    },
  },
  {
    label: t("logs.filter.presets.yesterday"),
    getValue: () => {
      const end = new Date()
      end.setDate(end.getDate() - 1)
      end.setHours(23, 59, 59, 999)

      const start = new Date()
      start.setDate(start.getDate() - 1)
      start.setHours(0, 0, 0, 0)

      return { from: start, to: end }
    },
  },
  {
    label: t("logs.filter.presets.thisWeek"),
    getValue: () => {
      const end = new Date()
      const start = new Date()

      // 获取本周一 (周日为0, 周一为1)
      const day = start.getDay()
      const diff = day === 0 ? -6 : 1 - day // 如果是周日,往前推6天;否则推到周一
      start.setDate(start.getDate() + diff)
      start.setHours(0, 0, 0, 0)

      return { from: start, to: end }
    },
  },
  {
    label: t("logs.filter.presets.thisMonth"),
    getValue: () => {
      const end = new Date()
      const start = new Date()
      start.setDate(1)
      start.setHours(0, 0, 0, 0)
      return { from: start, to: end }
    },
  },
]
