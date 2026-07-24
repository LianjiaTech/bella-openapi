# Execution Plan for #856: 文本生成多渠道路由增加自动缓存亲和，提升 prompt cache 复用率

## 目标

为文本生成类接口（`/v1/chat/completions`、`/v1/messages`、`/v1/responses`）增加自动缓存亲和路由能力，使具有稳定长前缀的请求尽量路由到同一渠道，提升下游供应商 prompt cache 复用率，降低成本和延迟。

## 非目标

- 不重构现有路由框架
- 不做 Console 配置页面
- 不修改下游供应商 prompt cache 协议
- 不保证供应商侧一定命中缓存（只提升 Bella 侧路由亲和性）
- 不影响 `/v1/route/list` 行为
- 不改变短请求/普通请求的随机负载均衡行为
- 不要求用户理解或手动维护缓存 key

## 验收标准

1. `/v1/chat/completions` 不带稳定长前缀的普通请求，仍然使用现有随机路由
2. `/v1/messages` 不带稳定长前缀的普通请求，仍然使用现有随机路由
3. `/v1/responses` 不带稳定长前缀或结构无法识别的请求，仍然使用现有随机路由
4. 三类 endpoint 带相同稳定长前缀的请求，多次调用稳定落到同一最高优先级可用渠道
5. 最后一条 user 消息/输入变化时，如果前缀不变，路由渠道保持稳定
6. 前缀内容变化时，可能路由到不同渠道
7. 某渠道不可用后，现有健康过滤仍生效，并在剩余可用渠道中稳定选择
8. 多模型逗号请求中，affinityKey 包含具体 model，不同 model 互不污染
9. 编译通过，并补充单测覆盖：
   - affinityKey 为空时走随机选择
   - affinityKey 非空时稳定选择
   - chat/messages/responses 的 stable prefix 提取规则
   - 前缀过短时不启用 affinity

## 约束

- 保持 `ChannelRouter` 现有公开方法兼容，新增重载而非修改签名
- 使用 Spring `@Value` 注入配置项，不引入新配置中心依赖
- 稳定前缀少于 1024 字符时不启用缓存亲和（避免影响短请求负载均衡）
- 最多取前 32768 字符参与 hash（防止大请求 hash 过慢）
- Java 8 兼容
- `/v1/responses` 结构更灵活，第一版应保守处理：能识别稳定文本前缀就启用 affinity，识别不了就返回 null

## 变更范围

| 文件 | 变更类型 | 说明 |
|------|---------|------|
| `api/server/src/main/java/com/ke/bella/openapi/protocol/RouteAffinityKeyBuilder.java` | 新增 | affinityKey 构建工具类，按 endpoint 类型提供 forChat/forMessages/forResponses 方法 |
| `api/server/src/main/java/com/ke/bella/openapi/protocol/ChannelRouter.java` | 修改 | 新增带 affinityKey 参数的 route 重载；新增 select 方法 |
| `api/server/src/main/java/com/ke/bella/openapi/endpoints/ChatController.java` | 修改 | 在路由前调用 affinityKey 生成，传入 route 重载 |
| `api/server/src/main/java/com/ke/bella/openapi/endpoints/MessageController.java` | 修改 | 在路由前调用 affinityKey 生成，传入 route 重载 |
| `api/server/src/main/java/com/ke/bella/openapi/endpoints/ResponsesController.java` | 修改 | 在路由前调用 affinityKey 生成，传入 route 重载 |
| `api/server/src/main/resources/application.yml` | 修改 | 新增 route-affinity 配置项 |
| `api/server/src/test/java/com/ke/bella/openapi/protocol/RouteAffinityKeyBuilderTest.java` | 新增 | 单测覆盖 affinityKey 生成逻辑（三类 endpoint） |
| `api/server/src/test/java/com/ke/bella/openapi/protocol/ChannelRouterAffinityTest.java` | 新增 | 单测覆盖亲和选择逻辑 |

## 实现思路

### Step 1: 新增配置项

**文件**: `api/server/src/main/resources/application.yml`

**新增配置**:
```yaml
bella:
  openapi:
    route-affinity:
      enabled: true
      min-prefix-chars: 1024
      max-prefix-chars: 32768
```

**验证**: 配置关闭时走原逻辑；配置修改阈值后行为相应变化。

### Step 2: 新增 RouteAffinityKeyBuilder 类

**文件**: `api/server/src/main/java/com/ke/bella/openapi/protocol/RouteAffinityKeyBuilder.java`

**职责**: 从各类请求中提取稳定前缀并生成 affinityKey。作为 Spring Bean 注入到各 Controller。

**实现细节**:
1. 注入配置项 `bella.openapi.route-affinity.enabled`、`min-prefix-chars`、`max-prefix-chars`
2. 三个公开方法：
   ```java
   public String forChat(String endpoint, String model, CompletionRequest request)
   public String forMessages(String endpoint, String model, MessageRequest request)
   public String forResponses(String endpoint, String model, ResponsesApiRequest request)
   ```
3. 统一生成格式：`endpoint + ":" + model + ":" + stablePromptHash`

#### `/v1/chat/completions` stable prefix 提取

来源：`CompletionRequest.messages`、`tools`、`functions`

参与 hash 的内容（按顺序拼接为 StringBuilder）：
- `tools` / `functions` 定义 JSON 序列化（使用 JacksonUtils.serialize）
- messages 中 role 为 `system` 的消息 content
- messages 中 role 为 `developer` 的消息 content
- messages 中除最后一条 role 为 `user` 的消息外的上下文消息 content

排除：
- 最后一条 `user` 消息

#### `/v1/messages` stable prefix 提取

来源：`MessageRequest.system`、`MessageRequest.messages`、`tools`

参与 hash 的内容（按顺序拼接）：
- `system` 字段（若为 String 直接拼接；若为 List<RequestTextBlock> 取各项 text 拼接）
- `tools` 定义 JSON 序列化
- messages 中除最后一条 role 为 `user` 的 InputMessage 外的上下文消息 content

排除：
- 最后一条 `user` 消息

#### `/v1/responses` stable prefix 提取

来源：`ResponsesApiRequest.instructions`、`tools`、`input`

参与 hash 的内容（按顺序拼接）：
- `instructions` 字段
- `tools` 定义 JSON 序列化
- `input` 中可识别的稳定文本前缀：
  - 若 input 为 String，取其内容
  - 若 input 为 List（反序列化为 List<Map> 或 List<InputItem>），按序提取 type=message 且 role 非最后一个 user 的 content 文本

排除：
- 最后一个用户输入项
- 无法识别结构的复杂 input item 不参与 hash

保守策略：若无法从 input 中提取结构化前缀，仅使用 instructions + tools 参与 hash；若这些也不存在或拼接后长度不够，返回 null。

#### 通用 hash 逻辑

```java
private String buildKey(String endpoint, String model, String stablePrefix) {
    if (!enabled) return null;
    if (stablePrefix == null || stablePrefix.length() < minPrefixChars) return null;
    String toHash = stablePrefix.length() > maxPrefixChars
        ? stablePrefix.substring(0, maxPrefixChars) : stablePrefix;
    byte[] digest = MessageDigest.getInstance("SHA-256").digest(toHash.getBytes(StandardCharsets.UTF_8));
    String hash = Hex.encodeHexString(digest).substring(0, 16);
    return endpoint + ":" + model + ":" + hash;
}
```

**验证**: 单测覆盖以下场景（每类 endpoint 各一组）：
- 长 system/tools + 上下文 → 生成有效 key
- 仅短 user 消息 → 返回 null（未达阈值）
- 最后一条 user 消息变化，前缀不变 → key 不变
- tools 定义变化 → key 变化
- 功能禁用 → 返回 null

### Step 3: ChannelRouter 新增带 affinityKey 的 route 重载

**文件**: `api/server/src/main/java/com/ke/bella/openapi/protocol/ChannelRouter.java`

**实现细节**:
1. 新增方法签名（五参数 + affinityKey）：
   ```java
   public ChannelDB route(String endpoint, String model, ApikeyInfo apikeyInfo, boolean isMock, boolean isDirectMode, String affinityKey)
   ```
2. 新增方法签名（四参数 + affinityKey，供 MessageController 使用）：
   ```java
   public ChannelDB route(String endpoint, String model, ApikeyInfo apikeyInfo, boolean isMock, String affinityKey)
   ```
3. 现有 `route(endpoint, model, apikeyInfo, isMock, isDirectMode)` 内部调用新方法，affinityKey 传 null，保持向后兼容
4. 现有 `route(endpoint, model, apikeyInfo, isMock)` 同理调用新方法，affinityKey 传 null
5. 新方法主体逻辑复用现有 filter + pickMaxPriority 流程，最终选择环节替换为 `select(channels, affinityKey)`
6. 新增 `select(List<ChannelDB> channels, String affinityKey)` 私有方法：
   ```java
   private ChannelDB select(List<ChannelDB> channels, String affinityKey) {
       if (StringUtils.isBlank(affinityKey)) {
           return random(channels);
       }
       List<ChannelDB> ordered = channels.stream()
           .sorted(Comparator.comparing(ChannelDB::getChannelCode))
           .collect(Collectors.toList());
       int index = Math.floorMod(affinityKey.hashCode(), ordered.size());
       return ordered.get(index);
   }
   ```

**验证**: 单测覆盖：
- affinityKey 为 null → 随机选择（多次调用结果不完全相同）
- affinityKey 非空 → 多次调用返回同一渠道
- affinityKey 非空 + 渠道列表变化 → 可能选择不同渠道（预期行为）
- 单渠道 → 无论 affinityKey 值，始终返回该渠道
- 按 channelCode 排序后稳定性验证

### Step 4: ChatController 集成

**文件**: `api/server/src/main/java/com/ke/bella/openapi/endpoints/ChatController.java`

**实现细节**:
1. 注入 `RouteAffinityKeyBuilder`
2. 在 `processCompletionRequest` 方法中，调用 `initializeChannel` 前生成 affinityKey：
   ```java
   String affinityKey = affinityKeyBuilder.forChat(endpoint, model, request);
   ```
3. 修改 `initializeChannel` 方法签名，增加 `String affinityKey` 参数：
   ```java
   private ChannelContext initializeChannel(String endpoint, String model, boolean isDirectMode, String affinityKey)
   ```
   内部调用变为：
   ```java
   ChannelDB channel = router.route(endpoint, model, EndpointContext.getApikey(), false, isDirectMode, affinityKey);
   ```
4. 多模型逗号请求场景：循环内每个 model 单独基于其 processedRequest 生成 affinityKey
5. Direct mode 调用入口也传入 affinityKey（由 RouteAffinityKeyBuilder 内部根据 enabled 决定是否生成）

### Step 5: MessageController 集成

**文件**: `api/server/src/main/java/com/ke/bella/openapi/endpoints/MessageController.java`

**实现细节**:
1. 注入 `RouteAffinityKeyBuilder`
2. 在 `router.route(endpoint, model, ...)` 调用前生成 affinityKey：
   ```java
   String affinityKey = affinityKeyBuilder.forMessages(endpoint, model, request);
   ChannelDB channel = router.route(endpoint, model, EndpointContext.getApikey(), isMock, affinityKey);
   ```
3. MessageController 当前调用 `router.route(endpoint, model, apikey, isMock)` 四参数版本，使用新增的 `route(endpoint, model, apikey, isMock, affinityKey)` 五参数重载

### Step 6: ResponsesController 集成

**文件**: `api/server/src/main/java/com/ke/bella/openapi/endpoints/ResponsesController.java`

**实现细节**:
1. 注入 `RouteAffinityKeyBuilder`
2. 修改 `routeToChannel` 方法，在走正常路由（非指定 channelCode）时传入 affinityKey：
   ```java
   private ChannelDB routeToChannel(String endpoint, String model, String channelCode, String affinityKey) {
       if (StringUtils.isNotBlank(channelCode)) {
           // 指定 channelCode 时跳过亲和路由
           ChannelDB channel = router.route(channelCode);
           if (channel == null) {
               throw new BizParamCheckException("channel_code not found: " + channelCode);
           }
           return channel;
       }
       return router.route(endpoint, model, EndpointContext.getApikey(), false, affinityKey);
   }
   ```
3. 在 `createResponse` 方法中调用 `routeToChannel` 前计算 affinityKey：
   ```java
   String affinityKey = affinityKeyBuilder.forResponses(endpoint, model, request);
   ChannelDB channel = routeToChannel(endpoint, model, channelCode, affinityKey);
   ```

### Step 7: 编写单元测试

**文件**:
- `api/server/src/test/java/com/ke/bella/openapi/protocol/RouteAffinityKeyBuilderTest.java`
- `api/server/src/test/java/com/ke/bella/openapi/protocol/ChannelRouterAffinityTest.java`

**RouteAffinityKeyBuilderTest 覆盖**:

Chat Completions:
- 长 system + tools + 上下文消息 → 生成有效 key
- 仅短 user 消息 → 返回 null
- 前缀相同 + 最后 user 消息不同 → key 相同
- tools 变化 → key 不同
- 功能关闭 → 返回 null
- 超过 max-prefix-chars 截断后 key 不变

Messages:
- system + tools + 长上下文 → 生成有效 key
- 仅短 user 消息 → 返回 null
- 前缀相同 + 最后 user 消息不同 → key 相同
- system 字段为 String 或 List<RequestTextBlock> 均可正确提取

Responses:
- instructions + tools + input 前缀 → 生成有效 key
- 仅短 input → 返回 null
- input 结构无法识别 → 返回 null（保守处理）
- instructions + tools 长度足够时仍可生成 key

**ChannelRouterAffinityTest 覆盖**:
- select 方法：affinityKey 为空走 random
- select 方法：affinityKey 非空走稳定 hash
- select 方法：渠道排序对结果影响（按 channelCode 排序后稳定）
- 端到端：route 方法传 affinityKey 后稳定选择

## 风险与依赖

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| 渠道列表变化时 hash % N 导致部分 key 迁移 | 缓存命中率暂时下降 | 第一版接受；后续可用一致性哈希优化 |
| 自动前缀提取规则与供应商真实 cache 规则不一致 | 亲和但未必命中 | 只提升概率，不承诺保证 |
| 稳定前缀提取过宽导致负载不均 | 某些渠道过载 | min-prefix-chars 门槛 + 配置可调 + 可随时关闭 |
| SHA-256 计算对大请求的性能开销 | 请求延迟微增 | max-prefix-chars 限制计算量；SHA-256 对 32KB 文本耗时 < 1ms |
| `/v1/responses` 输入结构复杂，误判可能性 | 不必要的亲和或遗漏 | 第一版保守识别，无法确定则返回 null 走随机 |

**依赖**:
- 无外部新依赖，Java 标准库 `java.security.MessageDigest` 即可完成 SHA-256
- 无数据库 schema 变更
- 无前端变更

## 验证方式

1. **编译验证**: `mvn clean compile` 通过
2. **单元测试**: `mvn test -pl server -Dtest=RouteAffinityKeyBuilderTest,ChannelRouterAffinityTest` 通过
3. **回归验证**: 配置 `route-affinity.enabled=false` 时，三类 endpoint 行为与当前版本完全一致
4. **集成验证**（部署后）:
   - 同一 akCode + 同一长前缀的 chat/messages/responses 请求多次调用，观察日志确认路由到同一渠道
   - 短请求仍随机分布
   - 渠道下线后自动迁移到其他可用渠道
