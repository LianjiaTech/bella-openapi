import { DateRange } from "react-day-picker"

/**
 * 日期范围预设选项
 */
export interface DateRangePreset {
  /** 预设选项显示文本 */
  label: string
  /** 获取日期范围的函数 */
  getValue: () => DateRange
}
