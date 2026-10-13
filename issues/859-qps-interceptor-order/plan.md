# Execution Plan for #859

> 调整 QPS 限流拦截器顺序，确保鉴权后优先短路超限请求

## 目标

将 `QpsRateLimitInterceptor` 的执行顺序从 `order=109` 调整为 `order=101`，使其成为鉴权（`AuthorizationInterceptor`, order=100）之后的首个能力拦截器，超限请求立即返回 429，不再继续执行月额度检查及后续业务处理。

## 非目标

- 不修改 QPS 限流的业务逻辑（算法、阈值、Redis Lua 脚本等）
- 不变更 controller、router 或 path 覆盖范围
- 不新增 `/v1/route`、`/v1beta/models/*` 等路径覆盖
- 不调整 `MonthQuotaInterceptor` 或 `AuthorizationInterceptor` 的 order 值
- 不重构拦截器注册方式或架构

## 验收标准

1. `QpsRateLimitInterceptor` 注册的 order 值为 101
2. 拦截器链顺序为：AuthorizationInterceptor(100) → QpsRateLimitInterceptor(101) → MonthQuotaInterceptor(110)
3. 当 QPS 超限时请求直接返回 429，不触发 `MonthQuotaInterceptor`
4. 正常请求（未超限）不受影响，仍按完整拦截器链执行
5. 路径覆盖范围（`endpointPathPatterns`）不变

## 约束

- 仅修改 `WebConfig.java` 中的 order 值和注释
- 不引入新依赖、新类或新配置项
- 变更必须向后兼容，不影响其他模块

## 变更范围

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `api/server/src/main/java/com/ke/bella/openapi/configuration/WebConfig.java` | 修改 | 将 `.order(109)` 改为 `.order(101)`，更新注释 |

## 实现思路

### Step 1: 修改拦截器注册顺序

**文件**: `api/server/src/main/java/com/ke/bella/openapi/configuration/WebConfig.java`

**操作**:
1. 将第 40 行 `.order(109)` 修改为 `.order(101)`
2. 将第 37 行注释更新为 `// QPS 限流拦截器（order=101）- 在 AuthorizationInterceptor(100) 之后，MonthQuotaInterceptor(110) 之前`

**变更前**:
```java
// QPS 限流拦截器（order=109）- 在 AuthorizationInterceptor(100) 之后，MonthQuotaInterceptor(110) 之前
registry.addInterceptor(qpsRateLimitInterceptor)
    .addPathPatterns(endpointPathPatterns)
    .order(109);
```

**变更后**:
```java
// QPS 限流拦截器（order=101）- 鉴权后首个能力拦截器，超限立即返回 429
registry.addInterceptor(qpsRateLimitInterceptor)
    .addPathPatterns(endpointPathPatterns)
    .order(101);
```

**验证方式**: 编译通过 (`mvn clean compile`)

### Step 2: 验证编译

**操作**: 执行 `cd api && mvn clean compile` 确认无编译错误

**预期结果**: BUILD SUCCESS，无报错

## 风险与依赖

| 风险 | 等级 | 缓解措施 |
|------|------|----------|
| order=101 与未来新增拦截器冲突 | 低 | 100-110 区间目前只有 100 和 110 两个值，101 不会冲突 |
| 高频限流场景下行为变化 | 低 | 限流逻辑本身未变，仅执行时机提前，不影响正确性 |
| 其他模块注册了 order 介于 100-109 的拦截器 | 低 | 已全局搜索确认，当前无 101-108 区间的拦截器 |

**依赖**: 无外部依赖，变更自包含。

## 验证方式

1. **编译验证**: `cd api && mvn clean compile` 通过
2. **人工审查**: 确认拦截器注册顺序符合预期
3. **集成测试**（部署后）:
   - 发送正常请求 → 正常响应，完整经过所有拦截器
   - 触发 QPS 限流 → 返回 429，日志中无 MonthQuotaInterceptor 执行记录
