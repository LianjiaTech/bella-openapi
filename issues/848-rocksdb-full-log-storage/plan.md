# Execution Plan: RocksDB Full Log Storage (#848)

## 目标

为大模型请求/响应日志提供完整存储能力：通过 RocksDB 将完整日志持久化到 PVC，在前端提供按需加载完整内容的功能，解决当前日志被截断导致无法查看完整 request/response 的问题。

## 非目标

- 不替换现有 ES 搜索链路（ES 继续存储截断版日志用于检索）
- 不修改现有 `ConsoleLogRepo` 的截断逻辑
- 不涉及 PVC 存储的运维层面配置（PV 创建、StorageClass 定义等由运维完成）
- 不涉及数据迁移（仅新写入数据进入 RocksDB）

## 验收标准

1. 所有日志完整写入 RocksDB（不截断），且 RocksDB 写入优先级最高（Disruptor 链首位）
2. 任意实例可通过 requestId + shardPath 读取其他实例写入的日志
3. ES 中存储 shardPath 字段，读取时无需依赖时间推算路径
4. 前端详情页检测到截断内容时显示"加载完整内容"按钮，点击可获取完整 request/response
5. 7 天自动清理正常运行，过期 DB 目录被删除
6. RocksDB 功能可通过配置开关关闭，关闭后系统行为与当前一致
7. inode 使用率可控（每实例/天约几百个 SST 文件）

## 约束

- Java 8 兼容（项目当前使用 Java 8）
- RocksDB 版本需兼容 Java 8（rocksdbjni 7.x 系列，8.x 需要 Java 11+）
- PVC 必须为 block storage（Ceph RBD / EBS / local SSD），不能是 NFS
- 写入不阻塞主请求处理流程（通过 Disruptor 异步写入）
- RocksDB 写入在 Disruptor handler 链中优先级最高（先于 CostLogHandler 等），确保完整数据第一时间落盘
- 读取延迟需 < 50ms（bloom filter + ReadOnly 打开 + Caffeine 缓存）
- 分片路径（shardPath）随日志一同写入 ES，读取时直接定位，不依赖时间推算

## 变更范围

### 后端（api/server）

| 文件/目录 | 变更类型 | 说明 |
|-----------|---------|------|
| `pom.xml` (server module) | 新增依赖 | 添加 rocksdbjni 依赖 |
| `com.ke.bella.openapi.db.log.RocksDBLogRepo` | 新增 | 实现 `LogRepo` 接口，写入完整日志到 RocksDB，同时将 shardPath 写回 EndpointProcessData |
| `com.ke.bella.openapi.db.log.RocksDBLogReader` | 新增 | 读取服务：通过 shardPath 直接打开对应 DB 查找 |
| `com.ke.bella.openapi.db.log.RocksDBProperties` | 新增 | 配置属性类 |
| `com.ke.bella.openapi.db.log.RocksDBCleanupTask` | 新增 | 定时清理过期 DB 目录 |
| `com.ke.bella.openapi.configuration.BellaAutoConf` | 修改 | 调整 Disruptor handler 顺序，RocksDB 写入最先执行 |
| `com.ke.bella.openapi.EndpointProcessData` (sdk) | 修改 | 新增 `shardPath` 字段 |
| `com.ke.bella.openapi.endpoints.LogController` | 修改 | 新增 GET `/v1/log/detail` 接口、POST `/v1/log/benchmark` 性能验证接口 |
| `application.yml` | 修改 | 添加 `bella.log.rocksdb.*` 配置项 |

### 前端（web/src）

| 文件/目录 | 变更类型 | 说明 |
|-----------|---------|------|
| `web/src/lib/api/logs.ts` (或对应 API 文件) | 修改 | 添加 `/v1/log/detail` 调用函数 |
| `web/src/app/[locale]/(dashboard)/logs/.../requestPanel.tsx` | 修改 | 检测截断标记，显示"加载完整内容"按钮 |
| `web/src/app/[locale]/(dashboard)/logs/.../responsePanel.tsx` | 修改 | 同上 |

## 实现思路

### Step 1: 添加 RocksDB 依赖和配置属性类

**操作**：
1. 在 `api/server/pom.xml` 中添加 `rocksdbjni` 依赖（版本 7.10.2，兼容 Java 8）
2. 创建 `RocksDBProperties` 配置类，映射 `bella.log.rocksdb.*` 配置项
3. 在 `application.yml` 中添加默认配置（enabled: false）

**配置项**：
```yaml
bella:
  log:
    rocksdb:
      enabled: false
      base-path: /pvc-mount/bella-logs
      flush-interval-ms: 1000
      retention-days: 7
      reader-cache-ttl-seconds: 60
      shard-interval: 1d              # 分片时间跨度: 5m / 1h / 2d 等
      cleanup-cron: "0 0 3 * * ?"    # 清理任务 cron 表达式
```

**分片时间跨度（shard-interval）**：

使用时间段表达式配置，格式为 `<数值><单位>`，支持 `m`（分钟）、`h`（小时）、`d`（天）：

| 配置值 | 分片目录格式 | 单分片数据量（2000条/s） | 适用场景 |
|--------|-------------|------------------------|---------|
| `1d` | `20260604/inst-xxx/` | ~1.7TB | 默认，日志量适中 |
| `1h` | `20260604-15/inst-xxx/` | ~72GB | 日志量大，需细粒度清理 |
| `5m` | `20260604-1530/inst-xxx/` | ~6GB | 极端场景，快速回收空间 |

分片跨度仅影响新分片的创建规则，已有 shardPath 不受变更影响（读取时直接按 shardPath 定位）。

**验证**：项目编译通过 `mvn clean compile`

---

### Step 2: 实现 RocksDBLogRepo（写入端）+ 调整 Disruptor 优先级

**操作**：
1. 在 `EndpointProcessData`（sdk 模块）中新增 `shardPath` 字段（String 类型，`@JsonProperty`）
2. 创建 `RocksDBLogRepo` 实现 `LogRepo` 接口
3. 写入逻辑：
   - 根据 `shard-interval` 配置生成分片目录标识（1d → `20260604`，1h → `20260604-15`，5m → `20260604-1530`）
   - 完整 DB 目录：`{base-path}/{分片标识}/inst-{ip}-{hash}/`
   - 使用实例 IP + 短 hash 标识（从 `BellaServerContextHolder.getIp()` 获取）
   - `record()` 时将当前分片相对路径（如 `2026-06-04/inst-10.0.1.1-a3f2/`）写入 `log.setShardPath()`
   - Key: `requestId` (UTF-8 bytes)
   - Value: Snappy 压缩的完整 `EndpointProcessData` JSON
   - 配置 Bloom Filter（10 bits/key）提升读取性能
4. 定时 flush：使用 `@Scheduled` 按 `flush-interval-ms` 间隔调用 `db.flush()`
5. 分片滚动：按 `shard-interval` 配置的时间跨度滚动（1d → 每天 00:00，1h → 每小时整点，5m → 每 5 分钟），关闭旧分片 DB，打开新分片目录的 DB
6. 条件装配：使用 `@ConditionalOnProperty(prefix="bella.log.rocksdb", name="enabled", havingValue="true")`
7. **调整 Disruptor handler 链顺序**（`BellaAutoConf.logRingBuffer()`）：
   - 当前：`CostLogHandler → PrometheusLogHandler → LogRecordHandler`
   - 改为：`RocksDBLogHandler`（最先执行，写入完整日志并设置 shardPath） → `CostLogHandler → PrometheusLogHandler → LogRecordHandler`
   - `RocksDBLogHandler` 是一个新的 `EventHandler<LogEvent>`，内部委托给 `RocksDBLogRepo`
   - 它在链首执行，确保后续 handler（如 ConsoleLogRepo 的截断逻辑）不影响 RocksDB 拿到的完整数据
   - 同时 shardPath 在此步骤写入 EndpointProcessData，后续 ConsoleLogRepo 序列化时会包含该字段，最终进入 ES

**关键设计**：
- `record()` 方法内不做 size 截断，直接序列化写入
- 写入失败仅记录 WARN 日志，不影响主流程
- shardPath 是相对路径（相对于 base-path），格式为 `{分片标识}/{实例标识}/`
- 未来分片策略可从"按天"改为"按小时"等，已写入的 shardPath 仍有效
- RocksDB Options: `setCreateIfMissing(true)`, `setCompression(CompressionType.SNAPPY_COMPRESSION)`, bloom filter table config

**并发安全 - 无需加锁**：
- **写入无锁**：每实例独占 DB 目录，无跨实例竞争；实例内写入在 Disruptor EventHandler 单线程中执行，无并发写
- **分片创建无锁**：分片滚动检测在同一个 Disruptor handler 线程中完成（写入前检查是否需要切换），天然串行
- **DB 引用切换**：使用 `AtomicReference<RocksDB>` 持有当前活跃 DB 引用，flush 定时任务和 handler 线程通过 atomic 读写保证可见性
- **旧分片延迟关闭**：滚动切换后旧 DB 不立即 close，等待下一次 flush 完成后再关闭，避免 flush 线程操作已关闭的 DB

**验证**：编写单元测试，验证写入和读取一致性（使用 temp 目录）；验证 shardPath 正确写入

---

### Step 3: 实现 RocksDBLogReader（读取端）

**操作**：
1. 创建 `RocksDBLogReader` 服务类
2. 读取逻辑：
   - 接收 `requestId` 和 `shardPath`（从 ES 日志记录中获取）
   - 直接拼接 `{base-path}/{shardPath}` 得到目标 DB 目录
   - 以 `RocksDB.openReadOnly()` 打开（走 Caffeine 缓存）
   - 查找 key，命中后解压 Snappy，反序列化为 `Map<String, Object>`（而非强类型 EndpointProcessData，兼容字段变更）
   - 返回完整 Map，由调用方按需取用字段
3. ReadOnly 句柄缓存：Caffeine cache，key 为 shardPath，TTL 60s
   - shardPath 天然可作为缓存 key，直接判断该分片是否已打开
   - 分片策略变更（天→小时）时，旧 shardPath 仍然有效，新路径自动创建新缓存条目
4. 缓存过期回调（removalListener）中关闭 RocksDB 句柄
5. **兜底逻辑**：当 `shardPath` 为空时（历史数据或字段缺失），回退到基于 `requestTime` 遍历该日期下所有 `inst-*` 子目录的方式

**活跃分片读取一致性**：

`RocksDB.openReadOnly()` 是时间点快照——只能看到打开时已有的 SST 文件，看不到之后 writer flush 产生的新 SST。对于当前活跃分片（writer 仍在追加数据），缓存的 ReadOnly 句柄可能读不到最新写入的 key。

**解决策略：miss 时重建（retry-on-miss）**：
1. 从缓存获取 ReadOnly 句柄，执行 `get(requestId)`
2. 若返回 null，判断该 shardPath 是否为活跃分片：
   - 判断方式：对比 shardPath 与当前实例的 writer 正在使用的 shardPath，或检查该 DB 目录的 `LOCK` 文件是否被持有（表示有 writer 活跃）
   - 更简单的方式：记录每个缓存条目的创建时间（openTime），若 `now - openTime > flush-interval-ms` 且 key 未命中，则认为可能是缓存过期导致的 miss
3. 若确认为活跃分片，**失效该缓存条目**（关闭旧句柄），重新 `openReadOnly()` 获取最新快照后重试一次
4. 第二次仍未命中则返回 null（数据确实不存在）

**为什么不用 `openAsSecondary()` + `tryCatchUpWithPrimary()`**：
- Secondary 模式每次读前需调用 `tryCatchUpWithPrimary()` 增加额外 I/O 开销
- retry-on-miss 仅在实际 miss 时才重建，大多数读请求（数据已刷盘多时）不触发重建
- 实际场景中用户从 ES 搜到日志再点详情，通常已经过数秒甚至数分钟，大概率已被 flush 且 ReadOnly 句柄已涵盖

**shardPath 方案优势**：
- 无需时间推算：直接定位，不依赖分片策略规则
- 策略可演进：从按天分片改为按小时分片时，已有数据的 shardPath 不变
- 缓存友好：直接用 shardPath 作为缓存 key，判断是否已打开 / 是否需要重新打开
- 一致性可控：活跃分片 miss 时触发重建，非活跃分片直接返回（数据不可变）

**验证**：
- 单元测试验证直接路径读取、缓存命中、key 不存在时返回 null、shardPath 为空时的兜底逻辑
- 验证活跃分片 retry-on-miss：写入后立即读取，首次 miss 后重建句柄应能读到

---

### Step 4: 新增读取 API 接口

**操作**：
1. 在 `LogController` 中新增 GET 方法：
   ```java
   @GetMapping("/detail")
   public LogDetailResponse detail(@RequestParam String requestId)
   ```
2. 返回类型为 `Map<String, Object>`：
   - RocksDB 中存储的是完整 EndpointProcessData JSON，读取时反序列化为 Map 而非强类型对象
   - 好处：字段增减不影响兼容性，前端按需取用 `request` / `response` 等 key
3. 内部流程：
   - 通过 `requestId` 查询 ES，获取该日志记录的 `shardPath` 和 `requestTime` 字段
   - 优先使用 `shardPath` 调用 `RocksDBLogReader` 直接定位
   - shardPath 为空时（历史数据），回退到 `requestTime` 遍历方式
4. 当 RocksDB 未启用或未找到数据时返回 404

**设计原则**：
- 对外 API 不暴露存储路径等内部实现细节，调用方只需提供 `requestId`
- `shardPath` 和 `requestTime` 均为内部寻址信息，由后端从 ES 索引获取
- 未来 `requestId` 如改为 snowflake 等带时间编码的方案，可直接从 ID 反解时间，进一步减少对 ES 的依赖

**验证**：启动本地服务，curl 调用验证接口响应格式

---

### Step 5: 读写性能验证接口

**背景**：系统写入速率 ~2000 条/s，单条压缩后 ~10KB，需确保 RocksDB 在实际部署环境的读写性能满足要求。提供内置 benchmark 接口，让社区用户部署后可以快速验证目标机器的存储性能水位，判断当前硬件是否满足生产要求。

**操作**：
1. 在 `LogController` 中新增性能测试接口（仅管理员可调用）：
   ```java
   @PostMapping("/benchmark")
   public BenchmarkResult benchmark(@RequestParam(defaultValue = "10000") int count,
                                    @RequestParam(defaultValue = "10240") int valueSize)
   ```
2. 接口逻辑：
   - **写入测试**：生成 `count` 条模拟日志（随机 requestId，value 为 `valueSize` 字节的随机 JSON），顺序写入当前活跃 RocksDB 分片
   - **Flush**：写入完成后执行一次 `db.flush()`
   - **读取测试**：随机抽取已写入的 100 个 requestId，执行 ReadOnly 读取（走正常 Reader 路径，含 Snappy 解压）
   - **清理**：测试完成后删除测试数据（通过 `db.delete()` 或使用独立临时分片）
3. 返回 `BenchmarkResult`：
   ```json
   {
     "writeCount": 10000,
     "writeTotalMs": 850,
     "writeOpsPerSec": 11764,
     "writeAvgLatencyUs": 85,
     "readCount": 100,
     "readTotalMs": 12,
     "readAvgLatencyUs": 120,
     "readP99LatencyUs": 450,
     "valueSizeBytes": 10240,
     "storageBackend": "/pvc-mount/bella-logs (xfs)"
   }
   ```
4. 安全限制：
   - 需要管理员权限（`@RequireAdmin` 或等效注解）
   - 限制 `count` 上限（如 100000），防止长时间阻塞
   - 使用独立测试分片 `__benchmark__/`，不污染生产数据

**验证目标**：
| 指标 | 预期 | 不可接受 |
|------|------|---------|
| 写入吞吐 | > 5000 ops/s | < 2000 ops/s |
| 写入平均延迟 | < 200μs | > 1ms |
| 读取平均延迟 | < 500μs | > 5ms |
| 读取 P99 延迟 | < 2ms | > 10ms |

---

### Step 6: 实现定时清理任务

**操作**：
1. 创建 `RocksDBCleanupTask`，cron 表达式从配置读取（`bella.log.rocksdb.cleanup-cron`，默认 `0 0 3 * * ?`）
2. 逻辑：
   - 扫描 `base-path` 下所有分片目录
   - 从目录名解析时间（兼容各种 shard-interval 格式），计算是否超过 `retention-days`
   - 超期目录：关闭所有对该目录的 ReadOnly 句柄（通过 `RocksDBLogReader` 的缓存失效方法）
   - 使用 `FileUtils.deleteDirectory()` 递归删除
3. 添加清理日志记录

**配置化清理周期的意义**：
- `shard-interval: 1d` 时，每天凌晨清理一次即可
- `shard-interval: 1h` 或更小时，可调高清理频率（如每小时一次 `0 0 * * * ?`），及时回收空间
- 运维可根据磁盘告警动态调整，无需重新部署

**验证**：单元测试验证不同 shard-interval 格式的目录名时间解析和过期判断逻辑

---

### Step 7: 前端适配 - 检测截断并加载完整内容

**操作**：
1. 在 `web/src/lib/api/` 中添加 `fetchLogDetail(requestId)` API 函数（只传 requestId）
2. 修改 `RequestPanel` 组件：
   - 检测 `requestData` 是否包含截断标记 `[REMOVED: Log size exceeded`
   - 如果是，显示"加载完整内容"按钮
   - 点击按钮调用 `fetchLogDetail(requestId)`，用完整数据替换显示
   - 加载状态：loading spinner + 错误提示
3. 修改 `ResponsePanel` 组件：同上逻辑
4. `LogDetailDrawer` 需传递 `requestId` 到子组件（shardPath 寻址由后端完成，前端无需感知）

**验证**：前端 dev server 启动后，手动测试截断日志的"加载完整内容"按钮

---

### Step 8: 集成测试和文档

**操作**：
1. 在 `application-docker.yml` 中添加 RocksDB 配置示例
2. 更新 Docker 相关配置，确保 PVC 挂载路径在 compose 中有 volume 映射
3. 验证完整流程：写入 → ES 搜索 → 点击详情 → 加载完整内容

**验证**：Docker 环境下端到端测试

## 风险与依赖

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| PVC 底层为 NFS 不兼容 RocksDB | 高 - 数据损坏 | 文档明确要求 block storage；启动时检查文件系统类型并 WARN |
| rocksdbjni 7.x 与 Java 8 兼容性 | 中 - 编译/运行失败 | 使用经过验证的 7.10.2 版本；CI 中验证 |
| 12TB/7天 磁盘空间 | 高 - 磁盘写满 | 配置磁盘使用率监控告警（80% 阈值）；retention-days 可调 |
| Snappy 压缩率不达预期 | 低 - 磁盘占用增大 | JSON 文本压缩率通常 60-80%，预估保守 |
| 实例崩溃丢失最后 1s 数据 | 低 - 可接受 | Issue 已明确"丢失可接受" |
| ReadOnly 句柄缓存导致文件句柄泄漏 | 中 - 资源耗尽 | Caffeine removalListener 中确保关闭 DB；添加 metrics 监控 |
| 日期滚动时刻写入竞争 | 低 - 短暂写入失败 | 使用 AtomicReference 做无锁切换，旧 DB 延迟关闭 |
| Disruptor 链首写入失败阻塞后续 handler | 中 - 成本/指标丢失 | RocksDBLogHandler 内部 try-catch 包裹，失败不抛异常，仅 WARN |
| 活跃分片 ReadOnly 快照过期读不到新数据 | 中 - 详情页偶现"未找到" | retry-on-miss 策略：miss 时失效缓存、重建句柄后重试一次 |

**外部依赖**：
- 运维需提供 15-20TB SSD block storage PVC
- Docker 部署需在 compose 中添加 volume 映射

## 验证方式

1. **单元测试**：RocksDBLogRepo 写入/读取一致性、RocksDBLogReader 跨目录查找、清理任务日期计算
2. **集成测试**：Docker 环境下完整写入→读取流程
3. **前端测试**：dev server 中验证截断检测和完整内容加载 UI
4. **性能验证**：本地 benchmark 验证 2000 writes/s 写入无瓶颈、读取 < 50ms
5. **功能开关验证**：`enabled: false` 时系统行为不变
