# Plan: #746 修复新增渠道时空协议配置参数传递

## 目标

修复新增渠道时当协议配置信息为空时仍向后端传入 `channelInfo` 参数的问题，确保创建渠道请求仅在协议配置存在有效内容时才携带该字段，避免无意义的空参数进入后端请求载荷。

## 非目标

- 不调整编辑渠道时 `channelInfo` 的更新逻辑
- 不修改后端 `MetadataValidator` 的必填校验或服务端创建逻辑
- 不改动价格信息、队列配置、协议列表或其他表单字段的提交流程
- 不处理旧版 `web/` 渠道创建页面，除非后续确认该入口仍在使用

## 验收标准

- 在 `web_v2` 新增渠道时，如果协议配置为空，提交请求体中不包含 `channelInfo` 字段
- 在 `web_v2` 新增渠道时，如果协议配置填写了有效 JSON，提交请求体仍正常包含 `channelInfo`
- 编辑渠道流程不因本次改动出现字段丢失或提交异常
- 相关代码通过前端静态检查或最小范围自测

## 约束

- 本次仅做前端请求裁剪，不涉及后端改动；若后端对空配置仍报错，另行通过后续 issue 处理
- 当前私有渠道新增提交流程走 `web_v2` 的 `createPrivateChannel` 接口，见 `web_v2/src/app/[locale]/(dashboard)/metadata/components/channelConfigDialog/hooks/useChannelSubmit.ts:41`
- 渠道创建请求结构由 `MetaDataOps.ChannelCreateOp` 定义，`channelInfo` 仍是合法字段，见 `api/sdk/src/main/java/com/ke/bella/openapi/metadata/MetaDataOps.java:126`
- 需要保持 issue 目录和分支命名一致：`issues/746-fix-empty-protocol-config/`，`issue/746/fix-empty-protocol-config/main`

## 变更范围

- `web_v2/src/app/[locale]/(dashboard)/metadata/components/channelConfigDialog/utils/formHelpers.ts`
- 视实现方式，可能补充：
  - `web_v2/src/app/[locale]/(dashboard)/metadata/components/channelConfigDialog/hooks/useChannelSubmit.ts`
  - `web_v2/src/lib/types/metadata.ts`（仅当类型约束需要允许省略字段时）

## 实现思路

1. 调整新增渠道请求体构造逻辑  
   - **目标**：让创建模式下的 `channelInfo` 在为空字符串、空白字符或等价空值时不进入请求体  
   - **涉及文件**：`web_v2/src/app/[locale]/(dashboard)/metadata/components/channelConfigDialog/utils/formHelpers.ts`  
   - **具体改动**：在 `buildSubmitData` 的 create 分支中，对 `formData.channelInfo` 做归一化处理；若为空则返回 `undefined` 或直接省略该属性，而不是固定透传空字符串

2. 校验提交链路对可选字段的兼容性  
   - **目标**：确保 `createPrivateChannel` 调用不会因 `channelInfo` 缺失而在前端类型或序列化阶段报错  
   - **涉及文件**：`web_v2/src/app/[locale]/(dashboard)/metadata/components/channelConfigDialog/hooks/useChannelSubmit.ts`，必要时 `web_v2/src/lib/api/metadata.ts` 或类型定义文件  
   - **具体改动**：确认提交数据对象允许可选字段透传，并避免二次组装时把 `undefined` 转回空字符串

3. 回归检查新增/编辑两条路径  
   - **目标**：确保本次只影响“新增渠道”，不误伤“编辑渠道”  
   - **涉及文件**：`web_v2/src/app/[locale]/(dashboard)/metadata/components/channelConfigDialog/utils/formHelpers.ts` 及调用方  
   - **具体改动**：核对 edit 分支仍按现有逻辑处理 `channelInfo`；如需要，在构造函数内按 mode 区分 create/edit 的空值策略

4. 进行最小化验证  
   - **目标**：验证请求载荷与表单行为符合预期  
   - **涉及文件**：无新增业务文件  
   - **具体改动**：本地打开新增渠道弹窗，分别测试空协议配置和有协议配置两种情况，检查请求体；补充执行前端 lint 或定向校验

## 风险与依赖

- 风险：后端当前对模型渠道创建仍存在 `channelInfo` 必填约束，因此本次只保证前端不发送空字段，不承诺后端一定接受完全缺失的字段
- 风险：`channelInfo` 字段内容是 JSON 字符串，需明确空字符串与 `{}` 是否同属“空配置”；本次默认仅裁剪空字符串/空白内容
- 依赖：当前定位到主入口是 `web_v2`；若线上仍使用 `web/` 旧页面，需要后续另行同步评估
