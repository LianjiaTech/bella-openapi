# 执行计划：清理 Web 目录

Related to #833

## 目标

删除旧的 `web/` 目录，将 `web_v2/` 重命名为 `web/`，并更新所有引用该路径的配置文件、脚本、文档和测试基础设施，确保项目构建、部署、开发流程正常运作。

## 非目标

- 不修改前端业务逻辑代码（组件、页面、API 调用等）
- 不升级前端依赖或框架版本
- 不调整 Docker 镜像名称（`bella-openapi-web` 保持不变）
- 不处理其他 issue 中 plan.md 对 `web_v2` 的历史引用（属文档快照，不影响运行）

## 验收标准

1. `web/` 目录为原 `web_v2/` 的完整内容，旧 `web/` 目录已删除
2. `web_v2/` 目录不再存在
3. `docker-compose.yml` 中 web 服务的 build context 指向 `./web`
4. `start.sh` 中构建命令指向 `./web`
5. `.gitignore` 中所有 `web_v2/` 相关条目已替换为 `web/` 或移除（避免重复）
6. `playwright.config.ts` 中 webServer command 指向 `cd web && npm run dev`
7. `CLAUDE.md` 描述与实际目录结构一致
8. `npm run dev`、`npm run build`、`npm run lint` 在新 `web/` 下正常执行
9. `docker-compose up web` 构建成功

## 约束

- 单次 PR 完成，不拆分多步
- 使用 `git mv` 保留文件历史
- 不修改前端源码（`src/` 内 ts/tsx/js 文件内容不变）
- CI/CD workflow 中镜像名 `bella-openapi-web` 不变，无需修改

## 变更范围

| 文件/目录 | 操作 | 说明 |
|---|---|---|
| `web/` | 删除 | 移除旧前端目录 |
| `web_v2/` → `web/` | 重命名 | `git mv web_v2 web` |
| `docker-compose.yml` | 修改 | build context `./web_v2` → `./web` |
| `start.sh` | 修改 | 构建路径 `./web_v2` → `./web` |
| `.gitignore` | 修改 | 合并/替换 `web_v2/` 条目为 `web/` |
| `playwright.config.ts` | 修改 | webServer command 路径更新 |
| `CLAUDE.md` | 修改 | 前端相关路径描述更新（已指向 `web/`，确认一致） |
| `specs/apikey/e2e-test-plan.md` | 修改 | 路径引用 `web_v2` → `web` |

## 实现思路

### 步骤 1：删除旧 `web/` 目录

```bash
git rm -rf web/
```

- **验证**：`ls web/` 应报错不存在

### 步骤 2：重命名 `web_v2/` 为 `web/`

```bash
git mv web_v2 web
```

- **验证**：`ls web/src` 显示原 `web_v2` 的源码结构；`git status` 显示 rename 操作

### 步骤 3：更新 `docker-compose.yml`

将第 39 行：
```yaml
      context: ./web_v2
```
改为：
```yaml
      context: ./web
```

- **验证**：`grep "context:" docker-compose.yml` 输出 `./web`

### 步骤 4：更新 `start.sh`

将第 339 行：
```bash
                --push ./web_v2
```
改为：
```bash
                --push ./web
```

- **验证**：`grep "web_v2" start.sh` 无输出

### 步骤 5：更新 `.gitignore`

移除所有 `web_v2/` 专有条目，保留 `web/` 条目（当前已有 `web/` 相关行，合并去重即可）。

目标 `.gitignore` web 相关部分：
```
#### web ####
web/node_modules/
web/.next/
web/out/
web/build
web/yarn-debug.log*
web/yarn-error.log*
web/.env
web/bin
```

- **验证**：`grep "web_v2" .gitignore` 无输出；`grep "web/" .gitignore` 显示合并后条目

### 步骤 6：更新 `playwright.config.ts`

将第 128 行：
```typescript
    command: 'cd web_v2 && npm run dev',
```
改为：
```typescript
    command: 'cd web && npm run dev',
```

- **验证**：`grep "web_v2" playwright.config.ts` 无输出

### 步骤 7：更新 `specs/apikey/e2e-test-plan.md`

全局替换 `web_v2` → `web`（该文件中有 3 处引用）。

- **验证**：`grep "web_v2" specs/apikey/e2e-test-plan.md` 无输出

### 步骤 8：确认 `CLAUDE.md` 一致性

当前 `CLAUDE.md` 已使用 `web/` 路径描述前端结构，与重命名后一致，无需修改。确认无 `web_v2` 引用残留。

- **验证**：`grep "web_v2" CLAUDE.md` 无输出

### 步骤 9：全局扫描残留引用

```bash
grep -rn "web_v2" --include="*.yml" --include="*.yaml" --include="*.sh" \
  --include="*.ts" --include="*.tsx" --include="*.js" --include="*.json" \
  --include="*.md" --include="*.conf" --include="Dockerfile*" . \
  | grep -v node_modules | grep -v .next | grep -v issues/
```

排除 `issues/` 下其他 plan 文件的历史引用（不修改已合并 plan 文档）。如有遗漏，逐一修复。

- **验证**：上述命令输出为空或仅剩 issues/ 下历史 plan 文件

### 步骤 10：本地验证

```bash
cd web && npm install && npm run lint && npm run build
```

- **验证**：lint 和 build 均通过无错误

## 风险与依赖

| 风险 | 影响 | 缓解措施 |
|---|---|---|
| 其他开发者本地有基于 `web_v2/` 路径的未提交改动 | 合并后本地构建失败 | PR 描述中明确说明路径变更，建议重新 clone 或手动调整 |
| 进行中的其他 PR 引用 `web_v2/` 路径 | 合并冲突 | 优先合并本 PR，其他 PR rebase 时统一修正路径 |
| `git mv` 在某些 Git 客户端显示为删除+新增而非 rename | 文件历史追踪困难 | 单独一次 commit 完成 rename，不混合其他修改 |
| CI/CD 流水线中硬编码 `web_v2` 路径 | 构建失败 | 已检查 `.github/workflows/`，未发现 `web_v2` 引用；`release.yml` 仅引用镜像名不受影响 |

**依赖**：无外部依赖。本变更为纯仓库结构调整。

## 验证方式

1. **静态检查**：`grep -rn "web_v2" . | grep -v node_modules | grep -v .next | grep -v issues/` 输出为空
2. **前端 lint**：`cd web && npm run lint` 通过
3. **前端构建**：`cd web && npm run build` 通过
4. **Docker 构建**：`docker-compose build web` 成功
5. **Playwright 配置**：`npx playwright test --list` 能识别 webServer 配置
6. **人工复核**：确认 `git log --follow web/src/app/page.tsx` 能追溯到 `web_v2` 时期的历史
