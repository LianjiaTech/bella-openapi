# Task 1 / Models 契约盘点

## 1. 范围说明

本文从总表中拆出 `Models` 能力点，覆盖 `/v1/models` 在 Go 重构前必须保持兼容的协议、不变量、回归基线与验证方式。

主索引文档：[`docs/design/refactor2go-task1-contract-inventory.md`](../refactor2go-task1-contract-inventory.md)

## 2. 协议

### 2.1 `GET /v1/models` 输入协议

#### 2.1.1 认证方式

- 默认使用 `Authorization: Bearer ...`

#### 2.1.2 Query 参数

- 当前不暴露额外筛选参数、排序参数或分页入参

#### 2.1.3 请求语义

- 当前继续只支持无请求体的 `GET /v1/models`
- query string 中即使携带额外筛选参数，当前也不构成受支持协议的一部分

### 2.2 `GET /v1/models` 输出协议

#### 2.2.1 Content-Type

- 响应默认是 `application/json`

#### 2.2.2 成功响应

- 返回 list JSON 结构，例如：
  ```json
  {
    "object": "list",
    "data": [
      {
        "id": "gpt-4o",
        "object": "model",
        "created": 1710000000,
        "owned_by": "openai"
      }
    ],
    "last_id": null,
    "has_more": false
  }
  ```
- 顶层核心字段：
  - `object=list`
  - `data`
  - `last_id`
  - `has_more`
- 顶层字段层次当前至少继续兼容：
  - `object`：固定为 `list`
  - `data`：模型数组
  - `last_id`：当前通常为空
  - `has_more`：当前通常为 `false`
- `data[]` 元素核心字段：
  - `id`
  - `object=model`
  - `created`
  - `owned_by`
- `data[]` 元素字段层次当前至少继续兼容：
  - `id`：模型名
  - `object`：固定为 `model`
  - `created`：秒级时间戳
  - `owned_by`：优先取 ownerName，缺失时回落到 ownerCode

#### 2.2.3 错误响应

- 失败时继续返回统一错误体
- `error` 对象当前至少继续兼容：
  - `code`
  - `httpCode`
  - `message`
  - `type`
  - `param`
  - `sensitive`

## 3. 不能改变的行为

- **过滤规则**
  - `/v1/models` 当前继续只返回 `ACTIVE` 状态模型
  - 当前继续走带权限过滤的模型查询，而不是把所有模型无差别暴露给调用方
- **输出映射规则**
  - `id` 继续对应模型名
  - `owned_by` 继续优先取 ownerName，缺失时回落到 ownerCode
  - `created` 继续输出秒级时间戳
- **列表语义**
  - 当前返回结构继续兼容 OpenAI 风格 models list，而不是裸数组
  - 当前不暴露额外筛选参数、排序参数或分页入参

## 4. 回归验证基线

- list 结构、字段映射与权限过滤边界保持一致
- 只返回 ACTIVE 模型的语义保持一致

## 5. 验证方式

- 回放普通查询、不同权限主体查询等样例
- 对比 Java 与 Go 的 list 结构、`owned_by` / `created` 映射与过滤结果
