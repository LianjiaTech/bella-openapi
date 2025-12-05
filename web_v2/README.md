# Bella OpenAPI v2

新一代 AI API 网关前端 - 基于 Next.js 14 构建

## 技术栈

- **框架**: Next.js 14 (App Router)
- **语言**: TypeScript 5
- **样式**: Tailwind CSS 3
- **HTTP 客户端**: Axios
- **代码规范**: ESLint + Next.js 规则

## 开发指南

### 安装依赖

```bash
npm install
```

### 开发模式

```bash
npm run dev
```

访问 [http://localhost:3000](http://localhost:3000) 查看应用

### 构建生产版本

```bash
npm run build
npm start
```

### 代码检查

```bash
npm run lint
```

## 项目结构

```
web_v2/
├── src/
│   ├── app/                # Next.js App Router 页面
│   │   ├── layout.tsx      # 根布局
│   │   ├── page.tsx        # 首页
│   │   └── globals.css     # 全局样式
│   ├── components/         # React 组件
│   │   └── ui/             # UI 组件（预留 shadcn/ui）
│   ├── lib/                # 工具库
│   │   ├── utils.ts        # 通用工具函数
│   │   └── api/            # API 调用函数
│   │       └── client.ts   # Axios 客户端配置
│   ├── hooks/              # 自定义 React Hooks
│   └── types/              # TypeScript 类型定义
│       └── common.ts       # 通用类型
├── public/                 # 静态资源
├── package.json            # 项目依赖
├── next.config.mjs         # Next.js 配置
├── tsconfig.json           # TypeScript 配置
└── tailwind.config.ts      # Tailwind CSS 配置
```

## 开发规范

### 目录组织

- **页面组件**: 放在 `src/app/` 目录，使用 Next.js App Router
- **可复用组件**: 放在 `src/components/` 目录
- **数据处理逻辑**: 使用自定义 Hooks，放在 `src/hooks/` 目录
- **API 调用**: 放在 `src/lib/api/` 目录
- **类型定义**: 放在 `src/types/` 目录

### 样式规范

- 优先使用 Tailwind CSS 工具类
- 复杂样式可以使用 CSS Modules
- 使用 `cn()` 工具函数合并类名

### 代码规范

- 使用 TypeScript 严格模式
- 遵循 ESLint 规则
- 组件和函数添加清晰的注释
- UI 渲染与数据逻辑分离

## 环境变量

创建 `.env.local` 文件配置环境变量：

```env
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
```

## Docker 部署

项目配置了 `standalone` 输出模式，支持 Docker 容器化部署。

## 与 web/ 目录的关系

- `web/`: 当前生产环境使用的完整版本
- `web_v2/`: 新一代版本，简化基础结构，用于未来逐步替换 web/

## License

查看项目根目录的 LICENSE 文件
