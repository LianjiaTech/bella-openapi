# Task 1 / 鉴权与入口约束

## 1. 范围说明

本文从横向公共语义中拆出鉴权与入口约束，聚焦所有能力点共享的认证入口、身份切换与请求上下文初始化规则。

主索引文档：[`docs/design/refactor2go/task1-common-semantics.md`](./task1-common-semantics.md)

## 2. 不能改变的行为

- **Runtime 接口的主鉴权规则**
  - 绝大多数 runtime 能力点继续以 `Authorization` 为主认证入口。
  - 当 `Authorization` 采用 `Bearer <apikey>` 形式时，当前继续按 apikey 校验、装载 `ApikeyInfo`，并据此做权限判定。
  - 若请求缺失可用认证信息，当前继续直接报鉴权错误，而不是匿名降级访问。
- **Gemini 兼容鉴权规则**
  - Gemini 兼容入口当前继续允许在缺少 `Authorization` 时回退读取 `x-goog-api-key`。
  - 这种 fallback 只对 Gemini 路径生效，不能误扩散到其他 endpoint。
- **Delegated Auth 规则**
  - 当当前 ak 具备 allocated permission 时，`X-BELLA-USER-AK-CODE` 继续允许把执行身份切换到目标用户 AK。
  - 切换后实际写入 `EndpointContext` 的 ak 信息继续以目标用户 AK 为准，而不是原始委托 AK。
  - 这是一种“执行主体替换”语义，不只是附带一个审计字段。
- **Console / 管理面登录态规则**
  - 管理类接口与 console 接口继续优先走登录态 `Operator` 路径，而不是普通 runtime 的 Bearer AK 路径。
  - 当存在 `Operator` 时，当前会继续从其 `managerAk` 派生可用的管理 AK，并把角色、权限包含集、排除集等信息写回 `optionalInfo`。
  - console / BellaAPI 能力与 runtime / EndpointAPI 能力继续保持两套入口语义：前者偏“登录态 + 管理权限”，后者偏“AK 调用 + endpoint 权限”。
- **请求上下文初始化规则**
  - 入口 filter 继续统一收集所有 `X-BELLA-*` header，生成或透传 `X-BELLA-TRACE-ID`，并为每个请求生成 `X-BELLA-REQUEST-ID`。
  - 这些上下文字段继续在请求生命周期内进入 `BellaContext / EndpointContext`，供路由、日志、限流、治理链路复用。
