# Task 1 / 路由与模式语义

## 1. 范围说明

本文从横向公共语义中拆出路由、Direct、Mock、Private 等模式语义，聚焦跨能力点共享的选路与渠道过滤约束。

主索引文档：[`docs/design/refactor2go/task1-common-semantics.md`](./task1-common-semantics.md)

## 2. 不能改变的行为

- **路由输入**
  - 常规路由继续以 `endpoint`、`model`、`apikeyInfo`、`isMock` 为主输入；其中 `model` 非空时优先按模型找渠道，`model` 为空时退回按 endpoint 找渠道。
  - 当按模型路由时，继续先把调用方传入模型名映射到 terminal model，再基于 terminal model 查询 active channels。
  - direct mode 不是另一套路由器，而是在同一套路由逻辑上额外传入 `isDirectMode=true`，只改变后续过滤行为。
  - 某些协议还继续允许显式指定 channelCode 直达已有渠道；这类路径绕过常规模型/endpoint 选路，但不能绕过渠道存在性校验。
- **基础选路规则**
  - 渠道集合取回后，继续先按 adaptor 是否支持当前 endpoint 对应 protocol 过滤；不支持当前 endpoint 的协议，即使渠道 active 也不能入选。
  - 当过滤后为空时，继续报“没有支持当前 endpoint 的可用渠道”，而不是静默降级到其他协议。
  - 候选集不为空时，继续先做权限/合规/可用性过滤，再做优先级选择，而不是反过来先随机命中再校验。
- **权限 / 合规过滤**
  - private channel 继续只对 `ownerType + ownerCode` 与当前 ak 完全匹配的调用方可见；这是一条访问资格约束，不是排序偏好。
  - `dataDestination` 继续受 ak 的 `safetyLevel` 约束；不满足合规级别的渠道必须在路由阶段被剔除，而不是命中后再报错。
  - 当正常合规渠道全部不可用，且 ak 处于最低 safety level 时，当前继续允许退化到 `trialEnabled=1` 的非 private 渠道。
  - 这种 trial fallback 只在最低 safety level 场景生效，不能误扩散到其他 safety level 或普通无权限场景。
- **可用性与优先级规则**
  - 常规模式下，继续基于渠道可用性状态过滤 unavailable 渠道；但 `protected` / `inner` 目的地渠道继续不受这层 unavailable 过滤影响。
  - direct mode 下继续跳过 availability check，不查 Redis 可用性状态；因此 direct mode 的语义是“跳过运行时可用性治理”，不是“跳过所有权限与合规过滤”。
  - 候选集确定后，继续先按 `visibility` 与 `priority` 取最高优先级集合；当 private 与 public 同时存在时，private 继续优先于 public。
  - 在同一最高优先级集合内，继续随机选一个渠道，而不是固定选择第一个。
- **模式语义**
  - direct mode 继续表现为“尽量透明地直连上游渠道”：仍然要完成选路和协议适配，但跳过可用性检查，且在 chat 直连路径下额外跳过本地限流与安全检查。
  - mock mode 继续表现为“正常完成入口校验后，路由结果替换为 `ch-mock` / `MockAdaptor`”；当存在原始命中渠道时，原渠道的 `channelInfo` 继续被复用到 mock channel 中。
  - private mode 不是显式请求参数，而是命中的渠道属性；一旦命中 private channel，后续并发计数等治理逻辑继续按 private 请求分支处理。
- **route / channel 选择对外可见的影响**
  - 选中的 channel 继续决定 `protocol`、`forwardUrl`、`channelInfo`、`priceInfo`、`supplier`，这些字段会进一步决定 adaptor 类型、序列化协议、streaming 行为和成本归集。
  - 因此 Go 重构时，只要最终命中的渠道不同，就可能改变外部行为；路由结果本身就是正式契约的一部分，而不只是内部实现。
