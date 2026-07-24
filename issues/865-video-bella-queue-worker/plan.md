# Execution Plan for #865

> refactor: 视频生成接入 Bella Queue 并复用现有 Worker 结构

## 目标

- `/v1/videos` 创建任务时投递到 Bella Queue，而非本地 Redis 队列。
- Worker 复用现有 `WorkerManager -> SingleWorker -> TaskProcessor` 生命周期，不新建独立 Worker 类。
- `TaskProcessor` 按 `endpoint` 分发，新增 `VideoTaskProcessor` 处理 `/v1/videos` queue task。
- 查询/取消/内容获取改为直接调用 Bella Queue API，不再依赖 `video_job` 表状态。
- 移除旧视频本地队列运行时代码（`VideoJobExecutor`、`VideoJobSubmitTask`、`VideoJobSyncTask`、`VideoJobException`、`VideoJobQueues`、`JobQueue`、`RedisJobQueue`）。
- `QueueClient` 补充 cancel 能力。

## 非目标

- 不删除 `video_job` 表、相关 SQL 或 jOOQ 数据模型。
- 不实现 `/v1/messages`、`/v1/responses` 的 queue worker 逻辑。
- 不修改 Bella Queue 服务端。
- 不做数据库 migration。

## 验收标准

1. `POST /v1/videos` 创建任务后，任务进入 Bella Queue；返回的 video id 为 Bella Queue `taskId`。
2. `GET /v1/videos/{id}` 通过 Bella Queue task detail 查询状态，映射为 `VideoJob` DTO。
3. `GET /v1/videos/{id}/content` 在任务完成后通过 `boundFileId` 重定向到文件 URL。
4. `DELETE /v1/videos/{id}` 调用 Bella Queue cancel API。
5. OpenAPI 不再启动 `VideoJobExecutor` / 本地 Redis video queue。
6. `WorkerManager` 仍是唯一 worker 生命周期入口。
7. 现有 chat queue worker 行为不发生语义变化。
8. 不修改历史 SQL 文件，不删除 `video_job` jOOQ 数据模型。

## 约束

- Java 8 + Spring Boot 2.3.x 兼容。
- 不引入新的 Maven 依赖。
- `QueueClient` 为单例，仅在其上新增 `cancelTask` 方法，不改变已有接口签名。
- `TaskProcessor` 重构须保持现有 chat 逻辑的行为不变（仅搬位置，不改语义）。
- Worker 通过 channel 的 `queueMode` 和 `endpoint` 区分文本/视频任务。

## 变更范围

### 新增文件

| 文件 | 说明 |
|------|------|
| `api/server/.../worker/EndpointTaskProcessor.java` | endpoint processor 抽象接口 |
| `api/server/.../worker/TextTaskProcessor.java` | 承载原 chat/completions queue worker 逻辑 |
| `api/server/.../worker/VideoTaskProcessor.java` | 处理 `/v1/videos` queue task |

### 修改文件

| 文件 | 说明 |
|------|------|
| `api/spi/.../queue/QueueClient.java` | 新增 `cancelTask(taskId, apikey)` 方法 |
| `api/server/.../worker/TaskProcessor.java` | 重构为 dispatcher，按 task.endpoint 分发给 EndpointTaskProcessor |
| `api/server/.../worker/SingleWorker.java` | builder 注入 videoFileService 等依赖供 VideoTaskProcessor 使用 |
| `api/server/.../worker/WorkerManager.java` | 传入 videoFileService 等新依赖 |
| `api/server/.../endpoints/VideoController.java` | 重构为调用 Bella Queue put/detail/cancel |
| `api/server/.../service/VideoService.java` | 重构为 Bella Queue 协议封装（创建、查询、取消） |

### 删除文件

| 文件 | 说明 |
|------|------|
| `api/server/.../executor/VideoJobExecutor.java` | 旧本地视频调度器 |
| `api/server/.../executor/VideoJobSubmitTask.java` | 旧渠道提交任务 |
| `api/server/.../executor/VideoJobSyncTask.java` | 旧轮询同步任务 |
| `api/server/.../executor/VideoJobException.java` | 旧异常类 |
| `api/server/.../queue/VideoJobQueues.java` | 旧 Redis 队列 facade |
| `api/server/.../queue/JobQueue.java` | 通用 Redis list 队列接口 |
| `api/server/.../queue/RedisJobQueue.java` | Redis list 队列实现 |

### 不变更文件

- `VideoRepo`、`VideoIdGenerator`、`DSLContextHolder`、jOOQ `VideoJob*` 数据模型
- 历史 SQL 文件
- `WorkerStreamingCallback`、`BatchWorker`、`PollBatchStatusWorker`

## 实现思路

### Step 1: QueueClient 补充 cancel 能力

**文件**: `api/spi/src/main/java/com/ke/bella/queue/QueueClient.java`

**操作**:
1. 新增方法 `cancelTask(String taskId, String apikey)`，发送 POST 到 `{url}/v1/queue/{taskId}/cancel`。
2. 返回 `Task` 对象（或 void，视 Bella Queue cancel 接口返回）。

**验证**: 编写单元测试 mock HTTP 调用验证请求 URL 和 header 正确。

---

### Step 2: 创建 EndpointTaskProcessor 接口

**文件**: `api/server/src/main/java/com/ke/bella/openapi/worker/EndpointTaskProcessor.java`

**操作**:
```java
public interface EndpointTaskProcessor {
    String endpoint();
    void execute(TaskWrapper taskWrapper, ChannelDB channel, Runnable releaseSlot);
}
```

**验证**: 编译通过。

---

### Step 3: 提取 TextTaskProcessor

**文件**: `api/server/src/main/java/com/ke/bella/openapi/worker/TextTaskProcessor.java`

**操作**:
1. 从当前 `TaskProcessor.processCompletionRequest()` 提取全部逻辑。
2. 实现 `EndpointTaskProcessor`，`endpoint()` 返回 `/v1/chat/completions`。
3. 保留原有依赖注入（`AdaptorManager`、`OpenapiClient`、`chatSafetyCheckService`）。

**验证**: 现有 chat queue worker 行为不变（通过现有集成测试验证）。

---

### Step 4: 实现 VideoTaskProcessor

**文件**: `api/server/src/main/java/com/ke/bella/openapi/worker/VideoTaskProcessor.java`

**操作**:
1. 实现 `EndpointTaskProcessor`，`endpoint()` 返回 `/v1/videos`。
2. `execute()` 流程:
   - 从 `taskWrapper.getTask().getData()` 解析 `VideoCreateRequest`。
   - 使用当前 `SingleWorker` 绑定的 channel，调用 `VideoAdaptor.submitVideoTask()`。
   - 轮询 `VideoAdaptor.queryVideoTask()` 直到终态。
   - 完成后调用 `VideoAdaptor.transferVideoToFile()` 转存 file service。
   - 写回 Bella Queue result（包含 `boundFileId`、`status`、`usage`）。
3. 依赖: `AdaptorManager`、`OpenAiService`（file service）。

**验证**: 单元测试 mock adaptor 验证流程调用顺序和 result 格式。

---

### Step 5: 重构 TaskProcessor 为 dispatcher

**文件**: `api/server/src/main/java/com/ke/bella/openapi/worker/TaskProcessor.java`

**操作**:
1. 持有 `Map<String, EndpointTaskProcessor>` 注册表。
2. `executeTask()` 从 `taskWrapper.getTask()` 中获取 endpoint（或从 channel entityType 推断），分发到对应 processor。
3. 兜底: 未匹配 endpoint 抛 `UnsupportedOperationException`。
4. Builder 接收 `List<EndpointTaskProcessor>` 并构建 map。

**验证**: 现有 chat 测试通过 + video task 分发正确。

---

### Step 6: 调整 SingleWorker 和 WorkerManager 依赖

**文件**: `SingleWorker.java`、`WorkerManager.java`

**操作**:
1. `SingleWorker.start()` 构建 `TextTaskProcessor` + `VideoTaskProcessor`，传入 `TaskProcessor`。
2. `WorkerManager` 注入 `OpenAiServiceFactory` 创建的 videoFileService，传给 `SingleWorker` builder。
3. 视频 channel 的 `queueMode` 需设置为 SINGLE（由 channel 配置决定，代码无需额外判断）。

**验证**: 启动后 `WorkerManager.refreshWorkers()` 能为视频 channel 启动 SingleWorker。

---

### Step 7: 重构 VideoService 为 Bella Queue 协议封装

**文件**: `api/server/src/main/java/com/ke/bella/openapi/service/VideoService.java`

**操作**:
1. `createVideoJob()`: 使用 `ChannelRouter.route("/v1/videos", model, apikey, QueueMode.SINGLE)` 选择 channel，构建 `Put` 对象投递 Bella Queue，返回 `taskId` 作为 video id。
2. `queryVideoJob()`: 调用 `QueueClient.getTaskDetail(taskId, apikey)` 并映射为 `VideoJob` DTO。
3. `deleteVideoJob()`: 调用 `QueueClient.cancelTask(taskId, apikey)`。
4. 移除对 `VideoRepo`、`VideoJobQueues`、`EndpointLogger` 的依赖。
5. 新增对 `ChannelRouter`、`QueueClient` 的依赖。

**验证**: 接口级集成测试验证 CRUD 操作通过 Bella Queue。

---

### Step 8: 重构 VideoController

**文件**: `api/server/src/main/java/com/ke/bella/openapi/endpoints/VideoController.java`

**操作**:
1. 简化 `createVideo()`: 移除 channel 选择逻辑（下沉到 VideoService），直接调用 `vs.createVideoJob()`。
2. `retrieveVideo()`: 调用 `vs.queryVideoJob(id)` 返回 `VideoJob`（不再返回 `VideoJobDB`）。
3. `retrieveVideoContent()`: 从 `VideoJob` 获取 `boundFileId`，重定向逻辑不变。
4. `deleteVideo()`: 调用 `vs.deleteVideoJob(id)` 触发 Bella Queue cancel。
5. 移除 `videoFileService`（file service 逻辑已内聚到 Worker/VideoService）。
6. 移除 `ChannelService`、`ModelService` 直接依赖（已下沉到 VideoService）。

**验证**: curl/Postman 验证完整 CRUD 链路。

---

### Step 9: 删除旧运行时代码

**文件**: 见"删除文件"列表。

**操作**:
1. 删除 `VideoJobExecutor`、`VideoJobSubmitTask`、`VideoJobSyncTask`、`VideoJobException`。
2. 删除 `VideoJobQueues`、`JobQueue`、`RedisJobQueue`。
3. 确认无其他引用（IDE 或 `grep` 验证）。
4. 移除 `application.yml` 中 `bella.video.schedule.*` 配置项。

**验证**: 编译通过，无 import 错误；grep 确认无残留引用。

---

### Step 10: 端到端验证

1. 启动服务（Docker 或本地）。
2. `POST /v1/videos` 创建任务 -> 确认 Bella Queue 中有新 task。
3. `GET /v1/videos/{taskId}` -> 返回正确状态。
4. Worker 拾取并处理任务 -> 状态变为 completed。
5. `GET /v1/videos/{taskId}/content` -> 302 重定向到文件 URL。
6. `DELETE /v1/videos/{taskId}` -> Bella Queue task 标记为 cancelled。
7. chat queue worker 发一条 chat completion -> 行为不变。

## 风险与依赖

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| Bella Queue cancel API 不存在或接口不兼容 | `DELETE /v1/videos/{id}` 无法工作 | 预先验证 Bella Queue 是否已有 cancel endpoint；若无则本期 cancel 返回 501 |
| TaskProcessor dispatcher 引入 endpoint 字段，但现有 task 无 endpoint 信息 | Worker 无法区分 text/video task | 利用 channel 的 entityType 或 task data 中的 model 对应 endpoint 推断 |
| VideoTaskProcessor 轮询阻塞 worker 线程 | 降低 worker 并发处理能力 | 设置合理超时（如 10min），异步轮询避免阻塞主 take 循环 |
| 旧数据迁移 | 已提交的旧 video_job 数据无法查询 | 本期不删 video_job 表，对旧 id 前缀做兼容：若 id 为旧格式则查 DB |
| `listVideos` API 依赖 DB 分页 | 迁移后新任务在 Bella Queue，旧任务在 DB | 本期 list API 仍查 DB（旧数据），新数据的 list 需后续迭代支持 |

## 验证方式

- **编译验证**: `mvn clean compile` 通过，无 import 错误。
- **单元测试**: `TextTaskProcessor`、`VideoTaskProcessor` 的逻辑验证（mock adaptor + mock queue client）。
- **集成测试**: 启动完整服务，验证 video CRUD 全链路通过 Bella Queue。
- **回归验证**: chat queue worker 创建任务 -> 获取结果，行为不变。
- **监控验证**: 确认旧 `VideoJobExecutor` 定时任务不再触发（日志无 `[VideoJob] Video job executor started`）。
