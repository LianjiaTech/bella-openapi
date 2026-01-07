# Mock 数据配置说明

## 概述

本项目支持通过环境变量控制是否使用 Mock 数据，方便开发和测试。

## 目录结构

```
web_v2/
├── src/
│   ├── mocks/
│   │   └── data/                    # Mock 数据文件目录
│   │       ├── categoryTree.json    # 分类树数据
│   │       └── endpointDetails.json # Endpoint 详情数据
│   └── app/
│       └── api/
│           └── v1/
│               └── meta/            # API 路由
├── .env.example                     # 环境变量示例文件
└── .env.local                       # 本地环境变量（不提交到 Git）
```

## 配置方式

### 1. 复制环境变量文件

如果项目中没有 `.env.local` 文件，请复制示例文件：

```bash
cp .env.example .env.local
```

### 2. 配置环境变量

编辑 `.env.local` 文件：

```bash
# 使用 Mock 数据
NEXT_PUBLIC_USE_MOCK=true

# 后端 API 地址（当使用真实 API 时）
BACKEND_API_URL=http://localhost:8080
```

### 3. 重启开发服务器

修改环境变量后，需要重启开发服务器才能生效：

```bash
npm run dev
```

## 使用场景

### 场景一：使用 Mock 数据（开发/测试）

适合前端独立开发，不依赖后端服务。

```bash
# .env.local
NEXT_PUBLIC_USE_MOCK=true
```

此时所有 API 请求将返回 Mock 数据。

### 场景二：使用真实后端 API（联调/生产）

适合前后端联调或生产环境。

```bash
# .env.local
NEXT_PUBLIC_USE_MOCK=false
BACKEND_API_URL=http://your-backend-api.com
```

此时所有 API 请求将转发到真实后端服务。

## Mock 数据维护

### 更新 Mock 数据

Mock 数据位于 `src/mocks/data/` 目录，可以直接编辑 JSON 文件：

- **categoryTree.json**: 分类树数据
- **endpointDetails.json**: Endpoint 详情数据

### 添加新的 Mock 数据

1. 在 `src/mocks/data/` 目录创建新的 JSON 文件
2. 在对应的 API 路由中导入并使用该文件
3. 添加环境变量判断逻辑

示例：

```typescript
// src/app/api/v1/example/route.ts
import { NextResponse } from 'next/server';
import exampleData from '@/mocks/data/example.json';

export async function GET() {
  const useMock = process.env.NEXT_PUBLIC_USE_MOCK === 'true';

  if (useMock) {
    return NextResponse.json({
      code: 200,
      data: exampleData,
      message: null,
      timestamp: Date.now(),
    });
  }

  // 真实 API 调用逻辑
  // ...
}
```

## 已实现的 Mock API

- `GET /api/v1/meta/category/tree/all` - 获取所有分类树
- `GET /api/v1/meta/endpoint/details` - 获取 Endpoint 详情

## 注意事项

1. **`.env.local` 文件不应提交到 Git**：该文件包含本地配置，已添加到 `.gitignore`
2. **环境变量修改需重启**：修改环境变量后必须重启开发服务器
3. **生产环境配置**：在生产环境中，通过部署平台设置环境变量，而不是使用 `.env.local`
4. **Mock 数据格式**：Mock 数据的格式应与真实 API 返回的格式保持一致

## 开发建议

1. **前端开发阶段**：使用 Mock 数据（`NEXT_PUBLIC_USE_MOCK=true`）
2. **联调阶段**：切换到真实 API（`NEXT_PUBLIC_USE_MOCK=false`）
3. **测试阶段**：根据测试需要灵活切换
4. **生产部署**：使用真实 API

## 故障排查

### API 返回 501 错误

错误信息：`Real API not implemented. Please set NEXT_PUBLIC_USE_MOCK=true to use mock data.`

**解决方案**：
- 检查 `.env.local` 中 `NEXT_PUBLIC_USE_MOCK` 是否设置为 `true`
- 重启开发服务器

### Mock 数据没有生效

**可能原因**：
1. 未重启开发服务器
2. 环境变量拼写错误
3. `.env.local` 文件位置不正确（应在项目根目录）

**解决方案**：
1. 重启开发服务器：`npm run dev`
2. 检查环境变量名称是否为 `NEXT_PUBLIC_USE_MOCK`
3. 确保 `.env.local` 在 `web_v2/` 目录下

## 更多信息

如有问题，请查看：
- Next.js 环境变量文档：https://nextjs.org/docs/app/building-your-application/configuring/environment-variables
- 项目 README：`../README.md`
