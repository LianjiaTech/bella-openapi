import { NextResponse } from 'next/server';
import { NextRequest } from 'next/server';
import { getUserInfoByScenario, currentScenario } from '@/mocks/login/authData';

/**
 * API: 获取当前用户信息
 * GET /api/console/userInfo
 *
 * 用途:
 * - 应用初始化时检查登录状态
 * - 刷新用户信息
 *
 * 返回:
 * - 已登录: 返回用户信息 { userId, username, email, ... }
 * - 未登录: 返回 401
 *
 * Mock模式:
 * - 设置 NEXT_PUBLIC_USE_MOCK=true 启用
 * - 检查Cookie中是否有BELLA-SESSION来判断登录状态
 */
export async function GET(request: NextRequest) {
  // Mock 模式
  const useMock = process.env.NEXT_PUBLIC_USE_MOCK === 'true';

  if (useMock) {
    console.log('[Mock] GET /api/console/userInfo - Scenario:', currentScenario);

    // 模拟网络延迟
    await new Promise(resolve => setTimeout(resolve, 200));

    // 检查是否有 Session Cookie
    const sessionCookie = request.cookies.get('BELLA-SESSION');
    const isLoggedIn = !!sessionCookie;

    console.log('[Mock] Session Cookie:', sessionCookie?.value, 'Logged in:', isLoggedIn);

    if (!isLoggedIn) {
      // 未登录：返回 401
      return NextResponse.json(
        {
          code: 401,
          message: '未登录',
          data: null,
        },
        { status: 401 }
      );
    }

    // 已登录：返回用户信息
    const userInfo = getUserInfoByScenario(currentScenario, true);

    return NextResponse.json(
      {
        code: 200,
        message: 'success',
        data: userInfo,
      },
      { status: 200 }
    );
  }

  // 真实后端模式
  try {
    // 构造后端 API URL
    const backendHost = process.env.NEXT_PUBLIC_API_HOST || 'localhost:8080';
    const backendUrl = `http://${backendHost}/console/userInfo`;

    // 转发 Cookie（用于会话认证）
    const cookie = request.headers.get('cookie') || '';

    const response = await fetch(backendUrl, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
        'X-BELLA-CONSOLE': 'true',
        'Cookie': cookie,
      },
      credentials: 'include',
    });

    // 检查 Content-Type，确保是 JSON 响应
    const contentType = response.headers.get('content-type');
    if (!contentType || !contentType.includes('application/json')) {
      console.error('[Backend GET /console/userInfo] Non-JSON response:', {
        status: response.status,
        contentType,
      });

      // 非 JSON 响应（可能是 HTML 错误页面或重定向）
      return NextResponse.json(
        {
          code: response.status,
          message: `后端返回非 JSON 响应 (${response.status})`,
          data: null,
          stacktrace: null,
          timestamp: Date.now(),
        },
        { status: response.status }
      );
    }

    // 解析 JSON 响应
    const data = await response.json();

    // 转发 Set-Cookie 头（如果后端更新了 Cookie）
    const setCookie = response.headers.get('set-cookie');
    const headers: HeadersInit = {
      'Content-Type': 'application/json',
    };
    if (setCookie) {
      headers['Set-Cookie'] = setCookie;
    }

    return NextResponse.json(data, {
      status: response.status,
      headers,
    });
  } catch (error) {
    console.error('[Backend GET /console/userInfo Error]', error);
    return NextResponse.json(
      {
        code: 500,
        message: '后端 API 调用失败',
        data: null,
        stacktrace: null,
        timestamp: Date.now(),
      },
      { status: 500 }
    );
  }
}
