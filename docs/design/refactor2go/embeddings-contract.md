# Task 1 / Embeddings 契约盘点

## 1. 范围说明

本文从总表中拆出 `Embeddings` 能力点，聚焦 `/v1/embeddings` 在 Go 重构前必须保持兼容的协议、不变量、回归基线与验证方式。

主索引文档：[`docs/design/refactor2go-task1-contract-inventory.md`](../refactor2go-task1-contract-inventory.md)

## 2. 协议

### 2.1 `POST /v1/embeddings` 输入协议

#### 2.1.1 认证方式

- 默认使用 `Authorization: Bearer ...`

#### 2.1.2 Content-Type

- 请求默认是 `application/json`

#### 2.1.3 请求体结构

- 顶层是 JSON object
- 核心字段：
  - `model`
  - `input`
  - `encoding_format`
  - `dimensions`
  - `user`
- 顶层字段层次当前至少继续兼容：
  - `model`：字符串，作为选路模型标识
  - `input`：字符串或字符串数组
  - `encoding_format`：字符串
  - `dimensions`：整数
  - `user`：字符串
- `input` 允许是单个字符串，也允许是字符串数组
- `encoding_format` 当前兼容：
  - `float`
  - `base64`
- `dimensions` 当前继续作为可选降维输入，而不是独立 endpoint 或 header 参数

#### 2.1.4 请求示例与语义

- 最小请求骨架仍应保持为：
  ```json
  {
    "model": "text-embedding-3-large",
    "input": "hello",
    "encoding_format": "float"
  }
  ```
- 批量请求仍应兼容：
  ```json
  {
    "model": "text-embedding-3-large",
    "input": ["hello", "world"],
    "encoding_format": "base64",
    "dimensions": 1024
  }
  ```
- `input` 为单个字符串时，当前继续代表单条 embedding 请求
- `input` 为字符串数组时，当前继续代表批量 embedding 请求，而不是多次单条请求包装

### 2.2 `POST /v1/embeddings` 输出协议

#### 2.2.1 Content-Type

- 响应默认是 `application/json`

#### 2.2.2 成功响应

- 返回 embedding response JSON 结构，例如：
  ```json
  {
    "object": "list",
    "data": [
      {
        "object": "embedding",
        "embedding": [0.1, 0.2],
        "index": 0
      }
    ],
    "model": "text-embedding-3-large",
    "usage": {
      "prompt_tokens": 2,
      "total_tokens": 2
    }
  }
  ```
- 顶层核心字段：
  - `object`
  - `data`
  - `model`
  - `usage`
- 顶层字段层次当前至少继续兼容：
  - `object`：固定为 `list`
  - `data`：embedding 数组
  - `model`：实际执行模型
  - `usage`：token 统计对象
- `data[]` 元素核心字段：
  - `object=embedding`
  - `embedding`
  - `index`
- `data[]` 元素字段层次当前至少继续兼容：
  - `object`：固定为 `embedding`
  - `embedding`：`float[]`、`double[]`、`base64` 字符串或其他与 `encoding_format` 对应的编码结果
  - `index`：从 `0` 开始的结果序号
- `embedding` 当前既可能是 float 数组，也可能是 base64 字符串或其他与 `encoding_format` 对应的编码结果
- `usage` 当前至少继续兼容：
  - `prompt_tokens`
  - `total_tokens`

#### 2.2.3 错误响应

- 失败时继续返回统一错误体，而不是 embedding success body
- 最小错误骨架仍应兼容：
  ```json
  {
    "error": {
      "code": "400",
      "httpCode": 400,
      "message": "...",
      "type": "Illegal Argument"
    }
  }
  ```
- `error` 对象当前至少继续兼容：
  - `code`
  - `httpCode`
  - `message`
  - `type`

## 3. 不能改变的行为

- **模型与路由规则**
  - `model` 继续来自请求体，并用于 `/v1/embeddings` 的选路
  - 请求继续按 `endpoint + model` 选择 channel
- **输入归一化规则**
  - `input` 为单个字符串时，继续在入口归一化为单元素数组后再向上游发起请求
  - `input` 为数组时，继续保留批量 embedding 语义，而不是拆成多次单条请求
- **批量约束规则**
  - 当 `input` 是数组时，当前继续受 channel property 中 `batchSize` 上限约束
  - 超过上限时继续报错，而不是静默截断或自动分批
- **协议边界**
  - 对外继续表现为 OpenAI Embeddings 协议，而不是暴露底层私有 embedding 结构
  - 即使底层是私有协议，响应出口仍应保持 `object/data/model/usage` 这一层外部结构
- **usage 规则**
  - 若上游已返回 `usage`，继续直接使用上游值
  - 若上游未返回 `usage`，当前继续根据预计算指标或请求内容补齐 `prompt_tokens` 与 `total_tokens`
  - 4xx 错误场景下，当前继续把 usage 归零
- **治理侧约定**
  - 非 private 请求继续触发并发计数等治理副作用

## 4. 回归验证基线

- 单条与批量输入的协议形态保持一致
- `float` / `base64` 两类编码输出保持兼容
- 批量上限校验、错误协议、usage 补齐规则保持一致
- 非 private 请求的治理副作用保持一致

## 5. 验证方式

- 回放单条输入、批量输入、超 batchSize、上游缺失 usage、4xx 错误等样例
- 对比 Java 与 Go 的响应结构、字段类型、usage 与错误形态
