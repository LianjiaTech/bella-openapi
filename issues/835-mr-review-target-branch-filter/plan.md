# Execution Plan for #835

## 目标

限制 `.agentrix-rules:daemon-mr-review` 规则，使 MR Review 工作流仅在 MR 目标分支等于 GitLab 默认分支时才触发。

## 非目标

- 不修改其他 daemon rules（`daemon-mr-merged`、`daemon-issue-opened` 等）
- 不调整 `mr-review.yml` 工作流本身的逻辑
- 不修改任何业务代码
- 不更改默认分支配置

## 验收标准

1. 当 MR 目标分支是默认分支（`develop`）时，`mr-review` 正常触发
2. 当 MR 目标分支不是默认分支（如 `develop -> master`）时，`mr-review` 不触发
3. `mr-merged-summary.yml` 和其他 merge 后处理流程不受影响
4. GitLab CI YAML 配置语法有效（通过 CI lint 验证）

## 约束

- 仅修改 `.gitlab/templates/daemon-rules.yml` 中的 `.agentrix-rules:daemon-mr-review` 规则
- 使用 GitLab CI 内置变量 `$CI_DEFAULT_BRANCH` 和 Agentrix daemon 注入的 `$AGENTRIX_BASE_REF`
- 保持规则 YAML 格式与现有风格一致

## 变更范围

| 文件 | 变更类型 | 说明 |
|------|---------|------|
| `.gitlab/templates/daemon-rules.yml` | 修改 | 在 `.agentrix-rules:daemon-mr-review` 的 `rules.if` 条件中添加 `$AGENTRIX_BASE_REF == $CI_DEFAULT_BRANCH` |

## 实现思路

### Step 1: 修改规则条件

**文件**: `.gitlab/templates/daemon-rules.yml`，第 15-19 行

**当前内容**:
```yaml
.agentrix-rules:daemon-mr-review:
  rules:
    - if: '$AGENTRIX_TRIGGER_SOURCE == "agentrix_daemon_webhook" && $AGENTRIX_EVENT_NAME == "merge_request" && ($AGENTRIX_EVENT_ACTION == "opened" || $AGENTRIX_EVENT_ACTION == "reopened" || $AGENTRIX_EVENT_ACTION == "updated")'
      when: on_success
    - when: never
```

**修改为**:
```yaml
.agentrix-rules:daemon-mr-review:
  rules:
    - if: '$AGENTRIX_TRIGGER_SOURCE == "agentrix_daemon_webhook" && $AGENTRIX_EVENT_NAME == "merge_request" && $AGENTRIX_BASE_REF == $CI_DEFAULT_BRANCH && ($AGENTRIX_EVENT_ACTION == "opened" || $AGENTRIX_EVENT_ACTION == "reopened" || $AGENTRIX_EVENT_ACTION == "updated")'
      when: on_success
    - when: never
```

**变更说明**: 在 `$AGENTRIX_EVENT_NAME == "merge_request"` 之后、action 判断之前，插入 `$AGENTRIX_BASE_REF == $CI_DEFAULT_BRANCH` 条件。

### Step 2: 验证 YAML 语法

运行 GitLab CI lint 或本地 YAML 解析验证修改后的文件语法正确。

## 风险与依赖

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| `$AGENTRIX_BASE_REF` 变量未注入或为空 | MR Review 完全不触发 | 确认 daemon webhook 文档中该变量的注入时机；合并后观察一次正常 MR 是否触发 |
| `$CI_DEFAULT_BRANCH` 在 webhook 触发的 pipeline 中不可用 | 条件永远不匹配 | GitLab 官方文档确认该变量在所有 pipeline 类型中可用；若不确定可在测试 pipeline 中打印验证 |
| 条件字符串拼接过长导致可读性下降 | 维护困难 | 当前为单行 if，与仓库现有风格一致，暂不拆分 |

**依赖**: 无外部依赖，变更仅涉及 CI 规则配置。

## 验证方式

1. **语法验证**: 使用 GitLab CI lint API 或 `glab ci lint` 验证 YAML 合法性
2. **功能验证**:
   - 创建一个目标分支为 `develop`（默认分支）的测试 MR → 预期触发 mr-review
   - 创建一个目标分支为 `master`（非默认分支）的测试 MR → 预期不触发 mr-review
3. **回归验证**: 确认 `daemon-mr-merged`、`daemon-issue-opened` 等其他规则不受影响
