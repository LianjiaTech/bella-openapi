# Execution Plan: Model Name Normalization (Dot/Underscore Compatibility)

Related to #821

## 目标

在 model name 解析阶段增加标准化逻辑，使得 hermes 等外部系统传入的下划线版本号格式（如 `claude_opus_4_7`）能够正确匹配到数据库中以点号分隔的模型名（如 `claude_opus_4.7`）。

## 非目标

- 不修改数据库中已有的 model_name 格式和存储方式
- 不修改 hermes 系统本身的行为
- 不引入通用的 model alias 表或 alias 管理 API
- 不处理其他类型的模型名称不一致问题（大小写、前缀等）

## 验收标准

1. 请求中 model 为 `claude_opus_4_7` 时（库中不存在该名称），能 fallback 路由到数据库中注册的 `claude_opus_4.7` 模型
2. 请求中 model 为 `claude_opus_4.7`（正确格式）时，直接命中，不触发替换逻辑
3. 替换逻辑对不含数字间下划线的模型名（如 `gpt-4o`、`my_custom_model`）无副作用
4. 替换逻辑作用于所有经过 `fetchTerminalModelName` 的模型查找路径
5. 正则精确匹配数字间下划线（`\d_\d`），不影响非数字间的下划线

## 约束

- 仅在 model name 解析层做一次标准化，不改变请求体原始数据
- **先查后替**：先用原始 model name 查找，找不到时再执行替换检查
- 替换规则：用精确正则 `(\d)_(\d)` 匹配数字之间的下划线，将其替换为点号（`$1.$2`）；仅当替换后的名称在模型库中存在时才采用
- 不引入额外的数据库表或 Redis 数据结构
- 保持现有缓存策略（JetCache L1/L2）不变

## 变更范围

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `api/server/src/main/java/com/ke/bella/openapi/service/ModelService.java` | 修改 | 在 `fetchTerminalModelName` 入口增加标准化逻辑 |
| `api/server/src/test/java/.../ModelServiceTest.java` | 新增 | 单元测试覆盖标准化逻辑 |

## 实现思路

### Step 1: 在 ModelService 中添加 model name 替换方法

**文件**: `api/server/src/main/java/com/ke/bella/openapi/service/ModelService.java`

添加 private 方法 `tryNormalizeModelName(String modelName)`，逻辑如下：

1. 使用精确正则 `(\d)_(\d)` 匹配模型名中所有 **数字之间** 的下划线
2. 将匹配到的 `_` 替换为 `.`，生成候选名称（如 `claude_opus_4_7` → `claude_opus_4.7`）
3. 在 model map 缓存中查找：如果候选名称存在，返回候选名称；否则返回原始名称

正则模式：`(\d)_(\d)` — 精确匹配 `数字_数字`，替换为 `$1.$2`。注意使用 `replaceAll` 以处理多段版本号（如 `model_1_2_3` → `model_1.2.3`）。

**验证方式**: 单元测试覆盖以下 case：
- `claude_opus_4_7` → `claude_opus_4.7`（候选名在库中存在时替换）
- `claude-3_5-sonnet` → `claude-3.5-sonnet`
- `gpt-4o` → `gpt-4o`（无数字间下划线，无变化）
- `claude_opus_4.7` → `claude_opus_4.7`（已经是正确格式，正则无匹配，不变）
- `some_model_1_2_3` → `some_model_1.2.3`（多段版本号全部替换）
- `my_model_name` → `my_model_name`（非数字间下划线不受影响）

### Step 2: 在 `fetchTerminalModelName` 中实现「先查后替」逻辑

**文件**: `api/server/src/main/java/com/ke/bella/openapi/service/ModelService.java`

修改 `fetchTerminalModelName` 方法，采用 **fallback 策略**：

```java
public String fetchTerminalModelName(String modelName) {
    // 先用原始名称查找
    List<String> path = getPath(modelName);
    if (!CollectionUtils.isEmpty(path)) {
        return path.get(path.size() - 1);
    }
    // 找不到时，尝试替换数字间下划线为点号
    String normalized = tryNormalizeModelName(modelName);
    if (!normalized.equals(modelName)) {
        path = getPath(normalized);
        if (!CollectionUtils.isEmpty(path)) {
            return path.get(path.size() - 1);
        }
    }
    return modelName;
}
```

注意：由于采用「先查后替」策略，原始名称能直接命中时不会触发标准化逻辑，性能影响为零。仅在找不到时才执行一次正则替换和二次查找。

缓存处理：`getPath` 本身已有缓存，因此 `fetchTerminalModelName` 外层不再需要单独缓存层。如果原设计使用 `@Cached`，需评估是否仍需要（因为 fallback 分支会产生两个不同的 cache key 指向同一结果）。推荐保留 `getPath` 级别的缓存即可。

**验证方式**: 启动应用后用两种格式调用同一模型，验证：
- 原始名称存在时直接返回，不触发替换逻辑
- 原始名称找不到、替换后存在时，返回替换后的结果

### Step 3: 编写单元测试

**文件**: `api/server/src/test/java/com/ke/bella/openapi/service/ModelNameNormalizationTest.java`

测试点：
- `tryNormalizeModelName` 方法本身的纯正则逻辑测试（不依赖数据库）
- 使用 mock model map 验证「先查后替」的 fallback 联动逻辑
- 确认正则精确性：`hello_world` 不会被替换（非数字间下划线）

**验证方式**: `mvn test -Dtest=ModelNameNormalizationTest`（注：按 CLAUDE.md 指引，后端测试需 MySQL 连接，如果是纯逻辑单测可在 CI 跑通）

## 风险与依赖

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 正则误匹配非版本号的数字间下划线 | 路由到错误模型 | 1. 先查后替 — 原始名称能命中时不触发替换；2. 替换后必须验证 model map 中存在性 |
| 找不到模型时增加一次额外查找 | 性能微弱下降 | 仅 fallback 路径触发，正常请求零开销；getPath 有缓存兜底 |
| 多段版本号如 `model_1_2_3` 全部替换为 `model_1.2.3` | 如果存在同名模型歧义 | 精确正则 `(\d)_(\d)` 仅替换数字间下划线，且结果必须在库中存在 |
| hermes 未来修复此问题后，标准化逻辑成为冗余 | 代码维护成本 | 逻辑集中在一个 private 方法，未来可安全移除 |

## 验证方式

1. **单元测试**: 纯逻辑测试覆盖各种 model name 输入
2. **集成测试**: 使用 Docker 环境启动服务，确认以下请求成功：
   - `POST /v1/chat/completions` with `model: "claude_opus_4_7"` 能路由成功
   - `POST /v1/chat/completions` with `model: "claude_opus_4.7"` 行为不变
3. **回归验证**: 现有模型名（如 `gpt-4o`, `claude-3.5-sonnet`）路由行为不变
