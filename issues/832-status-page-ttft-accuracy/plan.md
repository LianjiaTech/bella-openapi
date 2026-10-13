# Execution Plan for #832: 状态页平均 TTFT 统计逻辑不准确

## 目标

修复状态监控页顶部卡片"平均 TTFT"的计算逻辑，使其：

1. 仅使用 `ttft` 数据，不再混入 `ttlt`
2. 每个采样点的 `ttft` 已经是该时间段内所有请求 TTFT 的总和，直接求和后除以总完成数即可
3. 单渠道筛选与全部渠道模式使用统一计算逻辑

## 非目标

- 不新增"平均 TTLT"指标卡片（可作为后续 Issue）
- 不修改后端 metrics 接口返回的数据结构
- 不重构 `useChannels` hook 的整体架构
- 不修改图表（chartData）中 ttft/ttlt 的展示逻辑

## 验收标准

1. 顶部"平均 TTFT"只使用 `ttft` 数据，不再混入 `ttlt`
2. 计算公式：`avgTtft = sum(metrics.ttft) / sum(metrics.completed)`（`metrics.ttft` 为该采样点所有请求 TTFT 的总和）
3. 选择单个渠道时，平均 TTFT 与该渠道数据一致
4. 选择全部渠道时，平均 TTFT 使用全部渠道的请求量加权汇总
5. `completed <= 0` 或 `ttft` 缺失的时间点不参与计算
6. `avgTtft` 为 0 时正确显示 "0ms"

## 约束

- 仅修改前端代码，不涉及后端变更
- 变更文件限于 `web_v2/src/app/[locale]/(dashboard)/status/hooks/useChannels.ts`
- 保持 `summary` 对象的接口不变（`totalRequests`、`totalRequestTooMany`、`totalErrors`、`avgTtft`）
- 保持 `MetricsSummary.tsx` 组件无需修改（仅消费 summary 数据）
- 不引入新依赖

## 变更范围

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `web_v2/src/app/[locale]/(dashboard)/status/hooks/useChannels.ts` | 修改 | 修复 summary 计算逻辑中 avgTtft 的算法 |

## 实现思路

### Step 1: 移除 ttlt 混入并改为加权平均

**文件**: `web_v2/src/app/[locale]/(dashboard)/status/hooks/useChannels.ts`

**当前逻辑** (L115-L138):
```ts
const ttftValues: number[] = []

metricsData.forEach((timePoint) => {
  if (!channelCode || channelCode === "") {
    // ...
    if (timePoint.metrics.ttft) ttftValues.push(timePoint.metrics.ttft)
    if (timePoint.metrics.ttlt) ttftValues.push(timePoint.metrics.ttlt)  // BUG: ttlt 混入
  } else {
    // ...
    if (timePoint.metrics.ttft) ttftValues.push(timePoint.metrics.ttft)
    if (timePoint.metrics.ttlt) ttftValues.push(timePoint.metrics.ttlt)  // BUG: ttlt 混入
  }
})
const avgTtft = ttftValues.length > 0
  ? Math.round(ttftValues.reduce((sum, v) => sum + v, 0) / ttftValues.length)
  : 0
```

**修复后逻辑**:
```ts
let totalCompleted = 0
let totalErrors = 0
let totalRequestTooMany = 0
let totalTtft = 0

metricsData.forEach((timePoint) => {
  const metrics = (() => {
    if (!channelCode || channelCode === "") {
      return timePoint.metrics
    }
    if (timePoint?.channel_code === channelCode) {
      return timePoint.metrics
    }
    return null
  })()

  if (!metrics) return

  totalCompleted += metrics.completed || 0
  totalErrors += metrics.errors || 0
  totalRequestTooMany += metrics.request_too_many || 0
  totalTtft += metrics.ttft || 0
})

const avgTtft = totalCompleted > 0
  ? Math.round(totalTtft / totalCompleted)
  : 0
```

**关键变更点**:
1. 删除所有 `ttftValues.push(timePoint.metrics.ttlt)` 行
2. 每个采样点的 `ttft` 已经是总和，直接累加后除以总 `completed` 数
3. 统一全部渠道和单渠道的计算逻辑，消除重复代码

**验证方式**:
- 在浏览器中打开状态页，观察"平均 TTFT"数值是否合理
- 对比选择单渠道 vs 全部渠道时的数值变化
- 构造测试场景：某时间点 completed=0，确认不影响平均值
- 使用 DevTools 断点调试 `weightedTtftSum` 和 `totalCompletedForTtft` 确认计算正确

### Step 2: 前端 lint 检查

```bash
cd web_v2 && npm run lint
```

确保修改后无 lint 错误。

## 风险与依赖

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| `metrics.ttft` 含义已确认为采样点内所有请求 TTFT 总和 | 无 | 直接使用 `sum(ttft) / sum(completed)` |
| 修改后数值与旧数据对比变化大 | 用户可能误以为系统异常 | 属正常修复，修复后数据更准确反映实际体验 |
| `completed` 字段可能为 0 导致除零 | 计算异常 | 已在逻辑中用 `totalCompletedForTtft > 0` 守卫 |

**依赖**: 无外部依赖，纯前端逻辑修改。

## 验证方式

1. **手动验证**: 启动 `web_v2` dev server，进入状态页，对比修复前后"平均 TTFT"数值
2. **边界验证**: 确认无数据时显示 "0ms"，所有时间点 completed=0 时显示 "0ms"
3. **Lint 检查**: `cd web_v2 && npm run lint` 通过
4. **回归验证**: 确认 summary 的 `totalRequests`、`totalErrors`、`totalRequestTooMany` 未受影响
