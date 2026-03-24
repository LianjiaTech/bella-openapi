/**
 * 图表工具函数
 * 用于处理 MetricsDetail 组件中图表的数据格式化和刻度计算
 */

/**
 * 计算图表 X 轴刻度数量
 * @param dateRangeInMinutes - 日期范围（分钟数）
 * @param timeInterval - 时间间隔（字符串数字，如 '5', '10'）
 * @returns 刻度数量，最小为 1
 */
export function calculateTickCount(
  dateRangeInMinutes?: number,
  timeInterval?: string
): number {
  if (!dateRangeInMinutes || !timeInterval) return 0
  const count = Math.floor(+dateRangeInMinutes / +timeInterval)
  return count > 0 ? count : 1
}

/**
 * 聚合相同时间的数据，将指定字段的值累加求和
 * @param data - 原始数据数组
 * @param fields - 需要累加的数值字段名数组
 * @returns 聚合后的数据数组
 */
export function aggregateDataByTime<T extends { time: string }>(
  data: T[] | undefined,
  fields: (keyof T)[]
): T[] {
  if (!data || data.length === 0) return []

  const aggregatedMap = new Map<string, T>()

  data.forEach((item) => {
    const existing = aggregatedMap.get(item.time)

    if (existing) {
      // 累加指定字段的值
      fields.forEach((field) => {
        const existingValue = Number(existing[field]) || 0
        const currentValue = Number(item[field]) || 0
        ;(existing[field] as number) = existingValue + currentValue
      })
    } else {
      // 创建新记录（深拷贝）
      aggregatedMap.set(item.time, { ...item })
    }
  })

  // 转换为数组并返回
  return Array.from(aggregatedMap.values())
}

/**
 * 将时间字符串 "HH:MM" 转换为自零点以来的分钟数
 * @param timeStr - 时间字符串，格式为 "HH:MM"
 * @returns 分钟数（0-1439）
 */
function parseTimeToMinutes(timeStr: string): number {
  const [hours, minutes] = timeStr.split(':').map(Number)
  return (hours || 0) * 60 + (minutes || 0)
}

/**
 * 将分钟数转换回时间字符串 "HH:MM"
 * @param minutes - 分钟数
 * @returns 时间字符串
 */
function formatMinutesToTime(minutes: number): string {
  const hours = Math.floor(minutes / 60)
  const mins = minutes % 60
  return `${String(hours).padStart(2, '0')}:${String(mins).padStart(2, '0')}`
}

/**
 * 按时间间隔聚合数据（时间桶分组）
 * @param data - 原始数据数组（时间格式为 "HH:MM"）
 * @param intervalMinutes - 时间间隔（分钟数）
 * @param fields - 需要累加的数值字段名数组
 * @param avgFields - 需要计算平均值的字段名数组（如性能指标）
 * @returns 按时间桶聚合后的数据数组
 */
export function aggregateDataByInterval<T extends { time: string }>(
  data: T[] | undefined,
  intervalMinutes: number,
  fields: (keyof T)[],
  avgFields?: (keyof T)[]
): T[] {
  if (!data || data.length === 0 || intervalMinutes <= 0) return []

  // 先按精确时间去重（处理相同时间点的重复数据）
  const deduped = aggregateDataByTime(data, fields)

  // 时间桶映射
  const buckets = new Map<number, { sum: Partial<T>; count: number; items: T[] }>()

  deduped.forEach((item) => {
    // 解析时间为分钟数
    const minutesOfDay = parseTimeToMinutes(item.time)

    // 计算所属的时间桶（向下取整到最近的 interval 倍数）
    const bucketKey = Math.floor(minutesOfDay / intervalMinutes) * intervalMinutes

    if (!buckets.has(bucketKey)) {
      buckets.set(bucketKey, { sum: { time: '' } as Partial<T>, count: 0, items: [] })
    }

    const bucket = buckets.get(bucketKey)!
    bucket.count += 1
    bucket.items.push(item)

    // 累加求和字段
    fields.forEach((field) => {
      const currentValue = Number(item[field]) || 0
      const existingValue = Number(bucket.sum[field]) || 0
      ;(bucket.sum[field] as number) = existingValue + currentValue
    })

    // 收集需要计算平均值的字段
    if (avgFields) {
      avgFields.forEach((field) => {
        const currentValue = Number(item[field]) || 0
        if (currentValue > 0) {
          const existingValue = Number(bucket.sum[field]) || 0
          ;(bucket.sum[field] as number) = existingValue + currentValue
        }
      })
    }
  })

  // 转换为结果数组
  const result: T[] = []
  const sortedBuckets = Array.from(buckets.entries()).sort(([a], [b]) => a - b)

  sortedBuckets.forEach(([bucketKey, bucket]) => {
    const aggregated = { ...bucket.sum } as T

    // 设置时间为桶的起始时间
    aggregated.time = formatMinutesToTime(bucketKey)

    // 计算平均值字段
    if (avgFields && bucket.count > 0) {
      avgFields.forEach((field) => {
        const sum = Number(aggregated[field]) || 0
        // 只对非零值计算平均（避免将 0 值计入平均）
        const nonZeroCount = bucket.items.filter(item => Number(item[field]) > 0).length
        if (nonZeroCount > 0) {
          ;(aggregated[field] as number) = Math.round(sum / nonZeroCount)
        } else {
          ;(aggregated[field] as number) = 0
        }
      })
    }

    result.push(aggregated)
  })

  return result
}

/**
 * 计算 X 轴刻度点
 * 按照指定的刻度数量，从数据中均匀提取刻度点，并确保包含首尾数据点
 * @param data - 图表数据（必须包含 time 字段）
 * @param count - 期望的刻度数量
 * @returns 刻度点数组（time 值）
 */
export function calculateXAxisTicks<T extends { time: string }>(
  data: T[],
  count: number
): string[] {
  if (!data.length || !count || count <= 0) return []

  // 如果数据点数少于 count，显示所有数据点
  if (data.length < count) {
    return data.map((d) => d.time)
  }

  const ticks: string[] = []

  // 按照 count 间隔提取刻度：每隔 step 个数据点显示一个刻度
  const step = Math.floor(data.length / count)

  for (let i = 0; i < count; i++) {
    const index = i * step
    if (index < data.length) {
      ticks.push(data[index].time)
    }
  }

  // 确保最后一个数据点总是显示
  const lastTime = data[data.length - 1].time
  if (ticks[ticks.length - 1] !== lastTime) {
    ticks.push(lastTime)
  }

  return ticks
}
