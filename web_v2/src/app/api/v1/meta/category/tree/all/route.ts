import { NextResponse } from 'next/server';
import categoryTreeData from '@/mocks/data/categoryTree.json';

/**
 * API: 获取所有分类树
 * GET /api/v1/meta/category/tree/all
 *
 * 环境变量控制:
 * - NEXT_PUBLIC_USE_MOCK=true: 使用 Mock 数据
 * - NEXT_PUBLIC_USE_MOCK=false: 调用真实后端 API
 */
export async function GET() {
  const useMock = process.env.NEXT_PUBLIC_USE_MOCK === 'true';

  if (useMock) {
    // 返回 Mock 数据
    const mockResponse = {
      code: 200,
      message: null,
      timestamp: Date.now(),
      data: categoryTreeData,
      stacktrace: null,
    };

    return NextResponse.json(mockResponse);
  }

  // TODO: 调用真实后端 API
  // 示例实现:
  // try {
  //   const backendUrl = process.env.BACKEND_API_URL || 'http://localhost:8080';
  //   const response = await fetch(`${backendUrl}/api/v1/meta/category/tree/all`);
  //   const data = await response.json();
  //   return NextResponse.json(data);
  // } catch (error) {
  //   return NextResponse.json(
  //     { code: 500, message: 'Backend API error', data: null },
  //     { status: 500 }
  //   );
  // }

  // 当前未实现真实 API 调用，返回错误提示
  return NextResponse.json(
    {
      code: 501,
      message: 'Real API not implemented. Please set NEXT_PUBLIC_USE_MOCK=true to use mock data.',
      data: null,
      stacktrace: null,
    },
    { status: 501 }
  );
}
