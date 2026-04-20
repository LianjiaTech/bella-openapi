# Task 1 / Route 契约盘点

## 1. 范围说明

本文从总表中拆出 `Route` 能力点，覆盖 `/v1/route` 在 Go 重构前必须保持兼容的协议、不变量、回归基线与验证方式。

主索引文档：[`docs/design/refactor2go-task1-contract-inventory.md`](../refactor2go-task1-contract-inventory.md)

## 2. 协议

### 2.1 `POST /v1/route` 输入协议

#### 2.1.1 认证方式

- 这是 Bella 管理 / 诊断接口，请求体中显式传入 `apikey`

#### 2.1.2 Content-Type

- 请求默认是 `application/json`

#### 2.1.3 请求体结构

- 顶层是 JSON object
- 顶层核心字段：
  - `apikey`
  - `endpoint`
  - `model`
  - `queueMode`
- 顶层字段层次当前至少继续兼容：
  - `apikey`
  - `endpoint`
  - `model`
  - `queueMode`

#### 2.1.4 请求示例与语义

- 最小请求骨架当前至少应兼容：
  ```json
  {
    "apikey": "sk-xxx",
    "endpoint": "/v1/chat/completions",
    "model": "gpt-4o",
    "queueMode": 0
  }
  ```
- `apikey` 继续以明文形式作为诊断输入提供，而不是由标准 Bearer 鉴权链路推导
- `queueMode` 继续作为显式路由输入参与选路

### 2.2 `POST /v1/route` 输出协议

#### 2.2.1 成功响应

- 成功响应继续走 BellaAPI envelope，`data` 中是 route result，例如：
  ```json
  {
    "code": 200,
    "timestamp": 1710000000000,
    "data": {
      "channelCode": "channel_x",
      "entityType": "model",
      "entityCode": "gpt-4o",
      "protocol": "openai",
      "url": "https://...",
      "channelInfo": { "...": "..." },
      "priceInfo": "...",
      "queueMode": 0,
      "queueName": ""
    }
  }
  ```
- route result 核心字段：
  - `channelCode`
  - `entityType`
  - `entityCode`
  - `protocol`
  - `url`
  - `channelInfo`
  - `priceInfo`
  - `queueMode`
  - `queueName`
- route result 字段层次当前至少继续兼容：
  - `channelCode`
  - `entityType`
  - `entityCode`
  - `protocol`
  - `url`
  - `channelInfo`
  - `priceInfo`
  - `queueMode`
  - `queueName`
- 其中：
  - `channelInfo`：继续是结构化对象
  - `priceInfo`：当前继续是字符串化价格信息

#### 2.2.2 错误响应

- apikey 不存在或路由失败时继续返回 BellaAPI 统一错误体

## 3. 不能改变的行为

- **apikey 处理规则**
  - `/v1/route` 当前继续接收明文 `apikey`，并在内部转成 sha 后查询
  - 不存在的 apikey 继续报错，而不是返回空路由结果
- **路由输入规则**
  - 路由结果继续由 `endpoint + model + apikey + queueMode` 共同决定
- **路由输出语义**
  - 返回结果继续暴露命中的 channel 细节，而不仅仅是 channelCode
  - `queueMode` 与 `queueName` 继续作为 route result 的一部分显式返回
  - `channelInfo` 继续以结构化对象形式输出，而不是原始字符串

## 4. 回归验证基线

- 明文 apikey 输入、route result 结构与 BellaAPI envelope 保持一致
- `queueMode` / `queueName`、`channelInfo`、`priceInfo` 的输出语义保持一致
- apikey 不存在时的错误边界保持一致

## 5. 验证方式

- 回放正常路由、缺失/错误 apikey、不同 `queueMode` 与不同 endpoint/model 的样例
- 对比 Java 与 Go 的 route result 字段、错误响应与命中渠道细节
