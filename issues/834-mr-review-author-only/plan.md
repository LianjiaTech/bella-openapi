# Execution Plan for #834: MR review 只审查 MR 自身的提交

## 目标

修改 `.gitlab/workflow/mr-review.yml` 中的 review prompt，使 MR 自动审查仅关注 **MR 自身的提交所引入的改动**（即 source branch 相对于 target branch 的增量 diff），排除通过 rebase 或同步 target branch 带入的已有代码。不按作者过滤，而是按 MR 的增量 diff 确定审查范围。

## 非目标

- 不按 commit 作者过滤（审查所有属于该 MR 的提交，无论作者是谁）
- 不修改 CI pipeline 的触发条件或 rules
- 不修改审查机器人的输出格式
- 不涉及其他 workflow 文件
- 不新增脚本或工具

## 验收标准

1. MR review prompt 明确指示审查范围为 MR source branch 相对于 target branch 的增量 diff（`git diff origin/${target_branch}...HEAD`）
2. rebase / 同步 target branch 造成的已有代码变化不会被审查（因为这些变化已存在于 target branch 中，不会出现在增量 diff 中）
3. 纯 rebase / 纯同步 target branch（MR 无自身增量改动）时，机器人不发布评论（包括不发布"跳过"评论）

## 约束

- 仅修改 `.gitlab/workflow/mr-review.yml` 文件
- 不引入新的外部依赖或脚本
- 保持现有 CI 变量和 extends 结构不变
- 使用 `git diff origin/${AGENTRIX_BASE_REF}...HEAD` 获取 MR 的增量 diff

## 变更范围

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `.gitlab/workflow/mr-review.yml` | 修改 | 调整 AGENTRIX_PROMPT，指示 agent 使用三点 diff 获取 MR 增量改动 |

## 实现思路

### Step 1: 分析当前 diff 获取机制

**操作**：阅读 `.gitlab/workflow/mr-review.yml` 和相关 `.agentrix-run` 模板，理解当前 review agent 如何获取 MR diff。

**目的**：确认 agent 获取 diff 的方式和可用的 CI 变量（`AGENTRIX_PR_NUMBER`、`AGENTRIX_HEAD_REF`、`AGENTRIX_BASE_REF`）。

**验证**：记录 diff 获取路径，确认 `AGENTRIX_BASE_REF` 对应 target branch。

### Step 2: 修改 AGENTRIX_PROMPT 限定审查范围为 MR 增量 diff

**操作**：在 `AGENTRIX_PROMPT` 中增加以下指令：

1. 在审查前，使用 `git diff origin/${AGENTRIX_BASE_REF}...HEAD` 获取 MR 的增量 diff（三点 diff 表示 source branch 相对于与 target branch 的 merge-base 的差异，天然排除了 rebase 引入的 target branch 已有代码）
2. 仅基于该增量 diff 进行审查，不使用 `glab mr diff` 或其他可能包含 rebase 变更的方式
3. 如果增量 diff 为空（纯 rebase，无实质改动），直接结束，不发布任何评论

**具体 prompt 修改要点**：
- 在"任务目标"前增加"审查范围限定"段落，明确要求使用三点 diff
- 明确说明"审查的是 MR 自身带来的改动，不区分作者，只看增量"
- 明确说明"如果增量 diff 为空，静默退出，不发布任何评论"

**验证**：本地 review prompt 文本内容，确认逻辑完整且简洁明确。

### Step 3: 提交变更

**操作**：
1. `git add .gitlab/workflow/mr-review.yml`
2. `git commit -m "fix: limit MR review scope to MR's own commits only (#834)"`
3. 推送到远程

**验证**：push 成功，MR diff 仅包含 `.gitlab/workflow/mr-review.yml` 的改动。

## 风险与依赖

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| agent 不使用指定的 git diff 命令 | agent 可能依赖 `glab mr diff` 获取完整 diff（含 rebase 变更） | prompt 中明确要求必须使用 `git diff origin/${AGENTRIX_BASE_REF}...HEAD`，禁止使用其他方式获取 diff |
| 三点 diff 在特殊 git 历史下的行为 | 如果 source branch 未及时 fetch target branch，merge-base 可能不准确 | CI 环境中默认会 fetch 完整 ref，风险低；可在 prompt 中加入 `git fetch origin ${AGENTRIX_BASE_REF}` 前置步骤 |
| 纯 prompt 方案的可靠性 | LLM 不一定严格遵循过滤逻辑 | 指令简洁明确（单一 git 命令），比按作者过滤的多步逻辑更易被 LLM 正确执行 |

## 验证方式

1. **文本审查**：检查修改后的 prompt 逻辑是否完整、无歧义
2. **实际触发测试**：
   - 创建一个普通 MR（含代码改动）→ 预期正常审查增量 diff
   - 对该 MR 进行 rebase 操作 → 预期不重复审查 target branch 已有代码
   - 创建一个纯 rebase MR（无增量改动）→ 预期不发布任何评论
3. **回归验证**：确认正常 MR 的 review 功能不受影响
