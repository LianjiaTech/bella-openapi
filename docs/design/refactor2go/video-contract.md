# Task 1 / Video 契约盘点

## 1. 范围说明

本文从总表中拆出 `Video` 能力点，覆盖 `/v1/videos*` 在 Go 重构前必须保持兼容的协议、不变量、回归基线与验证方式。

主索引文档：[`docs/design/refactor2go-task1-contract-inventory.md`](../refactor2go-task1-contract-inventory.md)

## 2. 协议

### 2.1 `POST /v1/videos` 输入协议

#### 2.1.1 认证方式

- 默认使用 `Authorization: Bearer ...`

#### 2.1.2 Content-Type

- 请求是 `multipart/form-data`

#### 2.1.3 请求体结构

- multipart 核心字段：
  - `prompt`
  - `model`
  - `input_reference`
  - `seconds`
  - `size`
- multipart 字段层次当前至少继续兼容：
  - `prompt`
  - `model`
  - `input_reference`：`MultipartFile`
  - `seconds`：字符串
  - `size`：字符串
- `prompt` 与 `model` 当前继续是必填项
- `input_reference` 当前通过文件上传 `MultipartFile` 传入，而不是 URL / base64
- `seconds` 当前继续以字符串形式传入，再在入口做整数校验

#### 2.1.4 请求示例与语义

- 最小创建请求形态仍应保持为 multipart 表单：
  - `prompt`
  - `model`
- 当提供 `input_reference` 时，入口当前继续先把文件上传到文件服务，再把得到的 file id 写入下游 `input_reference`

### 2.2 `POST /v1/videos` 输出协议

#### 2.2.1 Content-Type

- 响应默认是 `application/json`

#### 2.2.2 成功响应

- 创建成功继续返回 `VideoJob` JSON 结构，例如：
  ```json
  {
    "id": "video_x",
    "object": "video",
    "model": "kling-v1",
    "status": "queued",
    "progress": 0,
    "created_at": 1710000000,
    "prompt": "a cat running",
    "seconds": "5",
    "size": "1280x720"
  }
  ```
- 顶层核心字段：
  - `id`
  - `object=video`
  - `model`
  - `status`
  - `progress`
  - `created_at`
  - `completed_at`
  - `expires_at`
  - `prompt`
  - `seconds`
  - `size`
  - `remixed_from_video_id`
- `VideoJob` 顶层字段层次当前至少继续兼容：
  - `id`
  - `object`
  - `model`
  - `status`
  - `progress`
  - `created_at`
  - `completed_at`
  - `expires_at`
  - `prompt`
  - `seconds`
  - `size`
  - `remixed_from_video_id`

#### 2.2.3 错误响应

- 参数错误、模型不存在、无可用 channel、文件上传失败等场景，继续统一返回错误体，而不是返回伪造的 `VideoJob` success body
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

### 2.3 `GET /v1/videos/{id}` 输入协议

#### 2.3.1 认证方式

- 默认使用 `Authorization: Bearer ...`

#### 2.3.2 Path 参数

- 核心参数：
  - `id`

### 2.4 `GET /v1/videos/{id}` 输出协议

#### 2.4.1 Content-Type

- 响应默认是 `application/json`

#### 2.4.2 成功响应

- 查询成功继续返回 `VideoJob` JSON 结构
- 顶层核心字段：
  - `id`
  - `object=video`
  - `model`
  - `status`
  - `progress`
  - `created_at`
  - `completed_at`
  - `expires_at`
  - `prompt`
  - `seconds`
  - `size`
  - `remixed_from_video_id`

#### 2.4.3 错误响应

- 资源不存在时继续返回统一错误体

### 2.5 `GET /v1/videos/{id}/content` 输入协议

#### 2.5.1 认证方式

- 默认使用 `Authorization: Bearer ...`

#### 2.5.2 Path / Query 参数

- path 参数：
  - `id`
- 可选 query 参数：
  - `variant`

#### 2.5.3 请求语义

- 该接口继续用于获取已完成视频任务绑定的内容下载地址
- `variant` query 当前存在但未参与内容选择

### 2.6 `GET /v1/videos/{id}/content` 输出协议

#### 2.6.1 成功响应

- 成功时继续返回 `302` 重定向，而不是直接输出视频二进制
- `Location` header 继续指向实际文件地址

#### 2.6.2 错误响应

- 视频不存在、状态未完成或绑定文件缺失时继续返回统一错误体

### 2.7 `POST /v1/videos/{id}/remix` 输入协议

#### 2.7.1 认证方式

- 默认使用 `Authorization: Bearer ...`

#### 2.7.2 Content-Type

- 请求默认是 `application/json`

#### 2.7.3 Path / 请求体结构

- path 参数：
  - `id`
- 请求体当前使用 `VideoRemixRequest`
- 核心字段：
  - `prompt`
  - `user`
- `VideoRemixRequest` 字段层次当前至少继续兼容：
  - `prompt`
  - `user`

### 2.8 `POST /v1/videos/{id}/remix` 输出协议

#### 2.8.1 错误响应

- `remix` 能力当前仍是不支持状态
- 继续走统一错误协议表达“不支持”，而不是返回占位成功结果
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

### 2.9 `GET /v1/videos` 输入协议

#### 2.9.1 认证方式

- 默认使用 `Authorization: Bearer ...`

#### 2.9.2 Query 参数

- 支持：
  - `after`
  - `limit`
  - `order`

### 2.10 `GET /v1/videos` 输出协议

#### 2.10.1 Content-Type

- 响应默认是 `application/json`

#### 2.10.2 成功响应

- 返回 list 结构，例如：
  ```json
  {
    "object": "list",
    "data": [
      { "id": "video_x", "object": "video", "status": "completed" }
    ],
    "last_id": "video_x",
    "has_more": false
  }
  ```
- 顶层核心字段：
  - `object=list`
  - `data`
  - `last_id`
  - `has_more`
- `data[]` 元素继续是 `VideoJob`
- `data[]` 中每个元素字段层次继续与单任务 `VideoJob` 响应保持一致

#### 2.10.3 错误响应

- 分页参数非法时继续返回统一错误体

### 2.11 `DELETE /v1/videos/{id}` 输入协议

#### 2.11.1 认证方式

- 默认使用 `Authorization: Bearer ...`

#### 2.11.2 Path 参数

- 核心参数：
  - `id`

### 2.12 `DELETE /v1/videos/{id}` 输出协议

#### 2.12.1 Content-Type

- 响应默认是 `application/json`

#### 2.12.2 成功响应

- 删除成功继续返回 `VideoJob` JSON 结构，而不是布尔值
- 顶层核心字段：
  - `id`
  - `object=video`
  - `model`
  - `status`
  - `progress`
  - `created_at`
  - `completed_at`
  - `expires_at`
  - `prompt`
  - `seconds`
  - `size`
  - `remixed_from_video_id`

#### 2.12.3 错误响应

- 资源不存在时继续返回统一错误体

## 3. 不能改变的行为

- **创建前置规则**
  - `prompt` 与 `model` 继续是必填项
  - `input_reference` 若存在，继续先上传为文件服务中的文件，再把 file id 写入创建请求
  - `seconds` 当前继续做范围校验
  - `size` 当前继续只接受有限枚举值
- **查询与内容获取规则**
  - `GET /v1/videos/{id}` 继续作为单任务状态查询入口
  - `GET /v1/videos/{id}/content` 继续只允许在 `status=completed` 时访问
  - 内容接口继续读取任务绑定的 file id，再 302 跳转到真实文件 URL
  - `variant` query 当前存在但未参与内容选择，重构时不要静默改变其语义
- **列表分页规则**
  - `limit` 默认继续为 `20`
  - `limit` 继续限制在 `1..100`
  - `order` 默认继续为 `desc`，并只接受 `asc/desc`
  - `has_more` 与 `last_id` 继续按“多取 1 条”逻辑生成
- **作用域规则**
  - 列表查询继续按当前 apikey 所属 `spaceCode` 做隔离，而不是全局列出所有视频任务
- **协议边界**
  - `remix` 能力当前仍是不支持状态，重构前后不能悄悄变成其他行为
- **usage / 收口口径**
  - video 的最终 usage 当前继续以后续异步回调结果为准，而不是在 `POST /v1/videos` 创建时就确定
  - 只有任务完成且回调结果携带 usage 时，当前才继续进行这笔视频请求的正式成本收口
  - 创建响应、查询响应与最终计费收口之间继续不是同一步完成

## 4. 回归验证基线

- `VideoJob` 结构、list 分页结构与内容接口重定向行为保持一致
- `limit/order/has_more/last_id` 规则保持一致
- `remix` 仍然是不支持状态
- usage 的异步收口口径保持一致

## 5. 验证方式

- 回放创建、查询、内容获取、删除、列表、不支持 remix、异常场景等样例
- 对比 Java 与 Go 的 JSON 结构、302 行为、分页字段与最终收口记录
