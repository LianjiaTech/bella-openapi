# Execution Plan for #831

## 目标

修复 `EndpointLogger` 中 RingBuffer `LogEvent` 复用时 `costOnly` 字段未重置的 bug，确保每次日志事件发布前该字段处于正确状态。

## 非目标

- 不重构 `EndpointLogger` 或 Disruptor 日志框架的整体架构
- 不修改 `LogEvent` 的其他字段（`data`、`repositoryCode` 每次都会被 `set` 覆盖，无此问题）
- 不引入新的 reset/clear 方法或生命周期钩子

## 验收标准

1. `EndpointLogger.log()` 中，在设置 `costOnly` 条件判断前显式重置 `event.setCostOnly(false)`
2. 连续请求中，非 batch/overrideInnerLog 请求不会因复用 RingBuffer 槽位而被错误标记为 `costOnly=true`
3. 单元测试覆盖以下场景：
   - 请求 A 触发 `costOnly=true`，紧接着请求 B 不满足条件时 `costOnly` 为 `false`
   - 请求满足条件时 `costOnly` 仍为 `true`

## 约束

- 修改范围限制在 `EndpointLogger.java` 单个文件
- 不改变 Disruptor RingBuffer 的使用模式（预分配+复用）
- 不改变 `LogEvent` 类结构
- 保持向后兼容，不影响其他 `LogEventHandler` 消费逻辑

## 变更范围

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `api/server/src/main/java/com/ke/bella/openapi/protocol/log/EndpointLogger.java` | 修改 | 在条件判断前添加 `event.setCostOnly(false)` |
| `api/server/src/test/java/.../protocol/log/EndpointLoggerTest.java` | 新增 | 添加单元测试验证 costOnly 重置行为 |

## 实现思路

### Step 1: 修复 EndpointLogger.log() 方法

**文件**: `api/server/src/main/java/com/ke/bella/openapi/protocol/log/EndpointLogger.java`

**变更**: 在第 43 行条件判断前插入一行重置语句。

修改前：
```java
LogEvent event = ringBuffer.get(sequence);
event.setData(log);
event.setRepositoryCode(logRepo);
if(log.isOverrideInnerLog() || log.isBatch()) {
    log.setInnerLog(true);
    event.setCostOnly(true);
}
ringBuffer.publish(sequence);
```

修改后：
```java
LogEvent event = ringBuffer.get(sequence);
event.setData(log);
event.setRepositoryCode(logRepo);
event.setCostOnly(false);
if(log.isOverrideInnerLog() || log.isBatch()) {
    log.setInnerLog(true);
    event.setCostOnly(true);
}
ringBuffer.publish(sequence);
```

**验证**: 编译通过 (`mvn clean compile -pl server`)

### Step 2: 添加单元测试

**文件**: `api/server/src/test/java/com/ke/bella/openapi/protocol/log/EndpointLoggerTest.java`

**测试用例**:

1. `testCostOnlyResetOnReuse` - 模拟 RingBuffer 返回同一个 LogEvent 实例，第一次请求设置 costOnly=true，第二次请求不满足条件时验证 costOnly 被重置为 false
2. `testCostOnlySetWhenConditionMet` - 验证满足 batch/overrideInnerLog 条件时 costOnly 仍正确设置为 true

**验证**: 测试通过 (`mvn test -pl server -Dtest=EndpointLoggerTest`)

## 风险与依赖

| 风险 | 等级 | 缓解措施 |
|------|------|----------|
| 额外的 `setCostOnly(false)` 调用增加极微量性能开销 | 低 | boolean 赋值开销可忽略，且在高频 Disruptor 路径中远小于 I/O 开销 |
| 测试需要 mock Disruptor RingBuffer | 低 | 使用 Mockito mock `RingBuffer` 返回固定 `LogEvent` 实例 |

**依赖**: 无外部依赖变更

## 验证方式

1. **编译验证**: `mvn clean compile -pl server` 成功
2. **单元测试**: `mvn test -pl server -Dtest=EndpointLoggerTest` 通过
3. **回归验证**: `mvn test -pl server` 全量测试通过（需数据库环境）
4. **人工验证**: 部署后观察日志系统，确认非 batch 请求不再出现 costOnly 异常标记
