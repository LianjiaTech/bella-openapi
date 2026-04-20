# Task 1 / Document 契约盘点

## 1. 范围说明

本文从总表中拆出 `Document` 能力点，覆盖 `/v1/document/parse` 在 Go 重构前必须保持兼容的协议、不变量、回归基线与验证方式。

主索引文档：[`docs/design/refactor2go-task1-contract-inventory.md`](../refactor2go-task1-contract-inventory.md)

## 2. 协议

### 2.1 `POST /v1/document/parse` 输入协议

#### 2.1.1 认证方式

- 默认使用 `Authorization: Bearer ...`

#### 2.1.2 Content-Type

- 请求默认是 `application/json`

#### 2.1.3 请求体结构

- 顶层是 JSON object
- 核心字段：
  - `file`
  - `user`
  - `model`
  - `type`
  - `callbackUrl`
  - `maxTimeoutMillis`
- `file` 结构核心字段：
  - `id`
  - `name`
  - `type`
  - `mime_type`
- `file` 字段层次当前至少继续兼容：
  - `id`：文件 ID
  - `name`：文件名
  - `type`：文件类型，例如 `pdf`
  - `mime_type`：MIME 类型，例如 `application/pdf`
- `type` 当前区分至少两种模式：
  - `task`
  - `blocking`
- 未在固定字段中的额外请求字段，当前不在文档中显式承诺兼容，Go 重构时应以 Java 现状行为为准，不要自行扩张或收紧

#### 2.1.4 请求示例与语义

- 最小请求骨架仍应兼容：
  ```json
  {
    "file": {
      "id": "file_x",
      "name": "a.pdf",
      "mime_type": "application/pdf"
    },
    "model": "doc-parse-x",
    "type": "task"
  }
  ```
- `type=task` 表示异步提交任务，当前继续直接返回 task info，而不是阻塞等待结果
- `type=blocking` 表示阻塞等待任务完成后直接返回解析结果，而不是先返回 taskId
- `callbackUrl` 在 task 模式下继续作为回调注册输入
- `maxTimeoutMillis` 在 blocking 模式下继续作为请求级等待时间输入

### 2.2 `POST /v1/document/parse` 输出协议

#### 2.2.1 成功响应

- 当 `type=task` 时，继续返回 task info 结构，例如：
  ```json
  { "taskId": "channel_x___task_y" }
  ```
- `taskId` 对外继续使用字符串编码，而不是对象结构
- 当 `type=blocking` 时，继续返回 `DocParseResponse`，例如：
  ```json
  {
    "status": "success",
    "message": "",
    "result": { "...": "..." }
  }
  ```
- `DocParseResponse` 核心字段：
  - `result`
  - `status`
  - `message`
- `result` 当前继续返回递归树形结构 `DocParseResult`
- `result` 字段层次当前至少继续兼容：
  - `source_file.id`
  - `source_file.name`
  - `source_file.type`
  - `source_file.mime_type`
  - `summary`
  - `tokens`
  - `path`
  - `element`
  - `children`
- `result.element` 当前至少继续兼容：
  - `type`
  - `positions[]`
  - `name`
  - `description`
  - `text`
  - `image`
  - `rows[]`
- `result.element.positions[]` 当前至少继续兼容：
  - `bbox`
  - `page`
- `result.element.image` 当前至少继续兼容：
  - `type`
  - `url`
  - `base64`
  - `file_id`
- `result.element.rows[].cells[]` 当前至少继续兼容：
  - `path`
  - `text`
  - `nodes`
- `status` 当前至少包括：
  - `success`
  - `failed`
  - `processing`

#### 2.2.2 错误响应

- 失败时继续返回统一错误体，而不是 task / result success body
- blocking 模式超时当前继续返回超时错误，而不是静默退化成 task 模式成功响应
- 最小错误骨架仍应兼容：
  ```json
  {
    "error": {
      "code": "408",
      "httpCode": 408,
      "message": "...",
      "type": "Channel Exception"
    }
  }
  ```
- `error` 对象当前至少继续兼容：
  - `code`
  - `httpCode`
  - `message`
  - `type`

### 2.3 `GET /v1/document/parse` 输入协议

#### 2.3.1 认证方式

- 默认使用 `Authorization: Bearer ...`

#### 2.3.2 Query 参数

- 当前通过 query parameter `task_id` 查询任务结果
- `task_id` 当前继续使用 `channelCode___taskId` 编码值，而不是裸 taskId

#### 2.3.3 请求语义

- 该接口继续只负责查询既有任务结果，不负责提交新任务
- 服务端继续先从 `task_id` 中拆出 channelCode，再定位对应 channel 查询结果

### 2.4 `GET /v1/document/parse` 输出协议

#### 2.4.1 成功响应

- 响应继续是 `DocParseResponse`
- 典型响应仍应兼容：
  ```json
  {
    "status": "processing",
    "message": "",
    "result": { "...": "..." }
  }
  ```
- 核心字段：
  - `result`
  - `status`
  - `message`
- 返回的 `result` 字段层次继续与 `POST /v1/document/parse` blocking 成功响应保持一致
- `status` 当前至少包括：
  - `success`
  - `failed`
  - `processing`

#### 2.4.2 错误响应

- `task_id` 缺失、格式非法、任务查询失败时继续返回统一错误体
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

## 3. 不能改变的行为

- **模式语义**
  - `POST /v1/document/parse` 继续同时承担“提交任务”和“阻塞等待结果”两种语义
  - `type=blocking` 时继续等待任务完成再返回，而不是先返回 taskId
  - 非 blocking 模式继续直接返回 task info
- **task_id 协议**
  - 对外暴露的 `taskId` 当前继续编码为 `channelCode___taskId`
  - `GET /parse` 继续从 `task_id` 中拆出 channelCode，再据此定位 channel，而不是按 model 或全局任务表查询
- **阻塞等待规则**
  - blocking 模式当前继续轮询等待任务完成
  - `maxTimeoutMillis` 当前存在最小值下限，不能低于既定阈值
  - 超时后继续返回 408 语义错误，而不是静默转成异步
- **回调语义**
  - task 模式下如果提供 `callbackUrl`，当前继续注册回调任务
  - 查询结果时若响应里附带 callback runnable，当前继续异步执行
- **路由与治理规则**
  - `POST /parse` 继续按 `endpoint + model` 选路
  - `GET /parse` 继续按 `task_id` 中的 channelCode 反查路由上下文
  - 非 private 请求继续触发并发计数等治理副作用

## 4. 回归验证基线

- task 与 blocking 两种模式的协议边界保持一致
- `taskId` 编码与查询拆解规则保持一致
- 阻塞等待、超时错误、回调语义与治理副作用保持一致

## 5. 验证方式

- 回放 task 模式、blocking 模式、超时场景、`GET /parse` 查询、callback 场景
- 对比 Java 与 Go 的响应结构、超时行为、任务路由与日志收口
