# Execution Plan for #872

## 目标

在前端日志查询页的请求日志详情中，为请求体（Request）和响应体（Response）内容区域添加"复制"和"下载"操作按钮，方便用户快速获取完整的请求/响应 JSON 数据。

## 非目标

- 不修改后端 API 逻辑
- 不改变现有日志数据的获取方式或存储格式
- 不重构现有组件结构或样式系统
- 不涉及 bella logs（链路查询）详情页的改造（该页面使用通用 DataField 组件，结构不同于 openapi logs 的 RequestPanel/ResponsePanel）

## 验收标准

1. openapi 日志详情抽屉中，请求体和响应体区域各有"复制"和"下载"按钮
2. 点击"复制"按钮，将完整的格式化 JSON 内容复制到系统剪贴板，并给出短暂的成功/失败提示
3. 点击"下载"按钮，将完整的格式化 JSON 内容以 `.json` 文件下载到本地，文件名包含 requestId 以便追溯
4. 按钮在无数据（空状态）时不显示
5. bella logs 详情页（链路查询）中 JSON 字段也支持复制功能

## 约束

- 仅修改前端代码（`web/` 目录）
- 复用项目已有的 UI 组件体系（lucide-react 图标、shadcn/ui 风格）
- 使用浏览器原生 `navigator.clipboard` API 进行复制，`Blob` + `URL.createObjectURL` 进行下载
- 不引入新的第三方依赖

## 变更范围

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `web/src/app/[locale]/(dashboard)/logs/components/openapiLogsContent/components/logsTable/components/components/requestPanel.tsx` | 修改 | 添加复制和下载按钮 |
| `web/src/app/[locale]/(dashboard)/logs/components/openapiLogsContent/components/logsTable/components/components/responsePanel.tsx` | 修改 | 添加复制和下载按钮 |
| `web/src/app/[locale]/(dashboard)/logs/components/bellaLogsServiceContent/bellaLogsTable/components/LogDetailDrawer.tsx` | 修改 | 为 JSON 字段添加复制按钮 |
| `web/src/lib/utils/clipboard.ts`（新增） | 新增 | 复制和下载的工具函数 |

## 实现思路

### Step 1: 创建复制/下载工具函数

**文件**: `web/src/lib/utils/clipboard.ts`

创建两个工具函数：
- `copyToClipboard(text: string): Promise<boolean>` — 使用 `navigator.clipboard.writeText`，返回成功/失败
- `downloadAsJson(content: string, filename: string): void` — 创建 Blob 并触发下载

**验证方式**: 单独导入函数在浏览器 console 中测试

### Step 2: 为 RequestPanel 添加复制和下载按钮

**文件**: `requestPanel.tsx`

1. 在 CardHeader 区域（与 Badge 同行）添加操作按钮组：Copy 图标按钮 + Download 图标按钮
2. 复制/下载内容源: 使用 `formatted` 变量（完整的格式化 JSON 文本），而非 `displayText`（UI 折叠后的截断显示内容）
3. 添加 `copied` state 控制复制成功的视觉反馈（图标短暂变为 Check）
4. 下载文件名格式: `request-{requestId}.json`，无 requestId 时使用时间戳
5. 无数据时（`!requestData`）不渲染按钮

**验证方式**: 
- 打开日志详情，点击复制按钮，在文本编辑器中粘贴验证内容正确
- 点击下载按钮，验证文件已下载且内容正确

### Step 3: 为 ResponsePanel 添加复制和下载按钮

**文件**: `responsePanel.tsx`

实现方式与 Step 2 相同：
1. 在 CardHeader 区域添加 Copy + Download 按钮
2. 复制/下载内容源: 使用 `formatted` 变量（完整格式化 JSON 文本），而非 `displayText`（UI 折叠后的截断显示内容）
3. 下载文件名格式: `response-{requestId}.json`
4. 无数据时（空状态占位 UI）不渲染按钮

**验证方式**: 同 Step 2

### Step 4: 为 bella logs 详情页 JSON 字段添加复制按钮

**文件**: `bellaLogsServiceContent/bellaLogsTable/components/LogDetailDrawer.tsx`

1. 在 `DataField` 组件的 JSON 展示模式中（`isJSON` 为 true 时），在 label 右侧添加复制图标按钮
2. 取消注释并实现已有的 `{/* <CopyButton value={stringValue} /> */}` 逻辑（代码中已有注释占位）
3. 对非 JSON 字段，也恢复已注释的 CopyButton

**验证方式**: 打开链路查询详情，验证 JSON 字段旁有复制按钮，点击后内容正确复制

### Step 5: UI 交互细节

- 复制成功后图标从 `Copy` 变为 `Check`，2 秒后恢复
- 按钮使用 `ghost` 样式，不抢占视觉焦点
- 按钮添加 `title` 属性提供 hover 提示（"复制" / "下载"）
- 按钮尺寸使用 `h-7 w-7` 配合 `h-3.5 w-3.5` 图标

**验证方式**: 启动 dev server (`npm run dev`)，在浏览器中测试完整交互流程

## 风险与依赖

| 风险 | 级别 | 缓解措施 |
|------|------|----------|
| `navigator.clipboard` 在非 HTTPS 环境不可用 | 低 | 生产环境为 HTTPS；开发环境 localhost 也支持；添加 fallback 提示 |
| 大型日志数据复制/下载可能影响性能 | 低 | JSON 内容已在内存中（formatted 变量），无额外计算开销 |
| 下载功能在部分移动端浏览器兼容性问题 | 低 | 本系统为内部管理平台，主要使用桌面浏览器 |

## 验证方式

1. **功能验证**: 启动前端 dev server，进入日志查询页，打开日志详情
   - 验证复制按钮在请求体和响应体区域可见
   - 验证复制内容完整且格式正确（JSON pretty-print）
   - 验证下载文件内容正确、文件名包含 requestId
2. **边界验证**:
   - 空数据时不显示按钮
   - 非 JSON 文本数据也可正常复制/下载
3. **代码检查**: `npm run lint` 通过
