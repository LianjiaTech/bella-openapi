# Task 1 / 请求上下文与特殊 Header

## 1. 范围说明

本文从横向公共语义中拆出请求上下文与特殊 Header 规则，聚焦所有能力点共享的 header 收集、透传与解释语义。

主索引文档：[`docs/design/refactor2go/task1-common-semantics.md`](./task1-common-semantics.md)

## 2. 不能改变的行为

- **公共上下文收集规则**
  - 入口 filter 继续统一收集所有 `X-BELLA-*` header，并以大写 key 形式写入请求级上下文。
  - 这些 header 不是只给某一个 controller 使用，而是会继续作为公共上下文被路由、日志、限流、安全检查、mock、direct mode 和协议适配层复用。
  - 因此 Go 重构后不能只保留少数显式字段，也不能把未知 `X-BELLA-*` header 直接丢弃。
- **链路标识 Header**
  - `X-BELLA-TRACE-ID` 在调用方传入时继续原样透传；未传入时继续由服务端生成 `<serviceId>-<uuid>` 形式的新值。
  - `X-BELLA-TRACE-ID` 继续回写到响应头，并进入请求日志上下文，作为跨服务链路追踪标识。
  - `X-BELLA-REQUEST-ID` 继续由服务端为每个请求生成，不依赖调用方传入值；它同样会回写到响应头，并进入请求上下文与日志。
- **模式切换 Header**
  - `X-BELLA-DIRECT=true` 继续显式开启 direct mode；仅传 `X-BELLA-MODEL` 但未开启 direct mode，不应自动触发直连语义。
  - direct mode 下，请求实际参与路由的模型继续优先取 `X-BELLA-MODEL`，而不是 body 中的 `model` 字段。
  - `X-BELLA-MOCK-REQUEST=true` 继续把请求标记为 mock 请求，并影响后续选路、mock adaptor、日志和治理逻辑。
- **治理与路由相关 Header**
  - `X-BELLA-MAX-WAIT` 继续作为请求级等待时间输入，只接受纯数字秒数；非法值继续忽略，而不是报错。
  - `X-BELLA-CHANNEL` 继续作为显式路由渠道输入，但只在支持该能力点的协议中生效，例如 `responses` 的 create / get。
  - `X-BELLA-USER-AK-CODE` 虽然属于鉴权语义的一部分，但它同样继续通过公共 header 上下文传递，不能在 filter 之后丢失。
- **扩展 Header 兼容规则**
  - 当前已经存在若干由具体能力点解释的扩展 header，例如 `X-BELLA-FUNCTION-SIMULATE`、`X-BELLA-MERGE-REASONING`、`X-BELLA-SPLIT-REASONING`、`X-BELLA-MOCK-*`。
  - 这些 header 的公共约束是“由统一入口收集、按原值透传、由下游能力点按需解释”，而不是在入口层做严格白名单裁剪。
