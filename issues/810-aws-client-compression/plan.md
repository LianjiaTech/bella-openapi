# Execution Plan for #810

## 目标

为 AWS Bedrock 客户端（`AwsClientManager`）支持可配置的 HTTP 传输压缩策略（gzip），以降低专线网络带宽峰值要求。

## 非目标

- 不修改非 AWS 相关的 Protocol Adapter（如 OpenAI、阿里云等）
- 不改变现有的请求路由逻辑或通道选择策略
- 不引入新的压缩算法（仅使用 AWS SDK 内置的 gzip 支持）
- 不改变 API 对外行为（压缩对调用方透明）

## 验收标准

1. `AwsProperty` / `AwsMessageProperty` 新增 `compressionEnabled` 配置字段（默认 `false`，保持向后兼容）
2. 当 `compressionEnabled = true` 时，AWS SDK 客户端启用请求体 gzip 压缩（`requestCompression`）
3. 同一 region + accessKeyId + compressionEnabled 组合对应独立的客户端缓存实例
4. 已有通道无需修改即可正常工作（默认不压缩）
5. 新建通道可通过 channel property 中设置 `compressionEnabled: true` 开启压缩

## 约束

- Java 8 运行时
- AWS SDK 版本 2.31.65（已支持 `requestCompression` 配置）
- 不能引入新的第三方依赖
- 压缩仅在 HTTP 传输层，不影响应用层协议（Converse API / InvokeModel API）
- 客户端缓存（`httpCache` / `asyncCache`）需考虑压缩开关作为缓存键的一部分

## 变更范围

| 文件 | 变更类型 | 说明 |
|------|---------|------|
| `api/sdk/.../completion/AwsProperty.java` | 修改 | 新增 `compressionEnabled` 字段 |
| `api/sdk/.../completion/AwsMessageProperty.java` | 修改 | 新增 `compressionEnabled` 字段 |
| `api/server/.../completion/AwsClientManager.java` | 修改 | 支持压缩配置的客户端构建 + 缓存键调整 |

共计 3 个文件，影响范围限定在 AWS 协议适配层。

## 实现思路

### Step 1: 在 AwsProperty 中新增压缩配置字段

**文件**: `api/sdk/src/main/java/com/ke/bella/openapi/protocol/completion/AwsProperty.java`

**操作**:
- 新增 `boolean compressionEnabled` 字段，默认 `false`
- 在 `description()` 方法中添加字段描述：`"compressionEnabled" -> "是否启用传输压缩"`

**验证**: 编译通过，JSON 序列化/反序列化兼容（`@JsonInclude(NON_NULL)` 已存在）

### Step 2: 在 AwsMessageProperty 中新增压缩配置字段

**文件**: `api/sdk/src/main/java/com/ke/bella/openapi/protocol/completion/AwsMessageProperty.java`

**操作**:
- 新增 `boolean compressionEnabled` 字段，默认 `false`
- 在 `description()` 方法中添加字段描述

**验证**: 编译通过

### Step 3: 修改 AwsClientManager 支持压缩配置

**文件**: `api/server/src/main/java/com/ke/bella/openapi/protocol/completion/AwsClientManager.java`

**操作**:
1. 修改 `client()` 和 `asyncClient()` 方法签名，新增 `boolean compressionEnabled` 参数
2. 调整缓存键策略：将 `accessKeyId + ":" + compressionEnabled` 作为二级缓存键，确保压缩/非压缩客户端不混用
3. 当 `compressionEnabled = true` 时，在 `ClientOverrideConfiguration` 中启用请求压缩：
   ```java
   .overrideConfiguration(ClientOverrideConfiguration.builder()
       .requestCompression(c -> c.requestMinCompressionSizeInBytes(compressionEnabled ? 1024 : Integer.MAX_VALUE))
       // ... 其他配置
       .build())
   ```
4. AWS SDK 2.x 的 `requestCompression` 会自动对超过阈值的请求体进行 gzip 压缩，并设置 `Content-Encoding: gzip` 头

**验证**: `mvn clean compile` 编译通过

### Step 4: 更新 AwsAdaptor 和 AwsMessageAdaptor 调用处

**文件**:
- `api/server/src/main/java/com/ke/bella/openapi/protocol/completion/AwsAdaptor.java`
- `api/server/src/main/java/com/ke/bella/openapi/protocol/message/AwsMessageAdaptor.java`

**操作**:
- 在调用 `AwsClientManager.client()` / `AwsClientManager.asyncClient()` 时，传入 `property.isCompressionEnabled()` 参数

**验证**: `mvn clean compile` 编译通过

### Step 5: 编译验证

**命令**: `cd api/ && mvn clean compile`

**预期**: 所有模块编译通过，无错误

## 风险与依赖

| 风险 | 等级 | 缓解措施 |
|------|------|---------|
| AWS SDK 2.31.65 的 `requestCompression` API 可用性 | 低 | SDK 2.26.0+ 已支持此特性，当前版本足够 |
| 压缩增加 CPU 开销 | 低 | 仅对大请求体有意义（阈值 1024 字节），小请求不压缩 |
| 客户端缓存膨胀（同 region 多出一份实例） | 低 | 仅在实际使用压缩配置时才创建新实例，生产环境通道数有限 |
| Bedrock Converse API 是否支持压缩请求体 | 中 | AWS 文档确认 Bedrock Runtime 支持 gzip 请求压缩；需在实际通道上验证 |
| 向后兼容：已有 channel property JSON 无此字段 | 低 | 布尔默认 `false`，Jackson 反序列化缺失字段默认 false |

**依赖**: 无新增外部依赖，全部基于现有 AWS SDK 内置能力。

## 验证方式

1. **编译验证**: `cd api/ && mvn clean compile` 通过
2. **单元测试**（可选，需数据库）: 对 `AwsClientManager` 验证不同参数组合返回不同客户端实例
3. **集成验证**: 在测试环境创建一个 `compressionEnabled: true` 的 AWS 通道，发送较大 prompt（>1KB），通过网络抓包或 AWS CloudWatch 确认请求体被压缩
4. **回归验证**: 已有 AWS 通道不设置 `compressionEnabled`，行为与修改前一致
