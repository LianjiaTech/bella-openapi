# Execution Plan for #864

## 目标

修复 `RocksDBLogReader` 在多实例（多 Pod）共享 PVC 时，因 `secondaryPath` 固定不变导致的 LOCK 文件竞争问题，使每个 Pod 的 Reader 拥有独立的 Secondary Instance 目录。

## 非目标

- 不修改 Writer（`RocksDBLogRepo`）的实例隔离逻辑
- 不修改 RocksDB 的打开方式（仍使用 Secondary Instance）
- 不变更日志存储目录结构
- 不修改清理任务的调度策略

## 验收标准

1. 多个 Pod 同时以 Secondary 模式读取同一 Primary DB 目录时不再抛出 `RocksDBException: IO error: lock hold by current process`
2. 每个 Pod 的 Secondary 目录路径包含实例唯一标识，格式为 `{dbPath}_secondary_{readerInstanceId}`
3. `RocksDBCleanupTask` 能正确清理包含实例标识的 Secondary 目录（通配匹配）
4. 单实例部署场景不受影响，行为向后兼容

## 约束

- 保持与 `RocksDBLogRepo` 中 `instanceId` 相同的生成策略（IP + hashCode），确保实例标识全局唯一
- Secondary 目录命名不能破坏现有目录结构的约定（位于 shard 目录同级或子目录内）
- 不引入新的外部依赖
- Java 8 兼容

## 变更范围

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `api/server/src/main/java/com/ke/bella/openapi/db/log/RocksDBLogReader.java` | 修改 | 生成 readerInstanceId，在 secondaryPath 中加入实例标识 |
| `api/server/src/main/java/com/ke/bella/openapi/db/log/RocksDBCleanupTask.java` | 修改 | 清理 Secondary 目录时使用前缀匹配代替固定后缀 |

## 实现思路

### Step 1: 为 RocksDBLogReader 生成实例唯一标识

**文件**: `RocksDBLogReader.java`

1. 新增 `readerInstanceId` 字段，在构造函数中生成，复用 `RocksDBLogRepo` 的 ID 生成策略：
   ```java
   private final String readerInstanceId;
   // 构造函数中：
   String ip = BellaServerContextHolder.getIp();
   if (ip == null) ip = "unknown";
   this.readerInstanceId = "reader-" + ip.replace(".", "-") + "-" + Integer.toHexString(System.identityHashCode(this));
   ```
2. 添加 `BellaServerContextHolder` 的 import

**验证**: 在日志中输出 `readerInstanceId`，确认不同 Pod 产生不同值。

### Step 2: 修改 secondaryPath 生成逻辑

**文件**: `RocksDBLogReader.java:96`

将：
```java
String secondaryPath = dbPath + properties.getReaderSecondarySuffix();
```
改为：
```java
String secondaryPath = dbPath + properties.getReaderSecondarySuffix() + "_" + readerInstanceId;
```

**验证**: 断点或日志确认生成的路径形如 `/pvc-mount/bella-logs/20260609/inst-10-0-1-5-xxx_secondary_reader-10-0-1-6-yyy`。

### Step 3: 更新 RocksDBCleanupTask 清理逻辑

**文件**: `RocksDBCleanupTask.java:55`

当前逻辑使用固定后缀匹配单一 Secondary 目录，需改为前缀匹配以清理所有实例的 Secondary 目录：

将：
```java
File secondaryDir = new File(shardDir.getAbsolutePath() + properties.getReaderSecondarySuffix());
if (secondaryDir.exists()) {
    FileUtils.deleteDirectory(secondaryDir);
}
```
改为遍历 shard 目录的父目录，匹配所有以 `{shardDirName}_secondary` 为前缀的目录：
```java
File parentDir = shardDir.getParentFile();
if (parentDir != null) {
    String prefix = shardDir.getName() + properties.getReaderSecondarySuffix();
    File[] secondaryDirs = parentDir.listFiles((dir, name) -> name.startsWith(prefix));
    if (secondaryDirs != null) {
        for (File secDir : secondaryDirs) {
            FileUtils.deleteDirectory(secDir);
            log.info("Cleaned up secondary directory: {}", secDir.getAbsolutePath());
        }
    }
}
```

**注意**: 清理逻辑需考虑目录层级。当前 shard 目录结构为 `basePath/shardId/instanceId/`，Secondary 目录位于 instance 目录同级（`basePath/shardId/instanceId_secondary_readerXxx/`）。需在遍历 instance 目录时匹配清理。

实际目录结构分析：
- Writer 写入：`basePath/shardId/instanceId/`（如 `/pvc-mount/bella-logs/20260609/inst-10-0-1-5-xxx/`）
- Reader Secondary：`basePath/shardId/instanceId_secondary_readerYyy/`（如 `/pvc-mount/bella-logs/20260609/inst-10-0-1-5-xxx_secondary_reader-10-0-1-6-yyy/`）

因此清理 shard 目录时使用 `FileUtils.deleteDirectory(shardDir)` 会递归删除整个 shard（包含所有 instance 和 secondary 子目录），这意味着当前 cleanup 的 `shardDir` 指的是 date 级目录。需要验证清理任务实际遍历的目录层级。

查看代码确认：`baseDir.listFiles(File::isDirectory)` 遍历的是 basePath 下一级目录（即 shardId 目录如 `20260609`），`FileUtils.deleteDirectory(shardDir)` 会删除整个 shard 目录树。而 `shardDir.getAbsolutePath() + properties.getReaderSecondarySuffix()` 尝试清理 `20260609_secondary` 这个目录——这在当前实现中实际不正确（Secondary 目录不在这一层），但因为不存在所以无害。

**修正后的分析**：Secondary 目录实际路径是 `basePath/shardId/instanceId_secondary[_readerXxx]/`，它位于 shard 目录**内部**。当 cleanup 执行 `FileUtils.deleteDirectory(shardDir)` 时，由于 shard 目录被整体递归删除，内部的 Secondary 目录也会一并清除。因此 cleanup 第 55 行的逻辑实际是冗余的遗留代码。

**结论**: `RocksDBCleanupTask` 第 55 行的固定后缀清理逻辑是一段不会命中的遗留代码（Secondary 目录在 shard 目录内部，已随 shard 一起被递归删除）。修改方案：
- 将固定后缀匹配改为前缀匹配模式，虽然当前场景下仍然不会命中（因为 shard 目录已被删除），但保持防御性编码以应对未来目录结构变化。

### Step 4: 添加初始化日志

在 `RocksDBLogReader` 构造函数末尾添加 info 日志，输出 `readerInstanceId`，便于运维排查。

**验证**: 启动日志中可见 Reader 实例标识。

## 风险与依赖

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 磁盘空间：每个 Reader Pod 对每个 DB 产生独立 Secondary 目录 | 中 | Secondary 目录仅存储 SST 软链接和少量元数据，空间占用极小；且随 shard 一起被 cleanup 任务清理 |
| instanceId 在 Pod 重启后变化（hashCode 部分不同） | 低 | 旧 Secondary 目录不再被访问，下次 cleanup 时随 shard 一起清理；不影响正确性 |
| `BellaServerContextHolder.getIp()` 在 Reader 构造时可能尚未初始化 | 中 | 添加 null 检查，fallback 为 "unknown"；或改用 `@PostConstruct` 延迟初始化 |

**依赖**:
- `BellaServerContextHolder` 需在 `RocksDBLogReader` 构造或初始化时可用
- 不依赖新的外部组件或配置

## 验证方式

1. **单元测试**: 验证多个 `RocksDBLogReader` 实例对同一 dbPath 生成不同的 secondaryPath
2. **集成验证**: 在 2 个 Pod 环境中，两个 Reader 同时读取同一 Primary DB，确认无 LOCK 异常
3. **日志检查**: 启动日志包含 `readerInstanceId`，读取日志包含完整 secondaryPath
4. **清理验证**: cleanup 任务执行后，过期 shard 目录（含 secondary 子目录）被完全清除
