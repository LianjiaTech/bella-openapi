# Execution Plan for #870

## 目标

修复元数据管理页面新建/编辑模型后模型列表未正确刷新的 bug。确保 `refetch()` 调用后模型列表数据被正确更新，用户无需手动刷新页面。

## 非目标

- 不重构元数据管理页面的整体架构
- 不修改模型创建/编辑的 API 接口
- 不调整其他页面的刷新逻辑
- 不引入新的状态管理方案

## 验收标准

1. 新建模型成功后，模型列表自动刷新并展示新模型
2. 编辑模型成功后，模型列表自动刷新并展示更新后的数据
3. 刷新时保留当前筛选条件（endpoint、tags、status、visibility）
4. 错误重试按钮点击后列表正常刷新
5. 无 console 错误或异常行为

## 约束

- 仅修改 `web/src/app/[locale]/(dashboard)/metadata/hooks/index.ts` 文件
- 保持现有 API 调用接口不变
- 保持 `fetchAndUpdateState` 函数签名不变
- 不引入新依赖

## 变更范围

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `web/src/app/[locale]/(dashboard)/metadata/hooks/index.ts` | 修复 | 修复 `fetchAndUpdateState` 中 `shouldUpdateFeatures=true` 分支的模型列表更新逻辑 |

## 实现思路

### Step 1: 修复 `shouldUpdateFeatures=true` 分支逻辑

**文件**: `web/src/app/[locale]/(dashboard)/metadata/hooks/index.ts` 第 125-133 行

**操作**:
1. 移除无用的 `Promise.all` 包装（只有单个请求，无需并行）
2. 将返回值正确赋给 `modelsData` 变量
3. 取消 `setModels(modelsData)` 的注释，确保模型列表被更新
4. 移除已注释的 `setFeatures(featuresData)` 死代码

**修改后代码**:
```typescript
if (shouldUpdateFeatures) {
  setInitialLoading(true)
  const modelsData = await fetchModels(endpoint, tags, status, visibility)
  setEndpointInfo(endpoint)
  setModels(modelsData)
}
```

**验证**:
- 在浏览器中进入元数据管理页面
- 选择一个 endpoint，确认模型列表加载正常
- 新建一个模型，确认列表自动包含新模型
- 切换 endpoint，确认列表正确刷新

### Step 2: 验证错误重试场景

**操作**: 无代码修改，仅验证

**验证**:
- 模拟网络错误（如断开后端），确认错误提示出现
- 点击重试按钮，确认调用 `refetch()` 后列表正常恢复

## 风险与依赖

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| `setModels` 调用可能触发不必要的重渲染 | 低 - 这是修复原本就应有的调用 | `else` 分支已有相同调用且运行正常 |
| 移除 `Promise.all` 后行为差异 | 无 - 只有单个 Promise，行为完全等价 | 对比 `else` 分支已验证此模式可行 |

**依赖**: 无外部依赖，修复完全局限于前端状态管理逻辑。

## 验证方式

1. **手动验证**（主要）:
   - 启动前端 dev server (`cd web && npm run dev`)
   - 访问元数据管理页面
   - 执行新建模型 → 确认列表刷新
   - 执行编辑模型 → 确认列表刷新
   - 切换筛选条件后新建模型 → 确认筛选条件保留且列表刷新
   - 触发错误后点击重试 → 确认列表恢复

2. **代码审查**:
   - 确认 `else` 分支逻辑未被改动
   - 确认 `finally` 中 loading 状态清理逻辑无影响
   - 确认 `refetch` 函数调用链路（page.tsx → hooks → fetchAndUpdateState）完整
