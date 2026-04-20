# Task 1 / Metrics-Log 契约盘点

## 1. 范围说明

本文从总表中拆出 `Metrics / Log` 能力点，覆盖 `/v1/metrics` 与 `/v1/log` 在 Go 重构前必须保持兼容的协议、不变量、回归基线与验证方式。

主索引文档：[`docs/design/refactor2go-task1-contract-inventory.md`](../refactor2go-task1-contract-inventory.md)

## 2. 协议

### 2.1 `GET /v1/metrics` 输入协议

#### 2.1.1 认证方式

- 这是 Bella 管理面接口

#### 2.1.2 Query 参数

- 核心参数：
  - `endpoint`
- 可选参数：
  - `channelType`
  - `model`
- `channelType` 默认值当前是 `model`

#### 2.1.3 请求语义

- 该接口继续用于查询指定 endpoint / model / channelType 维度下的 channel 指标
- 未传 `model` 时，当前继续允许基于 endpoint 聚合查询

### 2.2 `GET /v1/metrics` 输出协议

#### 2.2.1 Content-Type

- 响应默认是 `application/json;charset=UTF-8`

#### 2.2.2 成功响应

- 成功响应继续走 BellaAPI envelope，`data` 中是数组结果，例如：
  ```json
  {
    "code": 200,
    "timestamp": 1710000000000,
    "data": [
      {
        "channelCode": "channel_x",
        "entityCode": "gpt-4o",
        "endpoint": "/v1/chat/completions",
        "metrics": { "...": "..." }
      }
    ]
  }
  ```
- BellaAPI envelope 当前至少继续兼容：
  - `code`
  - `message`
  - `timestamp`
  - `data`
  - `stacktrace`
- `data[]` 单项核心字段：
  - `channelCode`
  - `entityCode`
  - `endpoint`
  - `metrics`
- `data[]` 单项字段层次当前至少继续兼容：
  - `channelCode`
  - `entityCode`
  - `endpoint`
  - `metrics`
- `metrics` 当前继续保持 `Map<String, Object>` 开放结构

#### 2.2.3 错误响应

- 查询失败时继续返回 BellaAPI 统一错误体

### 2.3 `POST /v1/log` 输入协议

#### 2.3.1 认证方式

- 这是内部 / 管理侧写日志接口，请求体中显式提供 `akSha`

#### 2.3.2 Content-Type

- 请求默认是 `application/json`

#### 2.3.3 请求体结构

- 请求体是 `EndpointProcessData`
- 入口当前至少要求：
  - `endpoint`
  - `akSha`
  - `bellaTraceId`
- 请求体字段层次当前至少继续兼容：
  - `akSha`
  - `requestId`
  - `accountType`
  - `accountCode`
  - `akCode`
  - `parentAkCode`
  - `endpoint`
  - `model`
  - `channelCode`
  - `isPrivate`
  - `user`
  - `requestMillis`
  - `requestTime`
  - `firstPackageTime`
  - `transcriptionDuration`
  - `duration`
  - `request`
  - `responseRaw`
  - `requestRaw`
  - `response`
  - `usage`
  - `metrics`
  - `forwardUrl`
  - `protocol`
  - `priceInfo`
  - `encodingType`
  - `supplier`
  - `isMock`
  - `bellaTraceId`
  - `functionCallSimulate`
  - `channelRequestId`
  - `cost`
  - `innerLog`
  - `maxWaitSec`
  - `nativeSend`
  - `batch`
  - `costDetails`
  - `overrideInnerLog`
  - `clientIp`
- 当前继续允许请求体携带 `overrideInnerLog` 等会影响后续收口语义的字段

#### 2.3.4 请求示例与语义

- 最小请求骨架当前至少应兼容：
  ```json
  {
    "endpoint": "/v1/chat/completions",
    "akSha": "ak_sha_x",
    "bellaTraceId": "trace_x"
  }
  ```
- `akSha` 继续作为绑定 apikey 与补齐上下文的关键输入，而不是普通审计字段

### 2.4 `POST /v1/log` 输出协议

#### 2.4.1 Content-Type

- 响应默认是 `application/json;charset=UTF-8`

#### 2.4.2 成功响应

- 成功响应继续走 BellaAPI envelope，例如：
  ```json
  {
    "code": 200,
    "timestamp": 1710000000000,
    "data": true
  }
  ```
- BellaAPI envelope 当前至少继续兼容：
  - `code`
  - `message`
  - `timestamp`
  - `data`
  - `stacktrace`
- `data` 当前继续返回布尔值 `true`

#### 2.4.3 错误响应

- 参数缺失或 akSha 无法映射到 apikey 时继续返回 BellaAPI 统一错误体

## 3. 不能改变的行为

### 3.1 Metrics

- **过滤规则**
  - 当前继续只统计 ACTIVE channel
  - `channelType=endpoint` 时继续直接按 endpoint 查 channel
  - 其他场景下若传了 `model`，当前继续先做 terminal model name 映射
  - 若未传 `model`，当前继续先找出 endpoint 下的全部 active models，再汇总对应 channel 指标
- **结果映射规则**
  - 返回结果中的 `entityCode` 当前继续来自 channel 对应的 entityCode，而不是 channelCode 本身
  - `metrics` 继续保持开放 map 结构，而不是固定字段 DTO

### 3.2 Log

- **校验规则**
  - `endpoint`、`akSha`、`bellaTraceId` 继续是必填项
- **apikey 绑定规则**
  - `akSha` 当前继续被用于查询并回填 `ApikeyInfo`
  - 找不到对应 apikey 时继续报错，而不是写匿名日志
- **写入规则**
  - 写入前当前继续强制 `innerLog=false`
  - 调用方不能通过直接调用 `/v1/log`，把一条普通外部写入日志声明成标准内部日志
  - 如果请求显式携带 `overrideInnerLog=true`，该字段继续保留实际语义：系统在后续分发时仍会把该记录切换为“内部 / cost-only”语义处理
  - 这不是普通透传字段；它会影响后续日志、计费/成本、指标或治理链路如何看待这条记录
  - 成功写入后继续返回 `true`

## 4. 回归验证基线

- Metrics 与 Log 两个接口的 BellaAPI envelope 保持一致
- Metrics 的 ACTIVE 过滤、entityCode 映射与开放 map 结构保持一致
- Log 的必填校验、akSha 绑定、`innerLog=false` 与 `overrideInnerLog` 语义保持一致

## 5. 验证方式

- 回放 metrics 的不同参数组合与 log 的正常/缺参/akSha 不存在/overrideInnerLog 场景
- 对比 Java 与 Go 的 envelope、字段映射、日志写入副作用与错误边界
