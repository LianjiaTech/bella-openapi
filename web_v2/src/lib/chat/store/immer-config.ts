/**
 * Immer Configuration
 *
 * 职责：
 * 1. 启用 Immer 的 MapSet 插件
 * 2. 使 Zustand store 能够正确处理 Set 和 Map 数据结构
 * 3. 在应用启动时自动执行初始化
 *
 * 设计说明：
 * - 使用副作用导入方式（side-effect import）
 * - 在任何 store 创建之前执行
 * - 独立文件便于维护和测试
 *
 * 避免 re-render：
 * - 纯配置代码，不涉及 React 组件
 * - 一次性执行，无状态更新
 */

import { enableMapSet } from 'immer'

// 启用 Immer 的 MapSet 插件，使其支持 Map 和 Set 数据结构
enableMapSet()

// 导出空对象以触发模块加载
export {}
