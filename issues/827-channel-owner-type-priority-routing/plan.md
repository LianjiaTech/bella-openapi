# Execution Plan for #827: Channel Owner-Type Priority Routing

## 目标

在 Channel 路由选择阶段，新增基于 API Key `owner_type` + `owner_code` 的多级优先级匹配机制：
- Channel 新增 `match_owner_type` 和 `match_owner_code` 字段，用于声明该 Channel 适配哪种 owner 的请求
- 路由时按三级优先级匹配：
  1. **精确匹配**：`match_owner_type + match_owner_code` 同时匹配 AK 的 `owner_type + owner_code`（最高优先）
  2. **类型匹配**：`match_owner_type` 匹配 AK 的 `owner_type`，且 `match_owner_code` 为空（次优先）
  3. **默认兜底**：`match_owner_type` 为空的 Channel（最低优先）
- 若高优先级匹配结果非空，则仅在该子集中选择；否则逐级回退

## 非目标

- 不修改 API Key 的 `owner_type` 管理逻辑
- 不引入新的 AK 类型枚举值
- 不调整现有 private/public visibility 逻辑（该逻辑基于 Channel 的 `owner_type` + `owner_code` 字段控制所有权可见性，与本需求新增的 `match_owner_type` + `match_owner_code` 路由偏好匹配互不干扰）
- 不修改前端 UI（Channel 管理页面的字段展示可后续迭代）
- 不修改其他路由维度（data_destination、priority、protocol 等）

## 验收标准

1. Channel 表新增 `match_owner_type` 字段（varchar(16)）和 `match_owner_code` 字段（varchar(64)），默认值均为空字符串
2. Channel CRUD API 支持设置和查询 `match_owner_type` 和 `match_owner_code`
3. `ChannelRouter.route()` 路由逻辑变更为三级优先级匹配：
   - **Level 1 精确匹配**：筛选 `match_owner_type == apikeyInfo.ownerType && match_owner_code == apikeyInfo.ownerCode` 的 Channel（两字段均非空时生效）
   - **Level 2 类型匹配**：若 Level 1 为空，筛选 `match_owner_type == apikeyInfo.ownerType && match_owner_code 为空` 的 Channel
   - **Level 3 默认兜底**：若 Level 2 为空，使用 `match_owner_type` 为空的 Channel
   - 若所有级别均为空，使用全部 Channel（保持向后兼容）
4. `listAvailableChannels()` 同步应用相同的三级优先级逻辑
5. 已有的 Channel（`match_owner_type` 和 `match_owner_code` 均为空）行为不变，作为兜底 Channel
6. 设置了 `match_owner_code` 的 Channel 必须同时设置 `match_owner_type`（`match_owner_code` 非空时 `match_owner_type` 不允许为空）

## 约束

- `match_owner_type` 的值范围应与 `EntityConstants.OWNER_TYPES`（system/org/person/project）保持一致，加上空字符串表示 default
- `match_owner_code` 非空时，`match_owner_type` 必须非空（不允许只设 code 不设 type）
- 数据库变更必须提供可回滚的 migration SQL
- 不得破坏现有 private Channel 的 owner 精确匹配逻辑（visibility=private 的 Channel 仍需 owner_type + owner_code 完全匹配才可见）
- 缓存 key 不变（`listActives` 缓存仍按 entityType:entityCode 维度），owner 匹配在内存中完成
- jOOQ 生成代码需同步更新

## 变更范围

| 模块 | 文件 | 变更类型 |
|------|------|----------|
| sql | `api/server/sql/25-20260526_alter_channel_match_owner.sql` | 新增（含 match_owner_type 和 match_owner_code 两个字段） |
| codegen | `api/server/src/codegen/java/.../tables/pojos/ChannelDB.java` | 重新生成 |
| codegen | `api/server/src/codegen/java/.../tables/records/ChannelRecord.java` | 重新生成 |
| codegen | `api/server/src/codegen/java/.../tables/Channel.java` | 重新生成 |
| server | `api/server/src/main/java/.../protocol/ChannelRouter.java` | 修改路由逻辑（三级优先级匹配） |
| server | `api/server/src/main/java/.../service/ChannelService.java` | 可能需微调（若需新增查询） |
| server | `api/server/src/main/java/.../endpoints/MetadataController.java` | Channel 创建/更新 DTO 支持新字段、校验逻辑 |
| server | `api/server/src/main/java/.../metadata/MetadataValidator.java` | 新增 match_owner_code 非空时 match_owner_type 必须非空的校验 |
| sdk | `api/sdk/src/main/java/.../common/EntityConstants.java` | 新增常量 |

## 实现思路

### Step 1: 数据库 Migration

**任务**：创建 `api/server/sql/25-20260526_alter_channel_match_owner.sql`

```sql
ALTER TABLE `channel` ADD COLUMN `match_owner_type` varchar(16) NOT NULL DEFAULT '' 
  COMMENT '路由优先级匹配-owner类型，空值表示默认兜底' AFTER `owner_name`;
ALTER TABLE `channel` ADD COLUMN `match_owner_code` varchar(64) NOT NULL DEFAULT '' 
  COMMENT '路由优先级匹配-owner编码，配合match_owner_type实现精确匹配' AFTER `match_owner_type`;
```

> 注：不再创建索引，因为该字段仅用于内存中过滤，不在 SQL WHERE 条件中使用（Channel 表数据量小，内存匹配性能充足）。

**验证**：SQL 可在本地 MySQL 执行无报错，字段类型和默认值正确。

### Step 2: jOOQ 代码重新生成

**任务**：执行 `mvn jooq:generate -pl server`，更新 codegen 目录下的 `ChannelDB`、`ChannelRecord`、`Channel` 表定义类。

**验证**：`ChannelDB.java` 中新增 `matchOwnerType` 和 `matchOwnerCode` 字段及 getter/setter。

### Step 3: 新增 EntityConstants 常量

**任务**：在 `EntityConstants.java` 中新增：
```java
public static final String DEFAULT_OWNER_TYPE_MATCH = "";
```

**验证**：编译通过。

### Step 4: 修改 ChannelRouter 核心路由逻辑

**任务**：在 `ChannelRouter.java` 的 `filter()` 方法返回结果**之后**、`pickMaxPriority()` **之前**，插入三级 owner 优先匹配逻辑：

```java
private List<ChannelDB> applyOwnerPreference(List<ChannelDB> channels, String akOwnerType, String akOwnerCode) {
    if (StringUtils.isBlank(akOwnerType)) {
        return channels;
    }

    // Level 1: 精确匹配 owner_type + owner_code
    if (StringUtils.isNotBlank(akOwnerCode)) {
        List<ChannelDB> exactMatched = channels.stream()
            .filter(ch -> akOwnerType.equals(ch.getMatchOwnerType())
                    && akOwnerCode.equals(ch.getMatchOwnerCode()))
            .collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(exactMatched)) {
            return exactMatched;
        }
    }

    // Level 2: 类型匹配 owner_type（match_owner_code 为空的）
    List<ChannelDB> typeMatched = channels.stream()
        .filter(ch -> akOwnerType.equals(ch.getMatchOwnerType())
                && StringUtils.isBlank(ch.getMatchOwnerCode()))
        .collect(Collectors.toList());
    if (CollectionUtils.isNotEmpty(typeMatched)) {
        return typeMatched;
    }

    // Level 3: 默认兜底（match_owner_type 为空）
    List<ChannelDB> defaults = channels.stream()
        .filter(ch -> StringUtils.isBlank(ch.getMatchOwnerType()))
        .collect(Collectors.toList());
    return CollectionUtils.isNotEmpty(defaults) ? defaults : channels;
}
```

在 `route()` 方法中，调用顺序为：
1. `filter()` — 现有安全/可用性筛选
2. `applyOwnerPreference()` — 新增三级 owner 优先级匹配
3. `pickMaxPriority()` — 现有 priority 排序
4. `random()` — 随机选择

同样在 `listAvailableChannels()` 中，过滤后排序前插入 `applyOwnerPreference()`。

**验证**：
- 单元测试：AK owner_type=org, owner_code=ORG001，Channel 列表含精确匹配(org+ORG001)、类型匹配(org)和 default → 选中精确匹配
- 单元测试：AK owner_type=org, owner_code=ORG002，Channel 列表含精确匹配(org+ORG001)、类型匹配(org)和 default → 精确不命中，回退到类型匹配
- 单元测试：AK owner_type=person，Channel 列表只有 org 类型匹配和 default → 回退到 default
- 单元测试：所有 Channel 的 `matchOwnerType` 为空时行为不变（向后兼容）
- 单元测试：AK owner_type 为空 → 跳过匹配，全部参与选择

### Step 5: Channel CRUD 支持新字段 + 校验

**任务**：
1. 确认 Channel 的创建/更新接口（MetadataController 或 ChannelService 中的 DTO）能够接收和持久化 `match_owner_type` 和 `match_owner_code` 字段。jOOQ Record 自动包含新字段，需确认创建/更新逻辑透传。
2. 在 MetadataValidator 中新增校验：`match_owner_code` 非空时，`match_owner_type` 必须非空（不允许只设 code 不设 type）。

**验证**：
- 通过 API 创建 Channel 时可指定 `matchOwnerType` 和 `matchOwnerCode`，查询时返回这两个字段
- 尝试创建 `matchOwnerCode` 非空但 `matchOwnerType` 为空的 Channel 时返回校验错误

### Step 6: 编写单元测试

**任务**：在 `api/server/src/test/` 下新增或扩展 `ChannelRouterTest`，覆盖三级优先级匹配的各种场景：

| # | AK (ownerType, ownerCode) | Channel 配置 | 预期选中 |
|---|---------------------------|-------------|---------|
| 1 | org, ORG001 | [org+ORG001, org（无code）, default] | org+ORG001（Level 1 精确匹配） |
| 2 | org, ORG002 | [org+ORG001, org（无code）, default] | org（Level 2 类型匹配） |
| 3 | person, P001 | [org+ORG001, org（无code）, default] | default（Level 3 兜底） |
| 4 | org, ORG001 | [default, default] | default（向后兼容） |
| 5 | 空, 空 | [org+ORG001, org, default] | 全部参与（跳过匹配） |
| 6 | org, ORG001 | [org+ORG001(high), org+ORG001(low)] | org+ORG001(high)（精确匹配后再走 priority 选择） |

**验证**：`mvn test -pl server -Dtest=ChannelRouterTest` 全部通过。

## 风险与依赖

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 现有 Channel 的 `match_owner_type`/`match_owner_code` 全为空，新逻辑回退到全量 → 行为不变 | 低 | 新逻辑仅在 matched 列表非空时才收窄范围 |
| 缓存未区分 owner → 同一 model 的 Channel 列表被多个 owner 的 AK 共享 | 无影响 | owner 筛选在内存中完成，不影响缓存 key |
| `match_owner_type` 取值不在 OWNER_TYPES 范围内 | 中 | Channel 创建/更新时校验取值范围 |
| `match_owner_code` 非空但 `match_owner_type` 为空 → 语义不合法 | 中 | MetadataValidator 中新增校验：code 非空时 type 必须非空 |
| private Channel 已有 ownerType/ownerCode 精确匹配（用于可见性控制）→ 与新字段语义可能混淆 | 中 | 文档明确：`ownerType`+`ownerCode` 是所有权归属（private 可见性控制），`matchOwnerType`+`matchOwnerCode` 是路由优先级偏好（public Channel 也可设置，用于按 owner 分流） |
| 三级回退增加代码复杂度 | 低 | 逻辑集中在单一方法 `applyOwnerPreference()` 中，便于测试和维护 |

**依赖**：
- 本地 MySQL 数据库用于执行 migration 和 jOOQ 代码生成
- 单元测试需要连接 MySQL（按 CLAUDE.md 描述）

## 验证方式

1. **SQL 验证**：migration 脚本在 MySQL 执行通过，`SHOW COLUMNS FROM channel` 确认 `match_owner_type` 和 `match_owner_code` 新字段
2. **编译验证**：`mvn clean compile` 通过
3. **单元测试**：`mvn test -pl server -Dtest=ChannelRouterTest` 覆盖三级优先级匹配的核心逻辑
4. **集成验证**：启动服务后，创建三个 Channel：
   - 一个 matchOwnerType=org, matchOwnerCode=ORG001（精确匹配）
   - 一个 matchOwnerType=org, matchOwnerCode=空（类型匹配）
   - 一个 matchOwnerType=空（默认兜底）
   用不同 owner_type+owner_code 组合的 AK 请求，验证三级回退行为
5. **回归验证**：现有无 matchOwnerType/matchOwnerCode 的 Channel 行为不变
6. **校验验证**：尝试创建 matchOwnerCode 非空但 matchOwnerType 为空的 Channel，确认返回校验错误
