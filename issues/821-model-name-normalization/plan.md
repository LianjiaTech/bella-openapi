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

1. 请求中 model 为 `claude_opus_4_7` 时，能正确路由到数据库中注册的 `claude_opus_4.7` 模型
2. 请求中 model 为 `claude_opus_4.7`（正确格式）时，行为不变
3. 标准化逻辑对不含版本号的模型名（如 `gpt-4o`）无副作用
4. 标准化逻辑作用于所有经过 `ChannelRouter.route()` 的模型查找路径
5. 缓存 key 使用标准化后的名称，避免同一模型的不同写法产生多个缓存条目

## 约束

- 仅在 model name 解析层做一次标准化，不改变请求体原始数据
- 标准化规则：尝试将版本号中的下划线替换为点号，仅当标准化后的名称在模型库中存在时才采用
- 不引入额外的数据库表或 Redis 数据结构
- 保持现有缓存策略（JetCache L1/L2）不变，仅调整 cache key 的计算方式

## 变更范围

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `api/server/src/main/java/com/ke/bella/openapi/service/ModelService.java` | 修改 | 在 `fetchTerminalModelName` 入口增加标准化逻辑 |
| `api/server/src/test/java/.../ModelServiceTest.java` | 新增 | 单元测试覆盖标准化逻辑 |

## 实现思路

### Step 1: 在 ModelService 中添加 model name 标准化方法

**文件**: `api/server/src/main/java/com/ke/bella/openapi/service/ModelService.java`

添加 private 方法 `normalizeModelName(String modelName)`，逻辑如下：

1. 使用正则匹配模型名中的版本号部分（末尾的 `_数字_数字` 模式）
2. 将版本号中的下划线替换为点号，生成候选名称
3. 在 model map 缓存中查找：如果候选名称存在，返回候选名称；否则返回原始名称

正则模式建议：`(\d+)_(\d+)$` — 匹配末尾的 `数字_数字` 并替换为 `数字.数字`

**验证方式**: 单元测试覆盖以下 case：
- `claude_opus_4_7` → `claude_opus_4.7`（假设后者在库中存在）
- `claude-3_5-sonnet` → `claude-3.5-sonnet`
- `gpt-4o` → `gpt-4o`（无变化）
- `claude_opus_4.7` → `claude_opus_4.7`（已经是正确格式，不变）
- `some_model_2_0_1` → 只替换末尾的 `_0_1` 部分为 `.0.1`？需讨论，但优先按最小末尾版本号对处理

### Step 2: 在 `fetchTerminalModelName` 方法调用前应用标准化

**文件**: `api/server/src/main/java/com/ke/bella/openapi/service/ModelService.java`

修改 `fetchTerminalModelName` 方法：
```java
public String fetchTerminalModelName(String modelName) {
    String normalized = normalizeModelName(modelName);
    List<String> path = getPath(normalized);
    return CollectionUtils.isEmpty(path) ? normalized : path.get(path.size() - 1);
}
```

注意：由于该方法使用 `@Cached` 注解以 `#modelName` 为 key，标准化应在进入缓存之前完成，避免 `claude_opus_4_7` 和 `claude_opus_4.7` 使用不同缓存条目。方案：将 normalize 逻辑提到缓存方法外层，或者在方法内部先 normalize 再调用一个内部缓存方法。

推荐方式：
- 将 `fetchTerminalModelName` 改为非缓存的 public 入口方法（做 normalize 后委托调用）
- 新增 `fetchTerminalModelNameCached(String normalizedName)` 带 `@Cached` 注解

**验证方式**: 启动应用后用两种格式调用同一模型，检查返回结果一致且缓存命中。

### Step 3: 编写单元测试

**文件**: `api/server/src/test/java/com/ke/bella/openapi/service/ModelNameNormalizationTest.java`

测试点：
- 标准化方法本身的纯逻辑测试（不依赖数据库）
- 使用 mock model map 验证标准化 + 路径解析联动

**验证方式**: `mvn test -Dtest=ModelNameNormalizationTest`（注：按 CLAUDE.md 指引，后端测试需 MySQL 连接，如果是纯逻辑单测可在 CI 跑通）

## 风险与依赖

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 正则匹配过于宽泛，误标准化非版本号的下划线 | 路由到错误模型 | 标准化结果必须在 model map 中验证存在性，不存在则不替换 |
| 缓存 key 变更导致短暂缓存失效 | 冷启动时微量性能下降 | 影响极小，JetCache TTL 仅 2/10 分钟 |
| 多段版本号如 `model_1_2_3` 的歧义 | 可能需要尝试多种替换组合 | 第一版仅处理末尾单个 `_数字` 对，覆盖已知 hermes 场景；后续按需扩展 |
| hermes 未来修复此问题后，标准化逻辑成为冗余 | 代码维护成本 | 逻辑集中在一个 private 方法，未来可安全移除 |

## 验证方式

1. **单元测试**: 纯逻辑测试覆盖各种 model name 输入
2. **集成测试**: 使用 Docker 环境启动服务，确认以下请求成功：
   - `POST /v1/chat/completions` with `model: "claude_opus_4_7"` 能路由成功
   - `POST /v1/chat/completions` with `model: "claude_opus_4.7"` 行为不变
3. **回归验证**: 现有模型名（如 `gpt-4o`, `claude-3.5-sonnet`）路由行为不变
