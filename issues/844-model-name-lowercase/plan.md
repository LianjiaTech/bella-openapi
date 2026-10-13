# Execution Plan for #844

## 目标

在日志记录入口处对 `model` 字段统一转为小写，消除因大小写不一致导致的模型维度统计分裂问题。

## 非目标

- 不修改请求路由、模型匹配逻辑中的 model 字段（路由层已有自己的标准化处理）
- 不对历史日志数据做回刷
- 不修改前端展示层的模型名处理
- 不改变 API 对外响应中 model 字段的大小写

## 验收标准

1. 使用不同大小写的同一模型名（如 `GPT-4O`、`Gpt-4o`、`gpt-4o`）发起请求后，日志中的 `model` 字段统一为小写
2. 日志入库、成本统计、Prometheus 指标中的模型维度不再因大小写产生重复分组
3. 不影响请求路由、模型匹配和实际调用逻辑
4. `model` 为 null 时不抛出 NPE

## 约束

- 仅修改日志写入路径，不影响请求处理主链路
- 变更需向后兼容，不改变已有 API 行为
- 保持最小变更原则，只改必要的一行代码

## 变更范围

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `api/server/src/main/java/com/ke/bella/openapi/protocol/log/EndpointLogger.java` | 修改 | 在 `log()` 方法中对 `model` 字段做小写标准化 |

## 实现思路

### Step 1: 修改 EndpointLogger.log() 方法

**文件**: `api/server/src/main/java/com/ke/bella/openapi/protocol/log/EndpointLogger.java`  
**位置**: 第 30 行 `log()` 方法体内，在 `log.setApikey(null)` 之后

**操作**:
在第 31 行 `log.setApikey(null);` 之后添加 model 小写标准化逻辑：

```java
if (log.getModel() != null) {
    log.setModel(log.getModel().toLowerCase());
}
```

**说明**:
- 使用 null 检查避免 NPE（某些异常路径 model 可能为空）
- 在清除 apikey 之后、mock 检查之前执行，确保所有进入后续流程的日志数据都已标准化
- `toLowerCase()` 使用 JVM 默认 Locale，对英文模型名（ASCII 字符）无歧义

### Step 2: 验证

**验证方式**:
1. **单元测试**（建议后续 impl 分支补充）:
   - 构造 `EndpointProcessData`，设置 model 为 `GPT-4O`，调用 `log()`，断言日志事件中 model 为 `gpt-4o`
   - 构造 `EndpointProcessData`，设置 model 为 null，调用 `log()`，断言不抛出异常
2. **集成验证**:
   - 部署后使用不同大小写模型名发起请求
   - 查询日志表/Grafana 确认模型维度已统一

## 风险与依赖

| 风险 | 等级 | 缓解措施 |
|------|------|----------|
| 下游系统依赖日志中的原始大小写 model 名 | 低 | Issue 描述确认期望统一小写；Grafana/成本统计均按小写聚合更合理 |
| `toLowerCase()` 对非 ASCII 模型名行为 | 极低 | 当前所有模型名均为 ASCII 字符；如需严格控制可使用 `Locale.ROOT` |
| model 字段为 null 导致 NPE | 低 | 已通过 null 检查防护 |

**依赖**: 无外部依赖，变更完全自包含。

## 验证方式

1. Code Review 确认变更仅限 `EndpointLogger.log()` 方法
2. 本地启动服务，使用 curl 发送不同大小写模型名请求，检查日志输出
3. 查看 Prometheus metrics 端点确认模型维度标签统一
