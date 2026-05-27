# /apikey 页面 E2E 测试计划

## 目标

为 `web_v2/src/app/[locale]/(dashboard)/apikey/page.tsx` 对应的“我的密钥”页面制定可落地的 Playwright E2E 测试规划，测试运行基于仓库根目录下的 Playwright 配置与 `tests/` 目录组织方式。

## 测试框架现状

- Playwright 配置文件位于根目录：[playwright.config.ts](../../playwright.config.ts)
- 当前 `testDir` 为 `./tests`
- 主测试项目为 `chromium`，依赖 `setup` 登录项目
- 认证态复用 `tests/.auth/cas-user.json`
- 默认 web server 启动命令为 `cd web_v2 && npm run dev`

## 页面核心能力

基于源码分析，`/apikey` 页面包含以下核心能力：

1. 页面标题与说明展示
2. API Key 列表拉取与表格渲染
3. 每条 API Key 余额异步拉取
4. 列表 loading / 空态 / 搜索无结果态
5. 搜索框输入、500ms 防抖、清空搜索
6. 分页切换
7. 创建新密钥
8. 创建成功弹窗与复制 API Key
9. 行内编辑名称
10. 行内编辑服务名
11. 编辑安全等级
12. 行操作菜单：管理子 AK / 重置 / 转交 / 复制 ak code / 删除
13. 重置成功后展示新 key 弹窗
14. 转交弹窗中的用户搜索、历史占位、原因必填校验
15. 删除确认弹窗
16. locale 路由下访问该页面

## 相关接口与网络断言点

页面依赖的接口主要来自 [web_v2/src/lib/api/apiKeys.ts](../../web_v2/src/lib/api/apiKeys.ts)：

- `GET /console/apikey/page`
- `GET /console/apikey/balance/:akCode`
- `POST /console/apikey/apply`
- `POST /console/apikey/reset`
- `POST /console/apikey/inactivate`
- `POST /console/apikey/rename`
- `POST /console/apikey/bindService`
- `GET /v1/userInfo/search`
- `POST /console/apikey/owner/transfer`
- `POST /console/apikey/certify`

建议对业务主路径中的关键请求做显式网络断言，尤其关注：

- 查询参数：`page`、`searchParam`
- 变更类请求 payload：`code`、`name`、`serviceId`、`certifyCode`、`targetUserId`、`transferReason`

## 推荐测试组织结构

建议在根目录 `tests/e2e/apikey-page/` 下组织测试文件，后续可以按以下结构拆分：

- `tests/e2e/apikey-page/render-and-list.spec.ts`
- `tests/e2e/apikey-page/search-and-create.spec.ts`
- `tests/e2e/apikey-page/row-actions.spec.ts`
- `tests/e2e/apikey-page/edit-and-safety.spec.ts`
- `tests/e2e/apikey-page/transfer.spec.ts`

## 优先级规划

### P0

优先覆盖能提供最大回归收益的主流程：

1. 页面基础渲染与首屏加载
2. 列表 loading / 空态 / 搜索无结果态
3. 搜索防抖与清空
4. 创建新密钥
5. 重置 API Key
6. 删除 API Key
7. 分页切换

### P1

其次覆盖主要编辑与复杂交互流程：

1. 编辑名称
2. 编辑服务名
3. 安全等级编辑
4. 转交流程
5. 复制 ak code
6. 跳转管理子 AK 页面

### P2

最后补齐边界体验和稳定性场景：

1. 各类错误态展示
2. 转交历史占位态
3. locale 路由验证
4. toast、clipboard、按钮 loading/disabled 状态细节

## 详细测试套件规划

---

## Suite A: 页面渲染与列表状态

### 1. should render page shell and table headers

**目标**
- 验证页面基础结构渲染正常

**UI 断言**
- 标题“我的密钥”可见
- 描述“管理您直接持有的 API 密钥，设置额度和安全等级”可见
- “创建新密钥”按钮可见
- 搜索框 placeholder 为“搜索 API Key...”
- 表头包含：
  - 密钥代码
  - 名称
  - 服务名
  - 月额度配置
  - 安全等级
  - 月额度使用
  - 备注
  - 操作

**网络断言**
- 首屏触发 `GET /console/apikey/page`

### 2. should show loading row before list resolves

**目标**
- 验证列表加载态

**做法**
- 拦截 `GET /console/apikey/page` 并延迟返回

**断言**
- 表格 loading 行可见
- 请求完成后 loading 消失，真实数据出现

### 3. should show empty state when no api keys exist

**做法**
- page 接口返回空数组与 `has_more=false`

**断言**
- 显示“暂无数据”
- 分页器不显示

### 4. should show search empty state when keyword has no match

**做法**
- 输入搜索关键词后返回空列表

**断言**
- 显示“未找到匹配 \"关键词\" 的结果”
- 显示辅助说明“请尝试其他关键词或清空搜索查看所有数据”

### 5. should fetch balances for every returned api key

**做法**
- page 接口返回多个 key
- balance 接口分别返回数据

**断言**
- 每个 key 均触发余额请求
- 月额度使用列展示对应数据

### 6. should paginate between pages

**做法**
- 第 1 页返回 `has_more=true`
- 第 2 页返回不同数据

**断言**
- 点击下一页后，请求参数 `page=2`
- 第二页数据展示
- 点击上一页后返回第一页

---

## Suite B: 搜索与创建

### 7. should debounce search request by 500ms

**目标**
- 验证搜索防抖逻辑

**断言**
- 输入期间出现搜索中状态
- 500ms 后才发起请求
- 请求使用最终关键词

### 8. should reset page to 1 when search query changes

**做法**
- 先翻到第 2 页，再输入搜索词

**断言**
- 搜索请求中的 `page=1`

### 9. should clear search with clear button

**断言**
- 输入后清空按钮出现
- 点击后输入框清空
- 重新请求全量列表

### 10. should create a new api key and show success dialog

**做法**
- 拦截 `POST /console/apikey/apply` 返回完整 API Key

**UI 断言**
- 创建成功弹窗打开
- 显示返回的 API Key
- 警示文案可见
- “复制 API Key”与“确认并关闭”按钮可见

**网络断言**
- 请求 payload 包含 `ownerCode` 与 `ownerName`

### 11. should copy api key in created dialog

**做法**
- mock clipboard 或验证复制副作用

**断言**
- 点击后按钮文案变为“已复制”或成功反馈出现

### 12. should refresh first page after closing created dialog

**断言**
- 关闭创建成功弹窗后重新请求第一页列表

---

## Suite C: 行操作菜单、重置与删除

### 13. should open action menu for a row

**断言**
- 行菜单中显示：
  - 管理子AK
  - 重置
  - 转交
  - 复制ak code
  - 删除

### 14. should navigate to sub-ak page

**断言**
- 点击“管理子AK”后跳转到 `/apikey/sub-ak/:code`

### 15. should copy ak code from action menu

**断言**
- 点击“复制ak code”后触发复制逻辑
- 如可稳定验证，断言成功 toast

### 16. should reset api key and show new key dialog

**做法**
- 点击重置 -> 确认 -> reset 接口返回新 key

**断言**
- 确认弹窗显示“重置 API Key”
- 提交时按钮显示“重置中...”
- 成功后出现新 key 成功弹窗

### 17. should delete api key and refresh list

**做法**
- 点击删除 -> 确认 -> inactivate 接口返回 true

**断言**
- 删除确认弹窗出现
- 提交时按钮显示“删除中...”
- 成功后列表重新加载

### 18. should show page error when reset or delete fails

**做法**
- reset / delete 接口返回失败

**断言**
- 页面顶部错误提示区域出现
- 错误文案符合接口返回信息或默认错误信息

---

## Suite D: 编辑名称、服务名与安全等级

### 19. should edit api key name

**做法**
- 点击名称旁编辑按钮
- 修改名称后提交

**网络断言**
- `POST /console/apikey/rename` payload 为 `{ code, name }`

**UI 断言**
- 弹窗标题为“修改名称”
- 成功后弹窗关闭并刷新列表

### 20. should edit service id

**网络断言**
- `POST /console/apikey/bindService` payload 为 `{ code, serviceId }`

**UI 断言**
- 弹窗标题为“修改服务名”
- 提交后列表刷新

### 21. should submit edit dialog with Enter key

**断言**
- 输入框按 Enter 能提交编辑

### 22. should open safe level dialog

**断言**
- 点击安全等级编辑按钮后，出现“安全认证”弹窗
- 显示“安全合规申请”链接
- 存在认证码输入框与确认按钮

### 23. should validate certify code is required

**做法**
- 空输入直接提交

**断言**
- 不发起 certify 请求
- 出现“请输入安全认证码” toast

### 24. should submit certify code and refresh list

**网络断言**
- `POST /console/apikey/certify` payload 为 `{ certifyCode, code }`

**UI 断言**
- 成功 toast 可见
- 弹窗关闭
- 列表重新加载

---

## Suite E: 转交流程

### 25. should open transfer dialog with default state

**断言**
- 标题“转交API Key - 搜索用户”可见
- 默认提示“请输入关键词开始搜索”可见
- “查看转交历史”入口可见

### 26. should search users with debounce in transfer dialog

**做法**
- 输入用户名、邮箱或 ID 关键词
- 拦截 `GET /v1/userInfo/search`

**断言**
- 展示“搜索中...”
- 返回结果后展示候选用户

### 27. should show no result and error state in transfer search

**做法**
- 分别返回空结果与错误

**断言**
- 空结果文案为“未找到匹配的用户”
- 错误文案为“搜索失败,请重试”

### 28. should require transfer reason before submit

**做法**
- 选中目标用户进入确认视图，不填写原因直接提交

**断言**
- 出现“请输入转交原因”
- 不触发 transfer 请求

### 29. should transfer api key successfully

**网络断言**
- `POST /console/apikey/owner/transfer` payload 包含：
  - `akCode`
  - `targetUserId`
  - `transferReason`

**UI 断言**
- 成功后弹窗关闭

### 30. should show transfer history placeholder

**断言**
- 点击“查看转交历史”后看到“暂无转交历史记录”
- 能返回搜索视图

---

## Suite F: locale 与访问控制

### 31. should render under locale route

**目标**
- 验证 locale 路由对页面功能无影响

**断言**
- `/[locale]/apikey` 页面可正常打开
- 核心 UI 元素可见

### 32. should handle unauthenticated access properly

**说明**
- 页面依赖 `useAuth()` 提供当前用户信息
- 若未登录环境存在跳转或拦截，需要单独验证访问控制

**断言建议**
- 未登录时重定向到登录页，或出现认证失败处理逻辑

## Seed / Setup 策略建议

### 推荐模式：storageState + API seed / network mock 混合

该页面依赖当前用户登录态，因此推荐：

1. 继续使用 Playwright 已有 `setup` 项目生成登录态
2. 主测试项目复用 `tests/.auth/cas-user.json`
3. 对业务关键接口按场景分别使用：
   - API seed：用于构造稳定可复用的列表数据
   - route mock：用于空态、错误态、边界态、剪贴板与 toast 等场景

### 更适合 API seed 的场景

- 准备第一页 / 第二页 API Key 数据
- 构造可搜索的数据集
- 构造可编辑、可删除、可重置的 API Key
- 构造用户搜索结果

### 更适合 route mock 的场景

- loading 态
- 空态
- 搜索无结果
- 后端错误态
- clipboard 行为
- `window.open` 行为
- toast 校验
- 转交历史占位态

## 测试数据建议

建议准备以下基础数据夹具：

1. `userWithKeys`
   - 至少 2~3 个 API Key
   - 覆盖完整字段与空字段情况

2. `paginatedKeys`
   - page1 / page2 明确区分的数据

3. `editableKey`
   - 适合验证名称、服务名、安全等级编辑

4. `resettableKey`
5. `deletableKey`
6. `searchableUsers`
   - 至少包含一个可成功转交目标

## 断言策略建议

### UI 断言优先

适用于：
- 页面元素
- 表格行展示
- 对话框
- 空态/错误态/加载态
- 路由跳转

### 网络断言优先

适用于：
- 列表查询参数
- 创建 / 删除 / 重置 / 编辑 / 转交 的 payload
- 翻页与搜索请求的查询参数

### toast / clipboard 断言

适用于：
- 复制成功/失败
- 安全认证码为空
- 安全等级修改成功

## 建议落地顺序

### 第一阶段

- render-and-list
- search-and-create
- row-actions 中的 reset/delete
- 分页

### 第二阶段

- 名称编辑
- 服务名编辑
- 安全等级
- 子 AK 跳转

### 第三阶段

- 转交流程
- locale
- 未登录访问控制
- 边界与错误态增强

## 总结

`/apikey` 页面是一个典型的“列表 + 多弹窗 + 多副作用请求”的管理页，适合采用“稳定登录态 + 分层接口 mock/seed”的方式建设 E2E 测试。建议先完成 P0 主路径回归，再逐步补充编辑与转交流程，以获得最高的测试收益与维护性。