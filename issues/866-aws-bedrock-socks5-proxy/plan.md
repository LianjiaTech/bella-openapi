# Execution Plan: AWS Bedrock 适配器支持 HTTP/HTTPS 代理 & SOCKS5 限制处理

Related to #866

## 目标

1. 为 AWS Bedrock 适配器（`AwsAdaptor`、`AwsMessageAdaptor`）增加 per-channel HTTP/HTTPS 代理支持
2. 修改 `AwsClientManager` 的缓存策略，将代理配置纳入缓存 key
3. 当 channel 配置了 SOCKS5 代理且使用 AWS 协议时，在请求阶段给出明确错误提示
4. 前端 channel 配置页面中，选择 AWS 相关协议时提示 SOCKS5 代理不可用

## 非目标

- 不实现 AWS SDK 对 SOCKS5 代理的底层支持（受限于 SDK 能力）
- 不替换 AWS SDK 的 HTTP 客户端实现
- 不实现 AWS SigV4 手动签名
- 不部署网络层 SOCKS-to-HTTP 转换网关（属基础设施范畴）
- 不修改其他适配器（OpenAI、Vertex 等）的代理逻辑

## 验收标准

1. AWS Bedrock channel 配置 HTTP/HTTPS 代理后，同步和流式请求均通过代理转发
2. 不同 channel 可独立配置不同代理，互不干扰
3. 同一 region + accessKeyId 但不同代理配置的 channel 使用不同客户端实例
4. AWS channel 配置 SOCKS5 代理时，请求阶段返回明确错误（含协议不支持说明）
5. 前端 channel 配置界面：选择 AWS 协议时，代理类型选择处展示 SOCKS5 不可用提示
6. 现有不配置代理的 AWS channel 行为不变（向后兼容）

## 约束

- AWS SDK 的 `ProxyConfiguration` 仅支持 HTTP/HTTPS scheme，不支持 SOCKS5
- `ApacheHttpClient` 和 `NettyNioAsyncHttpClient` 均受此限制
- 现有 `ProxyUtils` 是全局代理工具（基于系统属性），per-channel 代理需要独立参数传递
- 修改缓存 key 会导致已有客户端实例重建，需考虑启动期性能影响
- Java 8 运行时环境限制

## 变更范围

### 后端

| 文件 | 变更类型 | 说明 |
|------|---------|------|
| `api/sdk/.../protocol/completion/AwsProperty.java` | 修改 | 增加代理配置字段 |
| `api/sdk/.../protocol/completion/AwsMessageProperty.java` | 修改 | 增加代理配置字段（若独立） |
| `api/server/.../protocol/completion/AwsClientManager.java` | 修改 | 重构缓存 key，注入 ProxyConfiguration |
| `api/server/.../protocol/completion/AwsAdaptor.java` | 修改 | 传递代理配置给 ClientManager |
| `api/server/.../protocol/completion/AwsMessageAdaptor.java` | 修改 | 传递代理配置给 ClientManager |
| `api/server/.../protocol/message/AwsAdaptor.java` | 修改 | 传递代理配置给 ClientManager |
| `api/server/.../protocol/message/AwsMessageAdaptor.java` | 修改 | 传递代理配置给 ClientManager |

### 前端

| 文件 | 变更类型 | 说明 |
|------|---------|------|
| `web/src/components/` 下 channel 配置相关组件 | 修改 | AWS 协议选中时显示 SOCKS5 不可用提示 |

## 实现思路

### Step 1: 定义代理配置数据结构

**文件**: `api/sdk/.../protocol/completion/AwsProperty.java`

- 在 `AwsProperty` 中增加代理配置字段：
  - `proxyHost: String` — 代理主机地址
  - `proxyPort: Integer` — 代理端口
  - `proxyScheme: String` — 代理协议（仅允许 http/https）
  - `proxyUsername: String` — 代理认证用户名（可选）
  - `proxyPassword: String` — 代理认证密码（可选）
- 在 `description()` 方法中补充字段描述
- 如果 `AwsMessageProperty` 独立于 `AwsProperty`，同步添加

**验证**: 编译通过，单元测试中验证 JSON 序列化/反序列化含代理字段的 property 对象

### Step 2: 重构 AwsClientManager 缓存 key 与客户端构建

**文件**: `api/server/.../protocol/completion/AwsClientManager.java`

1. 修改 `client()` 和 `asyncClient()` 方法签名，增加代理配置参数（host, port, scheme）
2. 修改缓存 key 策略：从 `region -> accessKeyId` 改为 `region -> accessKeyId + proxyIdentifier`
   - `proxyIdentifier` 格式：`host:port:scheme`，无代理时为空字符串
3. 在 `ApacheHttpClient.builder()` 中注入 `ProxyConfiguration`：
   ```java
   ApacheHttpClient.builder()
       .proxyConfiguration(ProxyConfiguration.builder()
           .endpoint(URI.create(scheme + "://" + host + ":" + port))
           .username(username)  // 可选
           .password(password)  // 可选
           .build())
       .buildWithDefaults(...)
   ```
4. 在 `NettyNioAsyncHttpClient.builder()` 中注入 `ProxyConfiguration`：
   ```java
   NettyNioAsyncHttpClient.builder()
       .proxyConfiguration(software.amazon.awssdk.http.nio.netty.ProxyConfiguration.builder()
           .scheme(scheme)
           .host(host)
           .port(port)
           .build())
       .buildWithDefaults(...)
   ```
5. 当代理参数为 null/空时，不注入 ProxyConfiguration（保持向后兼容）

**验证**: 编译通过；构造不同代理参数验证缓存 key 隔离

### Step 3: SOCKS5 协议校验拦截

**文件**: `api/server/.../protocol/completion/AwsAdaptor.java` 及相关适配器

- 在适配器调用 `AwsClientManager.client()` / `asyncClient()` 之前，检查代理 scheme
- 如果 scheme 为 `socks5` 或 `socks`，抛出明确异常：
  ```
  AWS Bedrock adapter does not support SOCKS5 proxy. Please use HTTP/HTTPS proxy instead.
  ```
- 异常应为业务异常（如 `ChannelException` 或项目已有的异常类型），确保返回给调用方友好错误信息

**验证**: 单元测试模拟 SOCKS5 配置，断言抛出预期异常和错误消息

### Step 4: 适配器传递代理配置

**文件**: 所有 AWS 适配器（completion/message 下共 4 个文件）

- 从 channel property 中读取代理配置
- 传递给 `AwsClientManager.client()` / `asyncClient()`
- 确保无代理配置时传入 null，ClientManager 走无代理分支

**验证**: 集成测试（需真实 AWS 环境）或 Mock 测试验证参数传递正确

### Step 5: 前端 SOCKS5 不可用提示

**文件**: `web/src/components/` 下 channel 配置组件

- 定位 channel 编辑/创建表单中代理配置区域
- 当 protocol 字段值匹配 AWS 相关协议（如 `aws`、`bedrock` 等标识）时：
  - 在代理类型选择器旁显示警告提示：「AWS Bedrock 适配器不支持 SOCKS5 代理，请使用 HTTP/HTTPS 代理」
  - 可选：禁用 SOCKS5 选项或标记为不可用
- 不阻止保存（后端有校验兜底），仅做前端提示

**验证**: 启动 dev server，切换协议为 AWS 类型，确认提示出现；切换为其他协议，确认提示消失

## 风险与依赖

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| AWS SDK ProxyConfiguration API 在不同版本间有差异 | 编译失败或运行时异常 | 确认当前使用的 AWS SDK 版本（2.31.65）的 ProxyConfiguration API |
| 缓存 key 变更导致已有客户端实例全部重建 | 启动初期性能波动 | 影响仅限首次请求时创建新实例，后续请求命中缓存 |
| Netty ProxyConfiguration 与 Apache ProxyConfiguration 的 API 不同 | 需分别处理 | Step 2 中分别针对两个客户端编写代理注入逻辑 |
| 前端 channel 配置组件可能尚未实现代理字段 | 前端 Step 5 需依赖代理字段 UI | 若代理 UI 不存在，Step 5 范围缩小为仅在已有代理区域增加提示 |
| per-channel 代理功能可能由其他 issue 主导实现 | 本 issue 的 AwsProperty 代理字段可能与其他实现冲突 | 与 per-channel 代理主 issue 协调字段命名和结构 |

## 验证方式

1. **编译验证**: `mvn clean compile` 全量编译通过
2. **单元测试**:
   - `AwsClientManager` 缓存 key 隔离测试：相同 region+key 不同代理 → 不同实例
   - SOCKS5 校验拦截测试：配置 SOCKS5 → 抛出预期异常
   - `AwsProperty` 序列化测试：含代理字段的 JSON 正确解析
3. **前端验证**: `npm run dev` 启动开发服务器，手动验证 AWS 协议下的提示展示
4. **集成验证**（需环境支持）: 配置 HTTP 代理的 AWS channel，验证请求正确转发
5. **回归验证**: 不配置代理的 AWS channel 行为与修改前一致
