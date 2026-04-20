# Task 1 / Images 契约盘点

## 1. 范围说明

本文从总表中拆出 `Images` 能力点，覆盖 `/v1/images/*` 在 Go 重构前必须保持兼容的协议、不变量、回归基线与验证方式。

主索引文档：[`docs/design/refactor2go-task1-contract-inventory.md`](../refactor2go-task1-contract-inventory.md)

## 2. 协议

### 2.1 `POST /v1/images/generations` 输入协议

#### 2.1.1 认证方式

- 默认使用 `Authorization: Bearer ...`

#### 2.1.2 Content-Type

- 请求默认是 `application/json`

#### 2.1.3 请求体结构

- 顶层是 JSON object
- 顶层核心字段：
  - `prompt`
  - `model`
  - `background`
  - `moderation`
  - `n`
  - `output_compression`
  - `output_format`
  - `quality`
  - `response_format`
  - `size`
  - `style`
  - `user`
- 顶层字段层次当前至少继续兼容：
  - `prompt`
  - `background`
  - `model`
  - `moderation`
  - `n`
  - `output_compression`
  - `output_format`
  - `quality`
  - `response_format`
  - `size`
  - `style`
  - `user`
- 还允许携带扩展字段，如 `watermark`、`seed`、`guidance_scale` 以及其他 extra body 字段

#### 2.1.4 请求示例与语义

- 最小请求骨架仍应兼容：
  ```json
  {
    "prompt": "a cat",
    "model": "gpt-image-1",
    "size": "1024x1024",
    "response_format": "url"
  }
  ```
- `response_format` 当前继续决定返回 `url` 还是 `b64_json` 风格的图像结果

### 2.2 `POST /v1/images/generations` 输出协议

#### 2.2.1 Content-Type

- 响应默认是 `application/json`

#### 2.2.2 成功响应

- 继续返回统一的 images response JSON 结构，例如：
  ```json
  {
    "created": 1710000000,
    "data": [
      {
        "url": "https://...",
        "revised_prompt": "a cat sitting on a chair",
        "output_format": "png",
        "quality": "high",
        "size": "1024x1024"
      }
    ],
    "usage": {
      "num": 1,
      "size": "1024x1024",
      "quality": "high"
    }
  }
  ```
- 顶层核心字段：
  - `created`
  - `data`
  - `usage`
- `data[]` 元素核心字段：
  - `url`
  - `b64_json`
  - `revised_prompt`
  - `output_format`
  - `quality`
  - `size`
- `usage` 字段层次当前至少继续兼容：
  - `num`
  - `size`
  - `quality`
  - `input_tokens`
  - `input_tokens_details.image_tokens`
  - `input_tokens_details.text_tokens`
  - `output_tokens`
  - `total_tokens`
- `usage` 可包含：
  - `num`
  - `size`
  - `quality`
  - `input_tokens`
  - `input_tokens_details`
  - `output_tokens`
  - `total_tokens`

#### 2.2.3 错误响应

- 失败时继续返回统一错误体，而不是 images success body
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

### 2.3 `POST /v1/images/edits` 输入协议

#### 2.3.1 认证方式

- 默认使用 `Authorization: Bearer ...`

#### 2.3.2 Content-Type

- 请求是 `multipart/form-data`

#### 2.3.3 请求体结构

- multipart 核心字段：
  - `prompt`
  - `model`
  - `n`
  - `size`
  - `response_format`
  - `user`
- 图像输入支持三类：
  - 文件上传：`image` 或 `image[]`
  - URL：`image_url` 或 `image_url[]`
  - Base64：`image_b64_json` 或 `image_b64_json[]`
- 可选 mask 文件字段：`mask`
- multipart 字段层次当前至少继续兼容：
  - `image`：`MultipartFile[]`
  - `mask`：`MultipartFile`
  - `image_url`：`string[]`
  - `image_b64_json`：`string[]`
  - `prompt`
  - `model`
  - `n`
  - `size`
  - `response_format`
  - `user`
- 除已知字段外，其他 multipart 参数当前继续允许作为 extra body 输入

#### 2.3.4 请求示例与语义

- 最小请求形态仍应保持为 multipart 表单，而不是 JSON：
  - `prompt`
  - `model`
  - 至少一种图片输入
- 当请求中同时出现多种图像输入形式时，实际选择顺序继续遵循后文不变量中的既有优先级

### 2.4 `POST /v1/images/edits` 输出协议

#### 2.4.1 Content-Type

- 响应默认是 `application/json`

#### 2.4.2 成功响应

- 继续返回统一的 images response JSON 结构
- 顶层核心字段：
  - `created`
  - `data`
  - `usage`
- `data[]` 元素核心字段：
  - `url`
  - `b64_json`
  - `revised_prompt`
  - `output_format`
  - `quality`
  - `size`
- `usage` 可包含：
  - `num`
  - `size`
  - `quality`
  - `input_tokens`
  - `input_tokens_details`
  - `output_tokens`
  - `total_tokens`

#### 2.4.3 错误响应

- 失败时继续返回统一错误体，而不是 images success body
- 非 multipart 请求当前继续直接报参数错误
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

### 2.5 `POST /v1/images/variations` 输入协议

#### 2.5.1 认证方式

- 默认使用 `Authorization: Bearer ...`

#### 2.5.2 Content-Type

- 请求是 `multipart/form-data`

#### 2.5.3 请求体结构

- multipart 核心字段：
  - `image`
  - `model`
  - `n`
  - `response_format`
  - `size`
  - `user`
- multipart 字段层次当前至少继续兼容：
  - `image`：`MultipartFile`
  - `model`
  - `n`
  - `response_format`
  - `size`
  - `user`
- 当前继续只接受文件型 `image` 输入，而不是 URL / base64 变体

#### 2.5.4 请求示例与语义

- 最小请求形态仍应保持为 multipart 表单，且至少包含文件型 `image`

### 2.6 `POST /v1/images/variations` 输出协议

#### 2.6.1 Content-Type

- 响应默认是 `application/json`

#### 2.6.2 成功响应

- 继续返回统一的 images response JSON 结构
- 顶层核心字段：
  - `created`
  - `data`
  - `usage`
- `data[]` 元素核心字段：
  - `url`
  - `b64_json`
  - `revised_prompt`
  - `output_format`
  - `quality`
  - `size`
- `usage` 可包含：
  - `num`
  - `size`
  - `quality`
  - `input_tokens`
  - `input_tokens_details`
  - `output_tokens`
  - `total_tokens`

#### 2.6.3 错误响应

- 失败时继续返回统一错误体，而不是 images success body
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

- **路由规则**
  - `generations`、`edits`、`variations` 继续作为三个独立能力点路由
  - 三者都继续按 `endpoint + model` 选路
- **协议边界**
  - 对外继续表现为 OpenAI 风格 images 协议，而不是暴露底层私有图像协议
  - 无论底层适配到哪种 provider，出口都应保持统一 `ImagesResponse` 结构
- **Generations 请求展开规则**
  - `extra_body` 中的字段继续在请求发送时平铺到外层 JSON
  - `realExtraBody` 继续以 `extra_body` 字段形式附加到请求中
  - 若扩展字段与顶层字段同名，当前以展开后的字段覆盖原值
- **Edits 输入选择规则**
  - `/v1/images/edits` 当前优先级继续是：Base64、URL、文件
  - 当 provider 同时支持多种输入且请求里多种形式并存时，继续优先按 base64，再按 URL，最后按文件处理
  - 若 provider 不支持文件但支持 base64，文件上传继续在入口被转成 data URL 形式的 base64 再向上游发送
- **multipart 解析规则**
  - `image` 与 `image[]` 继续被视为同一组文件输入
  - `image_url` 与 `image_url[]` 继续被合并
  - `image_b64_json` 与 `image_b64_json[]` 继续被合并
  - edits 中除已知字段外的其他 multipart 参数，继续按基础类型推断后写入 extra body
- **Variations 输入规则**
  - `/v1/images/variations` 当前继续只接受文件型 `image` 输入，而不是 URL / base64 变体
- **usage 补齐规则**
  - 若响应自带 `usage`，继续沿用响应值
  - 若响应缺少 `usage`，当前日志侧继续至少补齐 `num`、`quality`、`size`
  - 当响应中缺少质量或尺寸时，当前默认回落为 `high` 与 `1024x1024`
- **治理侧约定**
  - 非 private 请求继续触发并发计数等治理副作用

## 4. 回归验证基线

- `generations`、`edits`、`variations` 三个子能力点的输入边界保持一致
- 输出仍统一为 `ImagesResponse` 结构
- 多来源图片输入优先级、multipart 合并规则、usage 补齐规则保持一致
- 错误协议与治理副作用保持一致

## 5. 验证方式

- 回放 JSON generations、multipart edits、multipart variations、非 multipart edits、缺失 usage 等样例
- 对比 Java 与 Go 的字段结构、输入优先级选择、usage 与错误响应
