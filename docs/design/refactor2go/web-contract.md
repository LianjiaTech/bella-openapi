# Task 1 / Web 契约盘点

## 1. 范围说明

本文从总表中拆出 `Web` 能力点，覆盖 `/v1/web/*` 在 Go 重构前必须保持兼容的协议、不变量、回归基线与验证方式。

主索引文档：[`docs/design/refactor2go-task1-contract-inventory.md`](../refactor2go-task1-contract-inventory.md)

## 2. 协议

### 2.1 `POST /v1/web/search` 输入协议

#### 2.1.1 认证方式

- 默认使用 `Authorization: Bearer ...`

#### 2.1.2 Content-Type

- 请求默认是 `application/json`

#### 2.1.3 请求体结构

- 顶层是 JSON object
- 请求体核心字段：
  - `query`
  - `model`
  - `user`
  - `auto_parameters`
  - `topic`
  - `search_depth`
  - `chunks_per_source`
  - `max_results`
  - `time_range`
  - `days`
  - `start_date`
  - `end_date`
  - `include_raw_content`
  - `include_images`
  - `include_image_descriptions`
  - `include_favicon`
  - `include_domains`
  - `exclude_domains`
  - `country`
- 顶层字段层次当前至少继续兼容：
  - `query`
  - `model`
  - `user`
  - `auto_parameters`
  - `topic`
  - `search_depth`
  - `chunks_per_source`
  - `max_results`
  - `time_range`
  - `days`
  - `start_date`
  - `end_date`
  - `include_raw_content`
  - `include_images`
  - `include_image_descriptions`
  - `include_favicon`
  - `include_domains`
  - `exclude_domains`
  - `country`
- 未知字段当前继续会被吸收到平铺 `extra_body`

#### 2.1.4 请求示例与语义

- 最小请求骨架仍应兼容：
  ```json
  {
    "query": "who is leo messi",
    "model": "web-search-x"
  }
  ```
- `topic` 当前继续兼容：
  - `general`
  - `news`
  - `finance`
- `search_depth` 当前继续兼容：
  - `basic`
  - `advanced`

### 2.2 `POST /v1/web/search` 输出协议

#### 2.2.1 Content-Type

- 响应默认是 `application/json`

#### 2.2.2 成功响应

- 典型响应仍应兼容：
  ```json
  {
    "query": "who is leo messi",
    "answer": "...",
    "images": [{ "url": "https://..." }],
    "results": [
      { "title": "...", "url": "https://...", "content": "...", "score": 0.8 }
    ],
    "response_time": 1.2,
    "request_id": "req_x"
  }
  ```
- 顶层核心字段：
  - `query`
  - `answer`
  - `images`
  - `results`
  - `response_time`
  - `auto_parameters`
  - `request_id`
- `images[]` 单项核心字段：
  - `url`
  - `description`
- `results[]` 单项核心字段：
  - `title`
  - `url`
  - `content`
  - `score`
  - `raw_content`
  - `favicon`
- `auto_parameters` 当前至少继续兼容：
  - `topic`
  - `search_depth`
  - 以及自动推导出的其他扩展字段

#### 2.2.3 错误响应

- 失败时继续返回统一错误体，而不是伪造 search success body
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

### 2.3 `POST /v1/web/crawl` 输入协议

#### 2.3.1 认证方式

- 默认使用 `Authorization: Bearer ...`

#### 2.3.2 Content-Type

- 请求默认是 `application/json`

#### 2.3.3 请求体结构

- 顶层是 JSON object
- 请求体核心字段：
  - `url`
  - `model`
  - `user`
  - `instructions`
  - `max_depth`
  - `max_breadth`
  - `limit`
  - `select_paths`
  - `select_domains`
  - `exclude_paths`
  - `exclude_domains`
  - `allow_external`
  - `include_images`
  - `extract_depth`
  - `format`
  - `include_favicon`
- 顶层字段层次当前至少继续兼容：
  - `url`
  - `model`
  - `user`
  - `instructions`
  - `max_depth`
  - `max_breadth`
  - `limit`
  - `select_paths`
  - `select_domains`
  - `exclude_paths`
  - `exclude_domains`
  - `allow_external`
  - `include_images`
  - `extract_depth`
  - `format`
  - `include_favicon`
- 未知字段当前继续会被吸收到平铺 `extra_body`

#### 2.3.4 请求示例与语义

- 最小请求骨架仍应兼容：
  ```json
  {
    "url": "https://docs.example.com",
    "model": "web-crawl-x",
    "format": "markdown"
  }
  ```
- `extract_depth` 当前继续兼容：
  - `basic`
  - `advanced`
- `format` 当前继续兼容：
  - `markdown`
  - `text`

### 2.4 `POST /v1/web/crawl` 输出协议

#### 2.4.1 Content-Type

- 响应默认是 `application/json`

#### 2.4.2 成功响应

- 典型响应仍应兼容：
  ```json
  {
    "base_url": "https://docs.example.com",
    "results": [
      { "url": "https://docs.example.com/a", "raw_content": "..." }
    ],
    "response_time": 1.2,
    "request_id": "req_x"
  }
  ```
- 顶层核心字段：
  - `base_url`
  - `results`
  - `response_time`
  - `request_id`
- `results[]` 单项核心字段：
  - `url`
  - `raw_content`
  - `favicon`
- `format=markdown` 时继续返回 markdown 风格内容
- `format=text` 时继续返回纯文本内容

#### 2.4.3 错误响应

- 失败时继续返回统一错误体，而不是伪造 crawl success body
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

### 2.5 `POST /v1/web/extract` 输入协议

#### 2.5.1 认证方式

- 默认使用 `Authorization: Bearer ...`

#### 2.5.2 Content-Type

- 请求默认是 `application/json`

#### 2.5.3 请求体结构

- 顶层是 JSON object
- 请求体核心字段：
  - `urls`
  - `model`
  - `user`
  - `include_images`
  - `include_favicon`
  - `extract_depth`
  - `format`
  - `timeout`
- 顶层字段层次当前至少继续兼容：
  - `urls`
  - `model`
  - `user`
  - `include_images`
  - `include_favicon`
  - `extract_depth`
  - `format`
  - `timeout`
- 未知字段当前继续会被吸收到平铺 `extra_body`

#### 2.5.4 请求示例与语义

- 最小请求骨架仍应兼容：
  ```json
  {
    "urls": ["https://example.com/a"],
    "model": "web-extract-x",
    "format": "markdown"
  }
  ```
- `extract_depth` 当前继续兼容：
  - `basic`
  - `advanced`
- `format` 当前继续兼容：
  - `markdown`
  - `text`

### 2.6 `POST /v1/web/extract` 输出协议

#### 2.6.1 Content-Type

- 响应默认是 `application/json`

#### 2.6.2 成功响应

- 典型响应仍应兼容：
  ```json
  {
    "results": [
      { "url": "https://example.com/a", "raw_content": "..." }
    ],
    "failed_results": [
      { "url": "https://example.com/b", "reason": "timeout" }
    ],
    "response_time": 1.2,
    "request_id": "req_x"
  }
  ```
- 顶层核心字段：
  - `results`
  - `failed_results`
  - `response_time`
  - `request_id`
- `results[]` 单项核心字段：
  - `url`
  - `raw_content`
  - `images`
  - `favicon`
- `failed_results[]` 单项核心字段：
  - `url`
  - `reason`
  - `status_code`
- `format=markdown` 时继续返回 markdown 风格内容
- `format=text` 时继续返回纯文本内容

#### 2.6.3 错误响应

- 失败时继续返回统一错误体，而不是伪造 extract success body
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
  - `search`、`crawl`、`extract` 继续作为三个独立能力点路由
  - 三者都继续按 `endpoint + model` 选路
- **协议边界**
  - 三个接口继续保持不同的请求 / 响应结构，而不是被压成一个统一的 web 工具接口
  - `search` 继续以 `results + answer/images` 为主
  - `crawl` / `extract` 继续以 `raw_content` 结果集为主
- **内容格式规则**
  - `crawl` 与 `extract` 中 `format` 继续决定 `raw_content` 的表现形式
  - `search.include_raw_content=true` 时，`results[].raw_content` 继续可见
- **附加内容开关**
  - `include_images`、`include_image_descriptions`、`include_favicon` 继续影响响应中对应字段是否出现
  - `extract` 继续支持 `failed_results`
- **扩展字段规则**
  - 三类 web 请求中的未知字段继续被吸收到平铺 extra body 中，而不是在入口丢弃
- **usage / 计费口径**
  - web 三类能力的 usage 当前继续主要在日志 / 计费侧归一生成，而不是要求上游响应显式返回统一 usage 字段
  - `search` 当前继续至少按 `search_depth`、结果条数、是否返回图片形成 usage；其中 `search_depth` 优先取响应中的自动参数，其次取请求值，缺失时回落为 `basic`
  - `crawl` 当前继续至少按抓取结果条数、是否携带 `instructions`、`extract_depth` 形成 usage；`extract_depth` 缺失时回落为 `basic`
  - `extract` 当前继续至少按成功条数、失败条数、`extract_depth`、`include_images`、`include_favicon` 形成 usage；布尔开关继续按请求值解释
- **治理侧约定**
  - 非 private 请求继续触发并发计数等治理副作用

## 4. 回归验证基线

- `search`、`crawl`、`extract` 三种结构边界保持一致
- `format`、`include_*`、`failed_results`、`raw_content` 等语义保持一致
- extra body 吸收规则、usage 归一口径与治理副作用保持一致

## 5. 验证方式

- 回放 search/crawl/extract 的最小请求、带扩展字段请求、格式切换请求、错误请求
- 对比 Java 与 Go 的输出字段、内容形态、usage 与错误响应
