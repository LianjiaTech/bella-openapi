# Plan: #792 修复生图 Playground 模型参数能力不兼容

## 目标

修复 `web_v2` 生图 Playground 在不同生图模型之间复用固定参数 UI 和请求构造导致的兼容性问题。完成后，`images/generations` 和 `images/edits` 页面都由 `Model.properties` 中的结构化 image 参数能力配置驱动 `size`、`quality`、`style` 的展示、默认值和请求传参，确保 GPT/OpenAI 系列与豆包 seedream 系列能分别使用符合自身能力和尺寸约束的参数选项。

## 非目标

- 不改后端 `/v1/images/generations` 或 `/v1/images/edits` 的协议转发链路。
- 不改 channel 结构，不在 channel 层新增参数能力配置。
- 不使用模型名或 provider 名在前端硬编码规则。
- 不把结构化参数枚举放到 `features`；`features` 仍仅作为能力标签展示。
- 不在本次代码里批量写入线上模型元数据，只定义并消费 `Model.properties` 结构。

## 验收标准

- `Model.properties.image.generations.parameters` 和 `Model.properties.image.edits.parameters` 可分别配置 `size`、`quality`、`style` 的 `enabled/default/options`。
- `options` 同时支持字符串数组和 `{ value, label }` 对象数组。
- generations 页面只展示并传递当前模型 `enabled` 的参数；不支持或未配置的参数不会进入 JSON body。
- edits 页面只展示并传递当前模型 `enabled` 的参数；不支持或未配置的参数不会 append 到 `FormData`。
- 切换模型时，旧模型遗留的 `size/quality/style` 不会继续传给新模型；prompt、上传图片和历史输入不被清空。
- 豆包 seedream 系列可通过配置展示符合其尺寸约束的选项，不再沿用固定 GPT 尺寸枚举；seedream 4 可通过配置传 `size: "2K"`。
- `cd web_v2 && npm run lint` 通过，或记录明确的既有 lint 阻塞。

## 约束

- 基于 `develop` 开发，当前 Issue Branch 为 `issue/792/image-playground-params/develop`。
- 目标 MR 目标分支为 `develop`；如果后续变成 stacked MR，必须重新确认 base branch。
- 前端参数能力以 `Model.properties` 为准；缺失、解析失败或 `enabled !== true` 时默认不展示、不传参。
- 不引入大规模后端改造。

## 变更范围

- `web_v2/src/lib/types/openapi.ts`
  - 扩展 `ModelProperties`，新增 image 参数能力相关类型。
  - 保持 `Model.properties: string` 不变。
- `web_v2/src/lib/utils/image.ts`
  - 新增 `parseModelProperties`、`parseImagePlaygroundConfig` 和参数 option 归一化逻辑。
- `web_v2/src/app/[locale]/(dashboard)/playground/images/generations/page.tsx`
  - 改为按 `selectedModel.properties.image.generations` 渲染和传递参数。
- `web_v2/src/app/[locale]/(dashboard)/playground/images/edits/page.tsx`
  - 改为按 `selectedModel.properties.image.edits` 渲染和传递参数。

## 实现思路

1. 扩展前端类型定义
   - 在 `openapi.ts` 中新增 `ImageEndpointProperties`、`ImageParameterProperties`、`ImageParameterOption` 等类型。
   - `ModelProperties.image` 下区分 `generations` 和 `edits`，每个 endpoint 下都有 `parameters.size/quality/style`。

2. 新增 properties 解析工具
   - 在 `image.ts` 中新增安全 JSON 解析函数，空值或非法 JSON 返回空对象。
   - 新增 `parseImagePlaygroundConfig(raw, endpoint)`，读取 `properties.image[endpoint].parameters`。
   - 归一化参数配置为 `{ enabled, defaultValue, options }`，其中 `options` 输出统一为 `{ value, label }[]`。
   - 新增合法默认值选择逻辑：当前值合法则保留，否则使用 default，否则使用第一个 option；不支持时清空。

3. 改造 generations 页面
   - 用 `parseImagePlaygroundConfig(selectedModel?.properties, "generations")` 生成配置。
   - size 下拉从固定选项改为 properties options。
   - quality/style 展示不再依赖 `features`，改由 properties 决定；`features` 继续用于模型信息 badge。
   - 构造请求 body 时只加入 enabled 且有值的 `size/quality/style`。
   - 删除无用的 `protocol/host` 和调试日志。

4. 改造 edits 页面
   - 用 `parseImagePlaygroundConfig(selectedModel?.properties, "edits")` 生成配置。
   - size 下拉从固定选项改为 properties options。
   - quality/style 从非受控 `defaultValue` Select 改为受控 state。
   - 构造 FormData 时只 append enabled 且有值的 `size/quality/style`。
   - 模型切换时只同步参数 state，不清空图片、prompt、url 输入等业务输入。

5. 配套模型元数据要求
   - 代码合入后，需要通过现有模型元数据配置流程为重点模型补齐 `properties.image`。
   - GPT/OpenAI 系列配置自身支持的 size/quality/style。
   - 豆包 seedream 系列配置符合其最大/最小尺寸限制和可用取值范围的 size options；seedream 4 可配置 `2K`。

## 风险与依赖

- 如果现有模型没有补齐 `properties.image`，前端会按安全策略不展示、不传参；需要配套模型元数据配置。
- 如果某个模型配置的 default 不在 options 内，前端会回落到第一个合法 option；需要在配置侧保持一致。
- 需要浏览器验证请求 payload，尤其是从 GPT 切到豆包、再切回 GPT 的参数状态重置行为。