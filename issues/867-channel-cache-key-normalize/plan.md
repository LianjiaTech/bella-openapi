# Execution Plan for #867: 修复渠道缓存 key 大小写不一致

## 目标

统一 `ChannelService` 中渠道缓存 key 的生成逻辑，确保 `@Cached` 注解缓存读取和手动 `cache.put` 写入使用同一套小写归一化规则，消除因 `entityType`/`entityCode` 大小写不一致导致的缓存命中失败问题。

## 非目标

- 不修改数据库查询逻辑或业务返回结构
- 不修改其他缓存（如 `channel:single:` 的 channelCode 缓存）
- 不处理本地配置文件（`application-local.yml`、`config-cache/` 等）
- 不对 entityType/entityCode 的上游调用方做归一化改造

## 验收标准

1. `listActives(entityType, entityCode)` 的 `@Cached` key 通过统一方法生成，结果为小写
2. `updateCache(entityType, entityCode)` 中 `cache.put` 的 key 通过同一方法生成
3. 新增 `cacheKey(String entityType, String entityCode)` 方法，返回 `(entityType + ":" + entityCode).toLowerCase(Locale.ROOT)`
4. 无论调用方传入何种大小写组合，缓存 key 始终一致
5. 现有单元测试无回归

## 约束

- 仅修改 `api/server/src/main/java/com/ke/bella/openapi/service/ChannelService.java` 一个文件
- 使用 `Locale.ROOT` 进行小写转换，避免土耳其语等 locale 导致的 `i/I` 问题
- `@Cached` 注解的 SpEL 表达式需使用 `target.cacheKey(...)` 调用实例方法
- 不改变缓存过期策略、缓存名称前缀、缓存层级配置

## 变更范围

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `api/server/src/main/java/com/ke/bella/openapi/service/ChannelService.java` | 修改 | 新增 `cacheKey` 方法、修改 `@Cached` key 表达式、修改 `cache.put` key 生成 |

## 实现思路

### Step 1: 新增 `cacheKey` 方法

在 `ChannelService` 类中新增公开方法：

```java
public String cacheKey(String entityType, String entityCode) {
    return (entityType + ":" + entityCode).toLowerCase(Locale.ROOT);
}
```

同时在文件头部添加 `import java.util.Locale;`（如尚未存在）。

**验证**：编译通过 `mvn clean compile -pl server`。

### Step 2: 修改 `@Cached` 注解的 key 表达式

将 `listActives` 方法上的注解从：

```java
@Cached(name = channelCacheKey, key = "#entityType + ':' + #entityCode")
```

修改为：

```java
@Cached(name = channelCacheKey, key = "target.cacheKey(#entityType, #entityCode)")
```

SpEL 中 `target` 引用当前 bean 实例，确保调用归一化方法。

**验证**：编译通过，确认 SpEL 表达式语法正确。

### Step 3: 修改 `updateCache` 中的手动缓存写入 key

将 `updateCache(String entityType, String entityCode)` 方法中的：

```java
cache.put(entityType + ":" + entityCode, channels);
```

修改为：

```java
cache.put(cacheKey(entityType, entityCode), channels);
```

**验证**：编译通过 `mvn clean compile -pl server`。

### Step 4: 整体验证

- 运行 `mvn clean compile -pl server` 确认编译无报错
- 检查是否有其他位置使用 `channelCacheKey` 进行缓存操作，确保一致性
- 如有单元测试覆盖 `ChannelService`，确认测试通过

## 风险与依赖

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 发布后旧大写 key 缓存仍存在，新小写 key 未命中 | 首次请求缓存 miss，走 DB 查询后重新写入 | 缓存有过期时间，旧 key 自然淘汰；若需快速生效可配合缓存清理 |
| `target.cacheKey(...)` SpEL 在 JetCache 中的兼容性 | 若 JetCache SpEL 解析不支持 `target` 关键字，缓存 key 生成异常 | 部署前需验证 JetCache 版本对 Spring SpEL `target` 的支持；备选方案为使用静态工具方法 |
| 其他服务通过非 `ChannelService` 路径直接操作同名缓存 | 缓存 key 格式不一致 | 全局搜索 `channelCacheKey` 常量引用，确认无外部写入 |

## 验证方式

1. **编译验证**：`mvn clean compile -pl server` 无错误
2. **缓存 key 一致性检查**：在 `listActives` 和 `updateCache` 设置断点或添加临时日志，传入混合大小写参数，确认 key 输出一致
3. **集成测试**（若有 Redis 环境）：调用 `add(...)` 新增渠道后立即调用 `listActives(...)`，验证缓存命中
4. **回归测试**：执行 `mvn test -pl server` 确认无测试回归
