/**
 * 主题常量配置
 * 统一管理 UI 组件的颜色和样式
 */

/**
 * 标签颜色类名数组
 * 用于模型特性标签、快速筛选标签等场景
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
 * 根据索引获取标签颜色类名
 * @param index - 标签索引
 * @returns Tailwind CSS 类名字符串
 */
export function getTagColorClass(index: number): string {
  return TAG_COLORS[index % TAG_COLORS.length]
}
