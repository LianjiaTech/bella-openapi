# Execution Plan for #840: 调整 GitLab Agentrix 工作流触发策略

## 目标

1. 禁用 `mr-review` 自动触发：MR 创建/更新/重新打开时不再自动执行代码审查
2. 禁用 `mr-merged-summary` 自动触发：MR 合并后不再自动生成摘要
3. 保留评论命令手动触发 review 的能力：用户在 MR 评论中通过 `@agentrix` 或 `/agentrix` 要求 review 时，`comment-command` job 能执行代码审查

## 非目标

- 不删除或重构现有 workflow 文件结构
- 不修改 `daemon-rules.yml` 中的共享规则模板
- 不修改 `.gitlab-ci.yml` 的 include 策略
- 不涉及其他 workflow（issue-auto-label、issue-plan-generator、plan-mr-merged-handler、sync-to-github）
- 不修改 `agentrix.yml` 模板

## 验收标准

1. MR 创建、更新、重新打开时，`mr-review` job 不再自动触发
2. MR 合并后，`mr-merged-summary` job 不再自动触发
3. 两个 workflow 文件仍保留原有 prompt 和 job 配置，后续可通过删除 `rules: - when: never` 恢复
4. 用户在 MR 评论中通过 `@agentrix` 或 `/agentrix` 明确要求 review 代码时，`comment-command` 能参考 `mr-review.yml` 的 prompt 执行代码审查
5. 手动 review 使用 `git fetch origin ${AGENTRIX_BASE_REF} && git diff origin/${AGENTRIX_BASE_REF}...HEAD` 获取真实增量 diff
6. 增量 diff 为空时不发布评论
7. 评论格式为中文，包含 `## Code Review` 及 `🔴 Must Fix` / `🟡 Suggestion` / `🟢 Praise` 分组，每条附文件路径与行号，末尾一句总体评价

## 约束

- 仅修改 `.gitlab/workflow/` 下的三个文件，不涉及其他目录
- `mr-review.yml` 和 `mr-merged-summary.yml` 的原有配置（prompt、variables、extends 等）必须保留，仅添加 `rules` 覆盖
- `comment-command.yml` 中的 review 逻辑必须与 `mr-review.yml` 中的 prompt 保持一致的审查标准和输出格式
- diff 获取方式必须使用三点 diff（`origin/${AGENTRIX_BASE_REF}...HEAD`），禁止使用 `glab mr diff`

## 变更范围

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `.gitlab/workflow/mr-review.yml` | 修改 | 添加 `rules: - when: never` 覆盖继承规则 |
| `.gitlab/workflow/mr-merged-summary.yml` | 修改 | 添加 `rules: - when: never` 覆盖继承规则 |
| `.gitlab/workflow/comment-command.yml` | 修改 | 在 prompt 中补充 MR review 的处理逻辑 |

## 实现思路

### Step 1: 禁用 mr-review 自动触发

**文件**: `.gitlab/workflow/mr-review.yml`

**操作**: 在 `mr-review` job 定义中，在 `extends` 之后、其他字段之前，添加 `rules` 字段覆盖从 `.agentrix-rules:daemon-mr-review` 继承来的规则：

```yaml
mr-review:
  stage: agentrix_execution
  extends:
    - .agentrix-run
    - .agentrix-rules:daemon-mr-review
  rules:
    - when: never
  retry:
    ...
```

**验证**: 推送后，创建一个测试 MR，确认 `mr-review` job 不出现在 pipeline 中。

### Step 2: 禁用 mr-merged-summary 自动触发

**文件**: `.gitlab/workflow/mr-merged-summary.yml`

**操作**: 在 `mr-merged-summary` job 定义中，添加 `rules` 字段覆盖继承规则：

```yaml
mr-merged-summary:
  stage: agentrix_post_merge
  extends:
    - .agentrix-run
    - .agentrix-rules:daemon-mr-merged
  rules:
    - when: never
  variables:
    ...
```

**验证**: 合并一个测试 MR，确认 `mr-merged-summary` job 不出现在 pipeline 中。

### Step 3: 在 comment-command 中补充 MR review 逻辑

**文件**: `.gitlab/workflow/comment-command.yml`

**操作**: 在 `AGENTRIX_PROMPT` 中的任务目标第 3 条（"如果目标是 MR，围绕该 MR 执行"）之后，补充 review 处理逻辑。具体在 prompt 中添加以下说明：

```
3. 如果目标是 MR，围绕该 MR 执行。
   - 如果根据评论上下文判断用户希望 review 代码（如"review"、"审查"、"看看代码"等），执行以下代码审查流程：
     a. 使用 `git fetch origin ${AGENTRIX_BASE_REF} && git diff origin/${AGENTRIX_BASE_REF}...HEAD` 获取增量 diff。禁止使用 `glab mr diff` 或其他方式获取 diff，因为它们可能包含 rebase 引入的非本 MR 改动。
     b. 如果增量 diff 为空（即纯 rebase 或同步 target branch，MR 无自身实质改动），直接结束，不发布任何评论。
     c. 基于增量 diff 审查代码，优先关注：
        - 敏感信息与资源泄露：密钥、token、密码、内部 URL 等是否被意外提交
        - 误改内容：与关联 Issue 无关的改动、意外删除或覆盖的代码、逻辑回退，以及实现是否符合 Issue 背景与需求
        - 安全性：注入、越权、未校验输入等安全风险
        其次关注正确性、性能、可维护性。若为 plan 文档 MR（branch 含 `/plan` 或标题以 `docs(plan):` 开头），只审查计划内容的完整性。
     d. 用 `glab mr note` 发布评论，使用中文，格式：
        ## Code Review
        ### 🔴 Must Fix
        ### 🟡 Suggestion
        ### 🟢 Praise
        每条附文件路径与行号，末尾一句总体评价。
```

**验证**: 在测试 MR 中评论 `@agentrix review`，确认 comment-command job 被触发并执行代码审查，输出格式符合要求。

## 风险与依赖

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| GitLab CI `rules` 覆盖行为不符合预期 | job 仍被触发或其他 job 受影响 | GitLab 文档明确：job 级 `rules` 会完全覆盖 `extends` 继承的 `rules`，不会叠加 |
| comment-command prompt 过长导致 agent 理解偏差 | review 输出质量不稳定 | prompt 中 review 逻辑独立成段，与其他逻辑清晰分离；审查标准直接复用 mr-review.yml 已验证的 prompt |
| `AGENTRIX_BASE_REF` 变量在 comment-command 上下文中不可用 | diff 命令失败 | 需确认 daemon-note-command 触发时是否注入 MR 相关变量；若不可用，需通过 `glab mr view` 获取 target branch |

### 关键依赖确认

- `comment-command` job 由 `.agentrix-rules:daemon-note-command` 触发，条件为 `$AGENTRIX_EVENT_NAME == "note"`，不依赖 MR 相关条件，因此 MR 评论也会触发
- `AGENTRIX_PR_NUMBER` 已在 comment-command 的变量中列出，说明 MR 上下文变量可用
- 但 `AGENTRIX_BASE_REF` 需要在实现时确认是否在 note 事件中注入。如果不可用，需要在 prompt 中指导 agent 通过 `glab mr view ${AGENTRIX_PR_NUMBER}` 获取 target branch 名称

## 验证方式

1. **静态验证**: 检查修改后的 YAML 语法正确性（`yamllint` 或 GitLab CI lint）
2. **mr-review 禁用验证**: 创建测试 MR，确认 pipeline 中无 `mr-review` job
3. **mr-merged-summary 禁用验证**: 合并测试 MR，确认 pipeline 中无 `mr-merged-summary` job
4. **手动 review 验证**: 在测试 MR 中评论 `@agentrix review this MR`，确认：
   - comment-command job 被触发
   - agent 使用 `git diff origin/${AGENTRIX_BASE_REF}...HEAD` 获取 diff
   - 评论以中文发布，格式包含 `## Code Review` 及三个分组
   - 每条问题附文件路径与行号
5. **空 diff 验证**: 在仅含 rebase 的 MR 中请求 review，确认不发布评论
6. **回退验证**: 确认删除添加的 `rules: - when: never` 后，原有自动触发行为恢复
