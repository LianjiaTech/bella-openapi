# Execution Plan for #855

## 目标

降低渠道健康大盘 `/channel-health` 页面的 Prometheus 查询扇出，减少首屏、筛选、刷新场景下 `/api/prometheus` 的并发请求数量，同时保持所有数据仍来自 Prometheus（不引入 mock 或前端推断）。

## 非目标

- 不新增后端 Java 代码或 Prometheus batch 聚合接口（属后续演进）
- 不修改 Prometheus 指标定义或录入逻辑
- 不重构大盘 UI 布局或视觉样式
- 不引入服务端缓存层（TTL 缓存留作后续迭代）

## 验收标准

1. 首屏 `/api/prometheus` 请求数从 ~21 条降至 ≤10 条
2. 模型/供应商筛选输入不会逐字符触发查询（debounce ≥ 400ms）
3. 未展开渠道详情时，不查询 TTFT bucket、TPS bucket 等诊断指标
4. 定时刷新与手动刷新仅刷新当前视图必要数据（核心指标 + 当前已展开详情）
5. 页面数据全部来自 Prometheus，无 mock、无前端推断
6. 无功能回归：聚合视图、详情面板、排序、分页、timeline 仍正常工作

## 约束

- 仅修改 `web/` 目录前端代码
- 不增加新的后端 API route（除可能的 batch proxy 外暂不做）
- 保持现有 TypeScript 类型接口兼容
- 保持 Next.js App Router 结构不变

## 变更范围

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `web/src/lib/api/channel-health.ts` | 重构 | 拆分为核心查询 + 详情查询两个函数 |
| `web/src/lib/types/channel-health.ts` | 修改 | 扩展类型支持懒加载字段为 `MetricValue`（已有 `unreleased`/`no_data` 状态，增加 `loading` 状态） |
| `web/src/components/channel-health/ChannelHealthDashboard.tsx` | 重构 | 分离核心数据加载与详情懒加载逻辑；筛选 debounce；刷新仅触发核心查询 |
| `web/src/lib/api/prometheus.ts` | 微调 | 无实质变更，保持不变 |
| `web/src/app/api/prometheus/route.ts` | 不动 | 本次不做 batch 合并 |

## 实现思路

### Step 1: 定义查询分层

将现有 21 条查询分为两类：

**核心查询（首屏必需，≤9 条）：**
1. `requestDistribution` — 请求分布（by status_class）
2. `requestTotal` — 请求总量（by channel group）
3. `requestRate` — 全局 RPM
4. `tokenTotal` — 总 Token
5. `tokenTpm` — Token TPM
6. `outputTokenTotal` — Output Token 总量
7. `outputTokenTpm` — Output Token TPM
8. `outputTokenTpmByChannel` — 每渠道 Output TPM
9. `timeline` — 时间线采样（range query）

**详情查询（展开渠道时按需加载，12 条）：**
- `ttftP50Result`, `ttftP95Result` — 每渠道 TTFT
- `groupTtftP50Result`, `groupTtftP95Result` — 聚合组 TTFT
- `tpsP50Result`, `tpsP95Result` — 每渠道 TPS
- `groupTpsP50Result`, `groupTpsP95Result` — 聚合组 TPS
- `inputBucketP50`, `inputBucketP95` — Input Token Bucket
- `outputBucketP50`, `outputBucketP95` — Output Token Bucket

**验证**：首屏网络面板仅出现 ≤10 条 `/api/prometheus` 请求。

### Step 2: 拆分 `getChannelHealthDashboardData`

在 `web/src/lib/api/channel-health.ts` 中：

1. 将现有函数拆为 `getCoreData(filters, signal)` 和 `getDetailData(filters, signal)`
2. `getCoreData` 返回 `ChannelHealthDashboardData`，详情字段（TTFT/TPS/Bucket）标记为 `loading` 或 `no_data`
3. `getDetailData` 返回详情指标 map，由调用方合并到已有 rows 上
4. 导出一个兼容函数 `getChannelHealthDashboardData` 调用 `getCoreData`（避免大范围改名）

**验证**：单元级 — 调用 `getCoreData` 确认仅发起 9 个 fetch；调用 `getDetailData` 确认发起 12 个 fetch。

### Step 3: 扩展类型支持 `loading` 状态

在 `web/src/lib/types/channel-health.ts` 中：

- 在 `MetricValue` 联合类型中增加 `{ status: "loading" }` 分支
- 在 Dashboard 组件中 `metricText` 对 `loading` 状态展示 spinner 或 "加载中"

**验证**：TypeScript 编译通过，`metricText` 对 `loading` 返回占位文本。

### Step 4: Dashboard 组件分离核心/详情加载

在 `ChannelHealthDashboard.tsx` 中：

1. 主 `useEffect` 仅调用 `getCoreData`
2. 新增 `useEffect` 监听 `selectedId` / `expandedGroups` 变化，当有渠道被选中或分组展开时，触发 `getDetailData` 并合并到 `data`
3. 详情数据加载期间，相关字段展示 loading 占位

**验证**：展开一个渠道详情 → Network 面板出现详情查询请求；折叠后不再触发。

### Step 5: 筛选输入 debounce

在 `ChannelHealthDashboard.tsx` 中：

1. 引入 debounce 逻辑（自写或使用 `setTimeout`/`useRef` pattern）
2. 模型和供应商 `Input` 的 `onChange` 仅更新本地显示值
3. debounce 400ms 后才更新 `filters` state 触发查询
4. 清空输入或按 Enter 时立即触发

**验证**：快速输入 "gpt-4" 共 5 个字符 → Network 面板仅出现 1 次查询（而非 5 次）。

### Step 6: 定时刷新优化

在 `ChannelHealthDashboard.tsx` 中：

1. 定时 `refreshNonce` 变化仅触发核心查询
2. 如果当前有展开的详情渠道，刷新后自动触发对应详情查询
3. 未展开时跳过详情查询

**验证**：设置 5s 自动刷新 → 每次刷新仅出现核心查询请求数。

### Step 7: 聚合组 TTFT/TPS 处理

聚合组（Group）级别的 TTFT P50/P95、TPS P50/P95 当前由 `groupQuantile` 查询获得。策略：

1. 将 group-level quantile 查询（4 条）归入详情查询
2. 聚合视图表头不显示 TTFT/TPS 列（当前表格已无此列，仅在展开详情时使用）
3. 展开分组时一并加载 group quantile

**验证**：首屏无 group quantile 请求；展开分组后出现。

## 风险与依赖

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 详情懒加载导致展开渠道时短暂无数据 | 用户体验有 loading 闪烁 | 展示 skeleton/loading 占位，保持布局稳定 |
| debounce 与 AbortController 交互可能导致 stale 请求覆盖新结果 | 数据不一致 | 每次 effect 清理时 abort 前一个 controller |
| 核心查询与详情查询的数据合并时机 | state 更新竞态 | 详情数据合并使用 functional setState，基于最新 rows |
| 分离后 `buildGroups` 缺少 TTFT/TPS 数据 | 聚合组 metric 字段为空 | 初始赋 `loading` 状态，详情加载后 merge |

## 验证方式

1. **手动验证（主要）**：
   - `npm run dev` 启动前端
   - 打开 Chrome DevTools Network 面板
   - 访问 `/channel-health`，统计首屏 `/api/prometheus` 请求数 ≤ 10
   - 输入筛选文字，确认 debounce 生效（仅 1 次查询）
   - 展开渠道详情，确认触发额外查询
   - 设置自动刷新，确认仅刷新核心指标

2. **TypeScript 编译检查**：
   - `npm run build` 或 `npx tsc --noEmit` 确认类型安全

3. **Lint 检查**：
   - `npm run lint` 无新增 error

4. **回归验证**：
   - 切换聚合维度（模型/供应商/Host/渠道资源）正常
   - 分页、排序正常
   - Timeline 采样条正常渲染
   - 详情面板 bucket 表格、趋势图正常
