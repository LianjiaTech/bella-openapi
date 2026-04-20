# Task 1 / OCR 契约盘点

## 1. 范围说明

本文从总表中拆出 `OCR` 能力点，覆盖 `/v1/ocr/*` 在 Go 重构前必须保持兼容的协议、不变量、回归基线与验证方式。

主索引文档：[`docs/design/refactor2go-task1-contract-inventory.md`](../refactor2go-task1-contract-inventory.md)

## 2. 协议

### 2.1 `POST /v1/ocr/idcard` 输入协议

#### 2.1.1 认证方式

- 默认使用 `Authorization: Bearer ...`

#### 2.1.2 Content-Type

- 请求默认是 `application/json`

#### 2.1.3 请求体结构

- 顶层是 JSON object
- 通用核心字段：
  - `model`
  - `user`
- 图片输入三选一：
  - `image_base64`
  - `image_url`
  - `file_id`
- 允许携带平铺的扩展字段与嵌套 `extra_body`

#### 2.1.4 请求示例与语义

- 最小请求骨架仍应保持为：
  ```json
  {
    "model": "ocr_x",
    "image_url": "https://..."
  }
  ```
- 仍应继续兼容其他等价输入方式：
  ```json
  { "model": "ocr_x", "image_base64": "..." }
  { "model": "ocr_x", "file_id": "file_x" }
  ```

### 2.2 `POST /v1/ocr/idcard` 输出协议

#### 2.2.1 Content-Type

- 响应默认是 `application/json`

#### 2.2.2 成功响应

- 顶层字段至少继续兼容：
  - `request_id`
  - `side`
  - `data`
- 典型响应仍应兼容：
  ```json
  {
    "request_id": "req_x",
    "side": "portrait",
    "data": {
      "name": "...",
      "birth_date": "...",
      "idcard_number": "..."
    }
  }
  ```
- `data` 中继续保留身份证场景专属字段集合

#### 2.2.3 错误响应

- 失败时继续返回统一错误体，而不是伪造 OCR success body
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

### 2.3 `POST /v1/ocr/bankcard` 输入协议

#### 2.3.1 认证方式

- 默认使用 `Authorization: Bearer ...`

#### 2.3.2 Content-Type

- 请求默认是 `application/json`

#### 2.3.3 请求体结构

- 顶层是 JSON object
- 通用核心字段：
  - `model`
  - `user`
- 图片输入三选一：
  - `image_base64`
  - `image_url`
  - `file_id`
- 允许携带平铺的扩展字段与嵌套 `extra_body`

#### 2.3.4 请求示例与语义

- 最小请求骨架仍应保持为：
  ```json
  {
    "model": "ocr_x",
    "image_url": "https://..."
  }
  ```

### 2.4 `POST /v1/ocr/bankcard` 输出协议

#### 2.4.1 Content-Type

- 响应默认是 `application/json`

#### 2.4.2 成功响应

- 顶层字段至少继续兼容：
  - `request_id`
  - `data`
- 典型响应仍应兼容：
  ```json
  {
    "request_id": "req_x",
    "data": {
      "card_number": "...",
      "bank_name": "...",
      "card_type": "...",
      "valid_date": "..."
    }
  }
  ```
- `data` 中继续保留银行卡场景专属字段集合

#### 2.4.3 错误响应

- 失败时继续返回统一错误体，而不是伪造 OCR success body
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

### 2.5 `POST /v1/ocr/hmt-residence-permit` 输入协议

#### 2.5.1 认证方式

- 默认使用 `Authorization: Bearer ...`

#### 2.5.2 Content-Type

- 请求默认是 `application/json`

#### 2.5.3 请求体结构

- 顶层是 JSON object
- 通用核心字段：
  - `model`
  - `user`
- 图片输入三选一：
  - `image_base64`
  - `image_url`
  - `file_id`
- 允许携带平铺的扩展字段与嵌套 `extra_body`

#### 2.5.4 请求示例与语义

- 最小请求骨架仍应保持为：
  ```json
  {
    "model": "ocr_x",
    "image_url": "https://..."
  }
  ```

### 2.6 `POST /v1/ocr/hmt-residence-permit` 输出协议

#### 2.6.1 Content-Type

- 响应默认是 `application/json`

#### 2.6.2 成功响应

- 顶层字段至少继续兼容：
  - `request_id`
  - `side`
  - `data`
- 典型响应仍应兼容：
  ```json
  {
    "request_id": "req_x",
    "side": "portrait",
    "data": {
      "name": "...",
      "birth_date": "...",
      "idcard_number": "..."
    }
  }
  ```
- `data` 中继续保留港澳台居住证场景专属字段集合

#### 2.6.3 错误响应

- 失败时继续返回统一错误体，而不是伪造 OCR success body
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

### 2.7 `POST /v1/ocr/tmp-idcard` 输入协议

#### 2.7.1 认证方式

- 默认使用 `Authorization: Bearer ...`

#### 2.7.2 Content-Type

- 请求默认是 `application/json`

#### 2.7.3 请求体结构

- 顶层是 JSON object
- 通用核心字段：
  - `model`
  - `user`
- 图片输入三选一：
  - `image_base64`
  - `image_url`
  - `file_id`
- 允许携带平铺的扩展字段与嵌套 `extra_body`

#### 2.7.4 请求示例与语义

- 最小请求骨架仍应保持为：
  ```json
  {
    "model": "ocr_x",
    "image_url": "https://..."
  }
  ```

### 2.8 `POST /v1/ocr/tmp-idcard` 输出协议

#### 2.8.1 Content-Type

- 响应默认是 `application/json`

#### 2.8.2 成功响应

- 顶层字段至少继续兼容：
  - `request_id`
  - `data`
- `tmp-idcard` 继续返回临时身份证场景专属字段集合

#### 2.8.3 错误响应

- 失败时继续返回统一错误体，而不是伪造 OCR success body
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

### 2.9 `POST /v1/ocr/overseas-passport` 输入协议

#### 2.9.1 认证方式

- 默认使用 `Authorization: Bearer ...`

#### 2.9.2 Content-Type

- 请求默认是 `application/json`

#### 2.9.3 请求体结构

- 顶层是 JSON object
- 通用核心字段：
  - `model`
  - `user`
- 图片输入三选一：
  - `image_base64`
  - `image_url`
  - `file_id`
- 允许携带平铺的扩展字段与嵌套 `extra_body`

#### 2.9.4 请求示例与语义

- 最小请求骨架仍应保持为：
  ```json
  {
    "model": "ocr_x",
    "image_url": "https://..."
  }
  ```

### 2.10 `POST /v1/ocr/overseas-passport` 输出协议

#### 2.10.1 Content-Type

- 响应默认是 `application/json`

#### 2.10.2 成功响应

- 顶层字段至少继续兼容：
  - `request_id`
  - `data`
- `overseas-passport` 继续返回护照场景专属字段集合

#### 2.10.3 错误响应

- 失败时继续返回统一错误体，而不是伪造 OCR success body
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

### 2.11 `POST /v1/ocr/general` 输入协议

#### 2.11.1 认证方式

- 默认使用 `Authorization: Bearer ...`

#### 2.11.2 Content-Type

- 请求默认是 `application/json`

#### 2.11.3 请求体结构

- 顶层是 JSON object
- 通用核心字段：
  - `model`
  - `user`
- 图片输入三选一：
  - `image_base64`
  - `image_url`
  - `file_id`
- 允许携带平铺的扩展字段与嵌套 `extra_body`

#### 2.11.4 请求示例与语义

- 最小请求骨架仍应保持为：
  ```json
  {
    "model": "ocr_x",
    "image_url": "https://..."
  }
  ```

### 2.12 `POST /v1/ocr/general` 输出协议

#### 2.12.1 Content-Type

- 响应默认是 `application/json`

#### 2.12.2 成功响应

- 顶层字段至少继续兼容：
  - `request_id`
  - `data`
- 典型响应仍应兼容：
  ```json
  {
    "request_id": "req_x",
    "data": {
      "words": ["hello", "world"]
    }
  }
  ```
- `data` 中继续保留通用 OCR 场景专属字段集合

#### 2.12.3 错误响应

- 失败时继续返回统一错误体，而不是伪造 OCR success body
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

### 2.13 `POST /v1/ocr/business-license` 输入协议

#### 2.13.1 认证方式

- 默认使用 `Authorization: Bearer ...`

#### 2.13.2 Content-Type

- 请求默认是 `application/json`

#### 2.13.3 请求体结构

- 顶层是 JSON object
- 通用核心字段：
  - `model`
  - `user`
- 图片输入三选一：
  - `image_base64`
  - `image_url`
  - `file_id`
- 允许携带平铺的扩展字段与嵌套 `extra_body`

#### 2.13.4 请求示例与语义

- 最小请求骨架仍应保持为：
  ```json
  {
    "model": "ocr_x",
    "image_url": "https://..."
  }
  ```

### 2.14 `POST /v1/ocr/business-license` 输出协议

#### 2.14.1 Content-Type

- 响应默认是 `application/json`

#### 2.14.2 成功响应

- 顶层字段至少继续兼容：
  - `request_id`
  - `data`
- `business-license` 继续返回营业执照场景专属字段集合

#### 2.14.3 错误响应

- 失败时继续返回统一错误体，而不是伪造 OCR success body
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

- **输入约束规则**
  - OCR 通用请求继续要求 `model` 非空
  - `image_base64`、`image_url`、`file_id` 继续保持“恰好三选一”的输入约束
- **路由规则**
  - 各 OCR 子 endpoint 继续作为独立能力点路由
  - 请求继续按 `endpoint + model` 选路
- **协议边界**
  - 同一套 `OcrRequest` 继续复用于多个 OCR 场景，但输出结构不强行统一
  - 每个证件 / 场景继续保留自己专属的返回字段集合
- **输出命名约束**
  - OCR 对外字段继续以 snake_case 为主
  - 典型字段如 `request_id`、`birth_date`、`issue_authority`、`idcard_number`、`file_id` 不应在重构中改成 camelCase
  - `idcard` / `hmt-residence-permit` 的 `side` 枚举值继续保持 `portrait` / `national_emblem`
- **扩展字段规则**
  - 未知请求字段继续被吸收到平铺 extra body 中，而不是在入口直接丢弃
- **治理侧约定**
  - 非 private 请求继续触发并发计数等治理副作用

## 4. 回归验证基线

- 各 OCR endpoint 的共享输入协议保持一致
- 各自专属输出字段集合保持不变
- snake_case 字段命名与 `side` 枚举保持一致
- 错误协议与治理副作用保持一致

## 5. 验证方式

- 回放三类输入来源、多个 OCR 子 endpoint、典型证件返回、错误请求等样例
- 对比 Java 与 Go 的字段命名、结构边界、错误响应与治理记录
