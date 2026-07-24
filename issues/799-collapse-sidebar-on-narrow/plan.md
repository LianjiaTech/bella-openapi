# Plan: #799 优化侧边栏窄屏自动折叠菜单栏

## 目标

在 `web_v2` 的 dashboard 布局中，当可视区域宽度小于 1280 时，侧边栏默认关闭；用户主动展开后，侧边栏以浮层形式覆盖在内容区域上方；用户切换菜单项后，侧边栏自动关闭。宽度大于等于 1280 时，保持当前桌面端固定侧边栏体验。

## 非目标

- 不修改旧 `web` 项目的侧边栏实现。
- 不改动侧边栏菜单结构、权限判断与路由定义本身。
- 不调整 `/logs` 等页面业务内容组件。
- 不改动接口数据结构、鉴权逻辑或 i18n 路由机制。

## 验收标准

- 在 `web_v2` 的 dashboard 页面中，当窗口宽度小于 1280 时，左侧菜单栏默认关闭。
- 在小于 1280 的场景下，用户可通过显式入口打开侧边栏，侧边栏以浮层形式覆盖在主内容区域上方，不压缩主内容宽度。
- 在小于 1280 的场景下，用户点击任意菜单项跳转后，侧边栏自动关闭。
- 当窗口宽度大于等于 1280 时，侧边栏保持当前固定布局，不影响现有桌面端操作。
- 不影响菜单分组展开、权限菜单显示、设置弹窗与登出入口。
- `web_v2` 工程 lint 通过。

## 约束

- 只修改 `web_v2` 项目代码，不在 `web` 目录落地实现。
- 继续沿用 `SidebarProvider` 管理全局侧边栏状态，避免在各页面分别维护响应式逻辑。
- 宽度判断基于浏览器可视区域，前端侧完成，不依赖服务端渲染结果。
- 桌面固定侧边栏与窄屏浮层侧边栏应复用同一套导航内容与展开状态，避免双份实现。
- 需要兼容当前 dashboard 布局中 `Sidebar` 固定定位、`main` 使用 `ml-64` 预留空间的结构。

## 变更范围

- `web_v2/src/components/providers/sidebar-provider.tsx`
- `web_v2/src/components/layout/sidebar/sidebar.tsx`
- `web_v2/src/components/layout/top-bar.tsx`
- `web_v2/src/app/[locale]/(dashboard)/layout.tsx`
- 视联动影响，关注使用公共 `TopBar` 的页面，但尽量避免逐页修改：
  - `web_v2/src/app/[locale]/(dashboard)/overview/page.tsx`
  - `web_v2/src/app/[locale]/(dashboard)/models/page.tsx`
  - `web_v2/src/app/[locale]/(dashboard)/metadata/page.tsx`

## 实现思路

### 1. 在 SidebarProvider 中补充响应式侧边栏状态
- **目标**：统一管理桌面态与窄屏浮层态的侧边栏显示状态。
- **涉及文件**：`web_v2/src/components/providers/sidebar-provider.tsx`
- **具体改动**：
  - 在现有 context 中新增类似 `isDesktop`、`isSidebarOpen`、`openSidebar`、`closeSidebar`、`toggleSidebar` 的状态与方法。
  - 基于 `window.innerWidth >= 1280` 初始化并监听 `resize`，在桌面态与窄屏态之间同步切换。
  - 进入桌面态时强制侧边栏可见；进入窄屏态时默认关闭侧边栏。
  - 保持现有 `selectedEndpoint`、`collapsedCategories` 的 sessionStorage 逻辑不变，使“整体侧边栏开关”和“分类折叠”状态解耦。

### 2. 调整 dashboard layout，使主内容区域在窄屏不再永久预留 16rem
- **目标**：避免当前 `main` 的 `ml-64` 和 `max-w-[calc(100vw-16rem)]` 在窄屏下留下空白，支持浮层覆盖。
- **涉及文件**：`web_v2/src/app/[locale]/(dashboard)/layout.tsx`
- **具体改动**：
  - 让 `main` 的左侧间距与最大宽度根据 `isDesktop` 动态切换。
  - 桌面态保留当前侧边栏占位逻辑；窄屏态移除左侧预留，让内容区占满宽度。
  - 保持现有 `h-screen` 和背景样式，避免影响页面滚动容器。

### 3. 将 Sidebar 改为支持桌面固定 + 窄屏浮层两种展示模式
- **目标**：复用现有导航结构，在不同宽度下切换呈现方式。
- **涉及文件**：`web_v2/src/components/layout/sidebar/sidebar.tsx`
- **具体改动**：
  - 从 `useSidebar()` 中读取新的桌面态判断与开关状态。
  - 桌面态继续使用当前固定在左侧的 `w-64` 侧边栏。
  - 窄屏态改为 `fixed` 浮层面板，配合遮罩层覆盖在内容区域上方。
  - 保留当前菜单分组展开逻辑、权限菜单计算、设置按钮和登出按钮。
  - 增加浮层关闭入口，并处理点击遮罩关闭。

### 4. 在 TopBar 增加窄屏菜单入口
- **目标**：让默认关闭的窄屏侧边栏可被用户主动唤起。
- **涉及文件**：`web_v2/src/components/layout/top-bar.tsx`
- **具体改动**：
  - 在 `TopBar` 左侧增加菜单按钮，仅在小于 1280 的场景下显示。
  - 按钮点击后调用 `toggleSidebar()` 或 `openSidebar()`。
  - 保持 `leftAction`、标题区、主题切换按钮现有布局可用，避免破坏已有页面头部结构。

### 5. 在菜单跳转后自动关闭窄屏浮层侧边栏
- **目标**：满足“切换菜单选项后再次自动关闭”的交互要求。
- **涉及文件**：`web_v2/src/components/layout/sidebar/sidebar.tsx`
- **具体改动**：
  - 为普通菜单项与子菜单项的跳转行为增加统一关闭逻辑。
  - 在窄屏态下，点击 `Link` 后调用 `closeSidebar()`；桌面态下不执行额外关闭。
  - 确保关闭逻辑不影响当前路径高亮与父级菜单自动展开。

### 6. 验证公共页面布局与静态检查
- **目标**：确保改动满足需求且不影响 dashboard 主要页面。
- **涉及文件**：以验证为主，重点关注使用 `TopBar` 的 dashboard 页面
- **具体改动**：
  - 本地启动 `web_v2` 开发服务，手动验证 `overview`、`models`、`metadata` 等 dashboard 页面在宽度大于/小于 1280 时的行为。
  - 重点验证：默认关闭、手动打开、遮罩覆盖、菜单跳转自动关闭、菜单分组展开、设置弹窗、登出按钮。
  - 执行 `web_v2` 下的 `npm run lint`。

## 风险与依赖

- 当前 `Sidebar` 使用固定定位，而 `layout.tsx` 通过 `ml-64` 为内容区留白；两者需同时调整，否则窄屏下会出现空白区或遮挡。
- `TopBar` 是 dashboard 多页面复用组件，新增菜单入口时需确认不会影响无标题、带自定义 `leftAction` 的页面布局。
- 菜单项当前直接使用 `Link` 渲染，自动关闭逻辑需要选择合适的绑定方式，避免破坏 Next.js 导航与点击区域行为。
- `web_v2/src/components/ui/virtualGrid/useResponsiveColumns.ts` 目前写死按侧边栏 256px 计算内容宽度；若页面依赖该逻辑，后续实现时需评估窄屏态是否需要一并适配，避免卡片列数计算偏差。
