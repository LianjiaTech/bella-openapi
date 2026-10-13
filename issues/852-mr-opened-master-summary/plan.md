# Execution Plan for #852: 新增 master 目标分支 MR 打开时的自动变更总结工作流

## 目标

1. 新增 daemon 共享规则 `.agentrix-rules:daemon-mr-opened-master`，匹配 `merge_request opened` 且目标分支为 `master` 的事件
2. 新增工作流文件 `.gitlab/workflow/mr-opened-master-summary.yml`，触发时自动分析 MR 增量 diff
3. 工作流能根据 diff 自动更新 MR 标题，并在 MR 下发布中文总结评论

## 非目标

- 不修改现有工作流文件（mr-review、mr-merged-summary、comment-command 等）
- 不修改 `.gitlab/templates/agentrix.yml` 模板
- 不修改任何业务代码
- 不处理非 `opened` 动作（如 `updated`、`reopened`）
- 不处理目标分支非 `master` 的情况
- 不合并 MR 或推送代码到源分支

## 验收标准

1. 当 Agentrix daemon 收到 `merge_request opened` 且目标分支为 `master` 的事件时，触发 `mr-opened-master-summary` job
2. 当 MR 目标分支不是 `master` 时，不触发该 job
3. 当事件动作不是 `opened` 时，不触发该 job
4. job 使用 `git diff origin/master...HEAD` 分析 MR 增量改动
5. job 根据改动使用 `glab mr update` 更新 MR 标题
6. job 在 MR 下使用 `glab mr note` 发布符合格式要求的中文 "Master 合并摘要" 评论
7. 更新标题失败时，在评论中说明失败原因
8. 新增 YAML 文件语法正确，可被 GitLab CI 正常解析

## 约束

- 工作流只读分析，不得修改仓库文件、创建 commit、push 分支或合并 MR
- diff 范围限定为 MR 增量（`origin/${AGENTRIX_BASE_REF}...HEAD`），不扩大到非 MR 改动
- 标题生成遵循项目 conventional commit 风格，避免泛泛标题
- 评论格式严格遵循 Issue 中定义的模板

## 变更范围

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `.gitlab/templates/daemon-rules.yml` | 修改 | 新增 `.agentrix-rules:daemon-mr-opened-master` 规则 |
| `.gitlab/workflow/mr-opened-master-summary.yml` | 新增 | 新增 MR master summary 工作流 job |

## 实现思路

### Step 1: 新增 daemon 共享规则

**文件**: `.gitlab/templates/daemon-rules.yml`

**操作**: 在文件末尾追加新的规则模板：

```yaml
.agentrix-rules:daemon-mr-opened-master:
  rules:
    - if: '$AGENTRIX_TRIGGER_SOURCE == "agentrix_daemon_webhook" && $AGENTRIX_EVENT_NAME == "merge_request" && $AGENTRIX_EVENT_ACTION == "opened" && $AGENTRIX_BASE_REF == "master"'
      when: on_success
    - when: never
```

**验证**:
- 使用 `yamllint` 检查语法正确性
- 确认规则条件同时要求四个条件全部匹配：trigger source、event name、event action、base ref

### Step 2: 新增 mr-opened-master-summary 工作流

**文件**: `.gitlab/workflow/mr-opened-master-summary.yml`（新建）

**操作**: 创建新的工作流文件，参考 `mr-review.yml` 和 `mr-merged-summary.yml` 的结构：

```yaml
mr-opened-master-summary:
  stage: agentrix_mr
  extends:
    - .agentrix-run
    - .agentrix-rules:daemon-mr-opened-master
  variables:
    AGENTRIX_AGENT: claude code
    AGENTRIX_TITLE: "MR !${AGENTRIX_PR_NUMBER} master summary"
    AGENTRIX_PROMPT: |
      你是 GitLab MR 变更总结机器人。当前触发事件：
      - merge request iid: ${AGENTRIX_PR_NUMBER}
      - title: ${AGENTRIX_MR_TITLE}
      - url: ${AGENTRIX_MR_URL}
      - source branch: ${AGENTRIX_HEAD_REF}
      - target branch: ${AGENTRIX_BASE_REF}

      任务目标：
      1. 使用 `glab mr view ${AGENTRIX_PR_NUMBER}` 读取 MR 信息（标题、描述、标签、作者、评论和关联信息）。
      2. 执行 `git fetch origin ${AGENTRIX_BASE_REF} && git diff origin/${AGENTRIX_BASE_REF}...HEAD` 获取 MR 相对 master 的增量 diff。禁止使用 `glab mr diff` 或其他方式获取 diff。
      3. 基于 diff 总结本次合并到 master 的改动。
      4. 总结可能影响点，包括：
         - 用户可见行为
         - API / 接口兼容性
         - 配置变更
         - 数据变更
         - 部署和回滚风险
         - 测试覆盖缺口
      5. 根据改动生成更准确的 MR 标题，要求：
         - 简洁准确，优先包含改动类型和主要影响对象
         - 使用项目 conventional commit 风格（如 feat:、fix:、chore:）
         - 避免泛泛标题（如 update、fix bug、代码优化）
         执行 `glab mr update ${AGENTRIX_PR_NUMBER} --title "<new-title>"` 更新标题。
         如果更新失败，记录失败原因，在后续评论中说明。
      6. 使用 `glab mr note ${AGENTRIX_PR_NUMBER}` 发布中文评论，格式为：

         ## Master 合并摘要

         ### 建议标题
         <已更新后的 MR 标题，若更新失败则说明原因>

         ### 改动摘要
         - <按模块/功能列出关键改动>

         ### 可能影响点
         - <影响范围、兼容性、配置、数据、部署风险等>

         ### 验证建议
         - <建议 reviewer 或发布前重点验证的点>

      约束：
      - 不得修改仓库文件、创建 commit、push 分支或合并 MR
      - 分析范围限定为 MR 增量 diff，不扩大到非本 MR 的改动
```

**验证**:
- `yamllint` 检查语法
- 确认 `extends` 正确引用了 `.agentrix-run` 和 `.agentrix-rules:daemon-mr-opened-master`
- 确认 `stage` 使用 `agentrix_mr`（与 mr-review 一致）

### Step 3: 确认 .gitlab-ci.yml include 策略

**操作**: 检查 `.gitlab-ci.yml` 的 include 配置是否使用通配符（如 `local: '.gitlab/workflow/*.yml'`）自动包含新文件。如果是通配符，无需额外修改；如果是显式列表，需要添加新文件路径。

**验证**: 确认新增的 `.gitlab/workflow/mr-opened-master-summary.yml` 能被 GitLab CI 正确加载。

## 风险与依赖

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| `AGENTRIX_BASE_REF` 在 `opened` 事件中值为空或不等于 `"master"` | 规则不匹配，job 不触发 | 确认 Agentrix daemon 在 MR opened 事件中正确注入 `AGENTRIX_BASE_REF` 为 MR 的 target branch；参考已有 `daemon-mr-review` 规则使用了 `$AGENTRIX_BASE_REF == $CI_DEFAULT_BRANCH` 的模式 |
| `glab mr update --title` 权限不足 | MR 标题更新失败 | prompt 中已要求失败时在评论中说明原因，不阻塞主流程 |
| Prompt 过长导致 agent 理解偏差 | 输出格式不一致 | prompt 结构清晰，任务步骤编号明确，约束单独列出；可后续根据实际输出迭代调整 |
| `$AGENTRIX_BASE_REF == "master"` 硬编码 vs `$CI_DEFAULT_BRANCH` | 如果仓库默认分支改名则不匹配 | Issue 明确要求目标分支为 `master`，使用硬编码符合需求；如需泛化可后续改为 `$CI_DEFAULT_BRANCH` |
| `.gitlab-ci.yml` 未自动 include 新文件 | job 不生效 | Step 3 中验证 include 策略，必要时显式添加 |

### 关键依赖确认

- Agentrix daemon 在 `merge_request opened` 事件中注入 `AGENTRIX_BASE_REF`（已有 `daemon-mr-review` 使用此变量，可确认可用）
- Agentrix daemon 在事件中注入 `AGENTRIX_PR_NUMBER`、`AGENTRIX_MR_TITLE`、`AGENTRIX_MR_URL`、`AGENTRIX_HEAD_REF`（已被现有工作流使用）
- `glab` CLI 在 runner 环境中可用且具备 MR 读写权限

## 验证方式

1. **静态验证**: 使用 `yamllint` 或 GitLab CI Lint API 验证两个修改/新增文件的 YAML 语法
2. **规则匹配验证**: 创建一个目标分支为 `master` 的测试 MR，确认 `mr-opened-master-summary` job 出现在 pipeline 中并被触发
3. **规则排除验证**: 创建一个目标分支为 `develop` 的 MR，确认该 job 不出现
4. **Diff 分析验证**: 确认 job 执行时使用 `git diff origin/master...HEAD` 获取正确的增量 diff
5. **标题更新验证**: 确认 MR 标题被更新为符合 conventional commit 风格的内容
6. **评论格式验证**: 确认 MR 下出现中文评论，包含"Master 合并摘要"、"建议标题"、"改动摘要"、"可能影响点"、"验证建议"四个 section
7. **失败处理验证**: 模拟 `glab mr update` 失败场景（如权限不足），确认评论中包含失败原因说明
