# Execution Plan: RocksDB Secondary Instance Mode for LogReader (#860)

## 目标

将 `RocksDBLogReader` 从 `RocksDB.openReadOnly()` + 短 TTL 缓存模式改为 `RocksDB.openAsSecondary()` 长生命周期实例模式，消除频繁重建实例带来的大对象加载开销、GC 压力和读取延迟。

## 非目标

- 不修改 `RocksDBLogRepo`（Writer）的写入逻辑
- 不修改 RocksDB 版本（当前 rocksdbjni 7.10.2 已支持 Secondary Instance）
- 不修改对外 API 接口（`/v1/log/detail` 等保持不变）
- 不修改前端代码
- 不涉及分片策略或清理策略变更

## 验收标准

1. `RocksDBLogReader` 使用 `RocksDB.openAsSecondary()` 打开实例，不再使用 `openReadOnly()`
2. 读取活跃分片时通过 `tryCatchUpWithPrimary()` 增量追赶，无需 close + reopen
3. 移除 `isActiveShardCandidate` 和 retry-on-miss 中的 invalidate + reopen 逻辑
4. Secondary 实例缓存 TTL 可配置且默认值显著大于当前 60s（建议 30min 或更长）
5. 每个 Secondary 实例的 `secondaryPath` 目录正确创建，路径规则为 `{dbPath}_secondary/`
6. `RocksDBCleanupTask` 清理过期分片时同时清理对应 `_secondary` 目录
7. 实例关闭时（`@PreDestroy`）正确释放所有 Secondary 句柄
8. 功能与当前行为一致：给定 requestId + shardPath 能正确读取日志

## 约束

- Java 8 兼容
- rocksdbjni 7.10.2 兼容（已支持 `openAsSecondary`）
- `secondaryPath` 目录磁盘占用极小（MANIFEST + 少量 log），不影响现有磁盘规划
- 不引入新的外部依赖
- 保持 `RocksDBLogReader` 的 Spring Bean 生命周期管理不变

## 变更范围

| 文件 | 变更类型 | 说明 |
|------|---------|------|
| `api/server/src/main/java/com/ke/bella/openapi/db/log/RocksDBLogReader.java` | 重构 | 核心改动：openReadOnly → openAsSecondary，移除 retry-on-miss 的 invalidate 逻辑，改用 tryCatchUpWithPrimary |
| `api/server/src/main/java/com/ke/bella/openapi/db/log/RocksDBProperties.java` | 修改 | 新增 `readerSecondarySuffix` 配置项（默认 `_secondary`），调整 `readerCacheTtlSeconds` 默认值 |
| `api/server/src/main/java/com/ke/bella/openapi/db/log/RocksDBCleanupTask.java` | 修改 | 清理过期分片时同步删除对应 `_secondary` 目录 |
| `api/server/src/main/resources/application.yml` | 修改 | 更新 `bella.log.rocksdb.reader-cache-ttl-seconds` 默认值文档 |

## 实现思路

### Step 1: 修改 RocksDBProperties 增加配置项

**文件**: `api/server/src/main/java/com/ke/bella/openapi/db/log/RocksDBProperties.java`

**操作**:
1. 新增字段 `private String readerSecondarySuffix = "_secondary";`
2. 将 `readerCacheTtlSeconds` 默认值从 `60` 改为 `1800`（30 分钟）

**验证**: 编译通过，配置注入正常。

### Step 2: 重构 RocksDBLogReader 核心逻辑

**文件**: `api/server/src/main/java/com/ke/bella/openapi/db/log/RocksDBLogReader.java`

**操作**:

1. **修改 `openReadOnly` 方法为 `openAsSecondary`**:
   - 方法签名改为 `openAsSecondary(String dbPath)`
   - 计算 `secondaryPath = dbPath + properties.getReaderSecondarySuffix()`
   - 创建 `secondaryPath` 目录（如不存在）
   - 调用 `RocksDB.openAsSecondary(options, dbPath, secondaryPath)` 替代 `RocksDB.openReadOnly(options, dbPath)`
   - `Options` 配置保持不变（BloomFilter、createIfMissing=false）

2. **修改 `readByShardPath` 方法**:
   - 移除 `isActiveShardCandidate` 判断和整个 retry-on-miss 分支
   - 在 `getFromDB` 内部首先调用 `db.tryCatchUpWithPrimary()` 进行增量追赶
   - 如果 `get` 返回 null，不再做 invalidate + reopen，直接返回 null

3. **简化 `getFromDB` 方法**:
   - 从缓存获取 `CachedDB` 实例（未命中时通过 `openAsSecondary` 创建）
   - 调用 `cachedDB.db.tryCatchUpWithPrimary()`
   - 调用 `cachedDB.db.get(keyBytes)`
   - 异常处理：如果 `tryCatchUpWithPrimary` 或 `get` 抛出 `RocksDBException`，记录日志并 invalidate 该缓存条目（实例可能已损坏），返回 null

4. **移除不再需要的代码**:
   - 删除 `isActiveShardCandidate` 方法
   - 删除 `cacheOpenTimes` 字段及相关逻辑
   - 删除 `rocksDBLogRepo` 的 `@Autowired` 依赖

5. **更新 `CachedDB` 内部类**:
   - 保持不变（`db.close()` 对 Secondary 实例同样有效）

6. **更新日志信息**:
   - 缓存 removalListener 日志从 "Closed ReadOnly RocksDB handle" 改为 "Closed Secondary RocksDB handle"

**验证**: 单元测试（见 Step 4），并确认编译无警告。

### Step 3: 修改 RocksDBCleanupTask 清理 secondary 目录

**文件**: `api/server/src/main/java/com/ke/bella/openapi/db/log/RocksDBCleanupTask.java`

**操作**:
1. 注入 `RocksDBProperties` 以获取 `readerSecondarySuffix`
2. 在 `cleanup()` 方法中，删除过期 `shardDir` 后，检查并删除同级的 `{shardDir.getName()}_secondary` 目录（如果存在）
3. 遍历 `shardDir` 内子目录（instance 目录）时，同步清理各 instance 子目录下的 `_secondary` 后缀目录

注意：Secondary path 位于各 instance 目录级别，路径格式为 `{basePath}/{shardId}/{instanceId}_secondary/`。需要在删除 instance 目录前先清理其对应 secondary 目录。实际上由于是递归删除整个 shard 目录，secondary 子目录也会被一并删除，但需确保 `logReader.invalidateCache` 在删除前调用以释放句柄。

**验证**: 人工验证清理逻辑覆盖 secondary 目录。

### Step 4: 验证与测试

**操作**:
1. 编写/更新单元测试验证 Secondary 实例行为：
   - 测试 `openAsSecondary` 能正确打开已有的 RocksDB 目录
   - 测试 `tryCatchUpWithPrimary` 后能读取到 Writer 新写入的数据
   - 测试 Primary 目录不存在时的容错处理
   - 测试缓存驱逐后重新打开的正确性
2. 确保 `mvn clean compile` 通过
3. 确保现有测试不受影响

## 风险与依赖

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| `tryCatchUpWithPrimary()` 在 Primary 写入密集时耗时增加 | 活跃分片读取延迟偶尔升高 | 监控 catchup 耗时，必要时考虑异步 catchup 或限流 |
| Secondary 实例在 Primary 做 compaction 时可能短暂不可用 | 读取返回错误 | 捕获异常后 invalidate 缓存条目，下次请求重建实例 |
| `secondaryPath` 目录权限问题 | Secondary 实例无法创建 | 确保 secondaryPath 与 dbPath 同一父目录，继承权限 |
| 长生命周期实例累积文件描述符 | fd 泄漏 | 通过 Caffeine TTL 驱逐确保不无限增长；监控 fd 使用 |

**依赖**:
- rocksdbjni 7.10.2 已包含 `RocksDB.openAsSecondary()` API（无需升级）
- `RocksDBLogRepo`（Writer）必须保持 WAL 开启（当前 `setDisableWAL(false)`，已满足）

## 验证方式

1. **编译验证**: `cd api && mvn clean compile` 通过
2. **单元测试**: 新增 `RocksDBLogReaderSecondaryTest` 验证 Secondary 实例的创建、catchup、读取流程
3. **集成验证**: 本地启动服务，写入日志后通过 `/v1/log/detail` 接口读取，验证行为一致
4. **性能对比**: 对比改造前后活跃分片的读取延迟（预期从 5-50ms 降至 <2ms）
5. **稳定性验证**: 观察 GC 日志，确认大对象分配频率下降
