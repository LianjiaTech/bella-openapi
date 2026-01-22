/**
 * 主题相关常量配置
 * 包含颜色方案、标签样式等主题配置
 */

/**
 * 标签颜色配置数组
 * 使用 Tailwind CSS 类名定义不同颜色主题
 * 
 * 这些类名使用 Tailwind CSS 的颜色系统，包含：
 * - 背景色（10% 透明度）
 * - 文本颜色
 * - 边框颜色（20% 透明度）
 */
export const TAG_COLORS = [
    "bg-green-500/10 text-green-500 border-green-500/20",
    "bg-blue-500/10 text-blue-500 border-blue-500/20",
    "bg-purple-500/10 text-purple-500 border-purple-500/20",
    "bg-orange-500/10 text-orange-500 border-orange-500/20",
    "bg-pink-500/10 text-pink-500 border-pink-500/20",
    "bg-indigo-500/10 text-indigo-500 border-indigo-500/20",
    "bg-cyan-500/10 text-cyan-500 border-cyan-500/20",
    "bg-red-500/10 text-red-500 border-red-500/20",
    "bg-yellow-500/10 text-yellow-500 border-yellow-500/20",
    "bg-lime-500/10 text-lime-500 border-lime-500/20",
    "bg-emerald-500/10 text-emerald-500 border-emerald-500/20",
    "bg-teal-500/10 text-teal-500 border-teal-500/20",
    "bg-sky-500/10 text-sky-500 border-sky-500/20",
    "bg-violet-500/10 text-violet-500 border-violet-500/20",
    "bg-fuchsia-500/10 text-fuchsia-500 border-fuchsia-500/20",
    "bg-rose-500/10 text-rose-500 border-rose-500/20",
    "bg-amber-500/10 text-amber-500 border-amber-500/20",
    "bg-slate-500/10 text-slate-500 border-slate-500/20",
  ] as const
  
  /**
   * FNV-1a 哈希算法实现
   *
   * FNV-1a (Fowler-Noll-Vo) 是一种非加密哈希算法，特点：
   * - 对于短字符串有优秀的分布均匀性
   * - 雪崩效应好（单字符变化会显著改变哈希值）
   * - 在标签数量超过颜色数量时，能最小化碰撞聚集
   *
   * @param str - 输入字符串
   * @returns 无符号32位哈希值
   */
  function fnv1aHash(str: string): number {
    let hash = 2166136261 // FNV-1a offset basis (32-bit)

    for (let i = 0; i < str.length; i++) {
      hash ^= str.charCodeAt(i)           // XOR with byte
      hash = Math.imul(hash, 16777619)    // Multiply by FNV-1a prime
    }

    return hash >>> 0 // Convert to unsigned 32-bit integer
  }

  /**
   * 根据标签内容获取稳定的颜色样式
   * 使用 FNV-1a 哈希算法确保同一标签始终映射到相同颜色
   * 相比简单哈希，FNV-1a 在标签数量增长时提供更均匀的颜色分布
   *
   * @param tag - 标签字符串
   * @returns Tailwind CSS 颜色类名
   *
   * @example
   * getTagColor('vision') // 总是返回相同的颜色
   * getTagColor('streaming') // 总是返回相同的颜色
   */
  export function getTagColor(tag: string): string {
    if (!tag) return TAG_COLORS[0]

    const hash = fnv1aHash(tag)
    const index = hash % TAG_COLORS.length
    return TAG_COLORS[index]
  }
  
  /**
   * 根据数组索引获取颜色(用于列表场景)
   *
   * @param index - 数组索引
   * @returns Tailwind CSS 颜色类名
   *
   * @deprecated 建议使用 getTagColor 以获得基于内容的稳定颜色映射
   *
   * @example
   * getTagColorByIndex(0) // 返回第一个颜色
   * getTagColorByIndex(18) // 自动循环,返回第一个颜色
   */
  export function getTagColorByIndex(index: number): string {
    return TAG_COLORS[index % TAG_COLORS.length]
  }