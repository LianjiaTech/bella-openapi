# Execution Plan for #850

## 目标

为 `PollBatchStatusWorker` 增加独立的 yml 配置开关，默认开启，支持通过配置关闭，使不需要批处理状态轮询的部署环境可灵活禁用该 worker。

## 非目标

- 不修改 `PollBatchStatusWorker` 本身的轮询逻辑
- 不修改 `SingleWorker` / `BatchWorker` 的启动逻辑
- 不调整 `bella.openapi.as-worker.enabled` 总开关行为
- 不增加运行时动态开关（仅启动时读取配置）

## 验收标准

1. 默认配置下（未显式设置 `poll-batch-status-enabled`），`PollBatchStatusWorker` 正常启动
2. 配置 `bella.openapi.as-worker.poll-batch-status-enabled: false` 后，`PollBatchStatusWorker` 不启动
3. 不影响 `WorkerManager` 中已有的 single/batch worker 刷新逻辑
4. 应用销毁时，已启动的 `PollBatchStatusWorker` 仍能正常 stop
5. 日志中明确输出 worker 是否启动的状态信息

## 约束

- 配置项命名遵循现有 `bella.openapi.as-worker.*` 命名风格
- 默认值为 `true`，保持向后兼容
- 仅在 `WorkerManager` 组件中读取配置，不引入新的 Spring Bean

## 变更范围

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `api/server/src/main/java/com/ke/bella/openapi/worker/WorkerManager.java` | 修改 | 增加配置字段，条件启动 PollBatchStatusWorker |
| `api/server/src/main/resources/application.yml` | 修改 | 增加 `poll-batch-status-enabled: true` 默认配置 |

## 实现思路

### Step 1: 在 WorkerManager 中增加配置字段

**文件**: `api/server/src/main/java/com/ke/bella/openapi/worker/WorkerManager.java`

在现有 `@Value` 注解字段区域（约第 72-73 行 `batchWorkerEnabled` 附近）增加：

```java
@Value("${bella.openapi.as-worker.poll-batch-status-enabled:true}")
private boolean pollBatchStatusEnabled;
```

**验证**: 编译通过，字段能正确注入。

### Step 2: 修改 init() 方法，按配置决定是否启动

**文件**: `api/server/src/main/java/com/ke/bella/openapi/worker/WorkerManager.java`

将 `@PostConstruct init()` 方法中 `PollBatchStatusWorker` 启动逻辑从无条件启动改为条件启动：

```java
if (pollBatchStatusEnabled) {
    pollBatchStatusWorker = PollBatchStatusWorker.builder()
            .openAiService(openAiService)
            .adaptorManager(adaptorManager)
            .build();
    pollBatchStatusWorker.start();
    log.info("Started PollBatchStatusWorker");
} else {
    log.info("PollBatchStatusWorker is disabled by configuration");
}
```

**验证**: 配置为 true 时 worker 启动；配置为 false 时 worker 不启动且有日志输出。

### Step 3: 在 application.yml 中增加默认配置

**文件**: `api/server/src/main/resources/application.yml`

在 `bella.openapi.as-worker` 节点下增加：

```yaml
bella:
  openapi:
    as-worker:
      enabled: true
      poll-batch-status-enabled: true
```

**验证**: 配置文件格式正确，YAML 解析无误。

### Step 4: 确认 destroy() 方法无需修改

**文件**: `api/server/src/main/java/com/ke/bella/openapi/worker/WorkerManager.java`

现有 `@PreDestroy destroy()` 方法已有 `if (pollBatchStatusWorker != null)` 判断（第 207 行），当 worker 未启动时 `pollBatchStatusWorker` 字段保持 null，destroy 逻辑天然兼容，无需修改。

**验证**: 确认 pollBatchStatusWorker 字段在未启动时为 null，destroy 方法安全跳过。

## 风险与依赖

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 默认值设为 true，现有环境行为不变 | 低 | 保持向后兼容，无需修改现有部署配置 |
| 配置项拼写错误导致静默使用默认值 | 低 | `@Value` 注解中指定默认值 `true`，即使配置缺失也保持原有行为 |
| destroy 等待逻辑中 pollBatchStatusWorker 为 null | 低 | 现有代码已有 null check（第 207、221 行），无需额外处理 |

**依赖**: 无外部依赖，仅涉及 Spring `@Value` 注入和条件判断。

## 验证方式

1. **编译验证**: `cd api/ && mvn clean compile` 确认无编译错误
2. **配置验证**: 检查 application.yml 格式正确性
3. **逻辑验证**: 代码审查确认：
   - 默认值为 true（向后兼容）
   - false 时不创建 PollBatchStatusWorker 实例
   - destroy 方法在 worker 为 null 时安全跳过
4. **日志验证**: 启动日志中应输出 worker 启动或禁用状态
