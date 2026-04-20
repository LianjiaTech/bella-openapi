# Task 1 / 错误归一

## 1. 范围说明

本文从横向公共语义中拆出错误归一规则，聚焦同步、SSE、WebSocket 以及管理面接口对外错误表现的一致性要求。

主索引文档：[`docs/design/refactor2go/task1-common-semantics.md`](./task1-common-semantics.md)

## 2. 不能改变的行为

- **两套统一错误出口**
  - `@EndpointAPI` 继续保持“成功返回原协议结构，失败返回统一 `error` 结构”的语义；不能把所有 runtime 成功响应都包成管理面风格的统一 envelope。
  - `@BellaAPI` 继续保持 `BellaResponse` 风格的统一包装，包含 `code`、`timestamp`、`data / message` 等字段；500 类错误当前还会带 `stacktrace`。
- **错误协议形状**
  - `@EndpointAPI` 的同步 HTTP 错误继续使用下面这类结构：
  ```json
  {
    "error": {
      "code": "400",
      "httpCode": 400,
      "type": "Illegal Argument",
      "message": "model is required",
      "param": null,
      "sensitive": null
    },
    "sensitives": null,
    "requestRiskData": null
  }
  ```
  - 安全检查失败时，继续允许出现下面这类变体：
  ```json
  {
    "error": {
      "code": "400",
      "httpCode": 400,
      "type": "safety_check",
      "message": "safety_check_no_pass",
      "sensitive": { "...": "..." }
    },
    "sensitives": { "...": "..." }
  }
  ```
  - `@BellaAPI` 的错误继续使用管理面 envelope，而不是 runtime 的 `error` 对象：
  ```json
  {
    "code": 400,
    "message": "xxx",
    "timestamp": 1710000000000,
    "data": null,
    "stacktrace": null
  }
  ```
- **`@EndpointAPI` 的同步错误协议**
  - 当 runtime 接口在进入流式输出之前失败时，外部继续收到普通 HTTP 响应，body 中以 `error` 对象表达错误，而不是返回某个能力点自己的成功 schema。
  - `error` 结构至少要继续稳定包含 `message`、`type`、`httpCode`；多数错误同时还会带 `code`。
  - 安全检查失败时，`error` 中继续允许带 `sensitive`，响应顶层也可能继续带 `sensitives`，这属于对外可观察字段。
  - HTTP 状态码继续与错误体表达保持一致，不能出现 body 是错误但 HTTP 仍返回 200 的退化行为。
- **错误分类的外部表现**
  - 参数校验、请求体解析、非法参数等错误继续表现为 `400`。
  - 鉴权缺失、无权限等错误继续表现为 `401`。
  - 限流错误继续表现为 `429`。
  - 上游通道异常、I/O 异常、内部异常继续通过统一异常转换逻辑暴露为对应的 `httpCode + type + message`，而不是被粗暴抹平成单一文案。
- **流式错误与同步错误的边界**
  - 在 SSE / WebSocket 建立之前发生的错误，继续走 HTTP body。
  - 一旦流已经建立，错误继续通过流内事件或 WebSocket 文本帧表达，不能再尝试回退为普通 HTTP JSON 错误体。
- **SSE / WebSocket 中的错误表达**
  - `chat/completions` 流继续把错误作为一个带 `error` 字段的数据帧发送，然后结束流；正常结束仍保持 `[DONE]` 终止语义。
  - `messages` 流继续把错误转换为 `type=error` 的 message event；它不是 chat 的 `[DONE]`，也不是 responses 的 `response.error`。
  - `responses` 流继续通过命名事件 `response.error` 表达失败，然后结束 SSE。
  - realtime WebSocket 在连接建立后继续通过协议内 error message 表达失败，其中既保留协议内 status code，也保留 4xx/5xx 级别的错误语义。
- **日志与可追踪性**
  - 无论错误发生在同步、SSE 还是 WebSocket 阶段，请求级 `requestId` 继续作为统一关联键进入日志与治理链路。
