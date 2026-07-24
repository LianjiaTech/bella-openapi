# Execution Plan for #863: 修复日志详情完整内容加载失败

## 目标

修复前端日志详情页"加载完整内容"功能，使其能正确展示接口返回的完整请求体和响应体。

## 非目标

- 不修改后端 `/v1/log/detail` 接口逻辑
- 不重构 apiClient 拦截器或日志页面组件结构
- 不调整其他 API 调用函数的返回值处理方式

## 验收标准

1. 点击日志详情的"加载完整内容"按钮后，能正确展示完整的 request body
2. 点击日志详情的"加载完整内容"按钮后，能正确展示完整的 response body
3. 不再出现"未找到完整内容"的错误提示（在接口正常返回数据的情况下）
4. 现有其他使用 apiClient 的功能不受影响

## 约束

- 仅修改前端代码，不涉及后端变更
- 修复范围限制在 `fetchLogDetail` 函数及其调用链
- 保持 apiClient 拦截器的现有行为不变

## 变更范围

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `web/src/lib/api/logs.ts` | 修改 | 修复 `fetchLogDetail` 的返回值，移除多余的 `.data` 解包 |

## 实现思路

### 步骤 1：修复 `fetchLogDetail` 返回值

**文件**：`web/src/lib/api/logs.ts`

**当前代码**（第 3-8 行）：
```typescript
export async function fetchLogDetail(requestId: string, shardPath: string): Promise<Record<string, unknown>> {
  const res = await apiClient.get<Record<string, unknown>>('/v1/log/detail', {
    params: { requestId, shardPath }
  });
  return res.data;
}
```

**问题分析**：
- `apiClient` 的 response 拦截器已自动解包 `{ code: 200, data: ... }` 格式，直接返回 `data` 字段内容
- `apiClient.get<T, T>` 的类型签名表明返回值类型就是 `T`（即已解包的数据）
- `fetchLogDetail` 再次读取 `res.data`，等于在已解包的对象上取 `.data` 属性，结果为 `undefined`
- 调用方检查 `detail?.request` / `detail?.response` 时得到 `undefined`，触发"未找到完整内容"提示

**修复方案**：
```typescript
export async function fetchLogDetail(requestId: string, shardPath: string): Promise<Record<string, unknown>> {
  const res = await apiClient.get<Record<string, unknown>>('/v1/log/detail', {
    params: { requestId, shardPath }
  });
  return res;
}
```

由于 apiClient 拦截器已返回解包后的数据，`res` 本身就是 `{ request, response, ... }` 对象，直接返回即可。

**验证方式**：
1. 启动前端 dev server（`cd web && npm run dev`）
2. 进入日志页面，找到一条被截断的日志记录
3. 点击"加载完整内容"按钮，确认 request 和 response 面板正确展示完整内容
4. 检查浏览器控制台无 JS 错误
5. 确认其他日志列表、过滤、分页功能正常

## 风险与依赖

| 风险 | 等级 | 缓解措施 |
|------|------|----------|
| 其他调用 `fetchLogDetail` 的代码可能依赖旧行为 | 低 | 搜索确认仅 requestPanel 和 responsePanel 两处调用，且都期望 `{ request, response }` 结构 |
| apiClient 拦截器在某些边界条件下可能不解包 | 低 | 拦截器仅在 `code === 200` 时解包；非 200 会抛异常，已被 catch 块处理 |

## 验证方式

1. **手动验证**：在开发环境中操作日志详情页，验证完整内容加载正常
2. **代码审查**：确认 `fetchLogDetail` 的所有调用方都与新返回值兼容
3. **回归测试**：确认日志页面其他功能（列表、搜索、分页）不受影响
4. **ESLint**：运行 `npm run lint` 确认无类型或规范问题
