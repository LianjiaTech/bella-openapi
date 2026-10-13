# Issues

## 命名规则

每个 Issue 对应一个子目录，命名为 `<issue-id>-<slug>/`。

示例：`42-fix-login-timeout/`

## 目录内文件

| 文件             | 用途                                         | 级别               |
| ---------------- | -------------------------------------------- | ------------------ |
| `plan.md`        | 目标、非目标、验收标准、约束、变更范围、实现思路、风险与依赖 | 必须               |
| `todos.md`       | 任务拆解与执行进度                             | 建议               |
| `diagnosis.md`   | Bug 诊断：症状、根因、证据                     | Bug 类 Issue 建议  |

## 生命周期

1. **创建** — 通过 `/create-issue` 或手动创建 GitLab Issue，同时在 `issues/` 下建立对应目录
2. **规划** — 编写 `plan.md`（可由 `/create-plan` 生成初稿），经 MR 审查后合并
   - 标记 `plan::skip` 可跳过 plan MR 审批流程，但 plan.md 仍必须创建
3. **执行** — 按 plan 实施，在 `todos.md` 中跟踪进度
4. **交付** — 提交 MR，关联 Issue（使用 closing pattern: `Closes #<id>`）
5. **关闭** — MR 合并后 Issue 自动关闭
