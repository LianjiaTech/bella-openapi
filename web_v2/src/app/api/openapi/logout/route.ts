import { NextResponse } from 'next/server';
import { NextRequest } from 'next/server';

/**
 * API: 登出
 * POST /api/openapi/logout
 *
 * 用途:
 * - 清除用户 Session
 * - 删除 Cookie
 *
 * 返回:
 * - 成功: 200 OK
 *
 * Mock模式:
 * - 设置 NEXT_PUBLIC_USE_MOCK=true 启用
 * - 清除 BELLA-SESSION Cookie
 */
export async function POST(request: NextRequest) {
  // Mock 模式
  const useMock = process.env.NEXT_PUBLIC_USE_MOCK === 'true';

  if (useMock) {
    console.log('[Mock] POST /api/openapi/logout');

    // 模拟网络延迟
    await new Promise(resolve => setTimeout(resolve, 200));

    // 清除 Cookie
    const response = NextResponse.json(
      {
        code: 200,
        message: '登出成功',
        data: null,
      },
      { status: 200 }
    );

    // 删除 Session Cookie
    response.cookies.set('BELLA-SESSION', '', {
      httpOnly: true,
      secure: false,
      sameSite: 'lax',
      maxAge: 0, // 立即过期
      path: '/',
    });

    return response;
  }

  // 真实后端模式
  try {
    // 构造后端 API URL
    const backendHost = process.env.NEXT_PUBLIC_API_HOST || 'localhost:8080';
    const backendUrl = `http://${backendHost}/openapi/logout`;

    // 转发 Cookie
    const cookie = request.headers.get('cookie') || '';

    const response = await fetch(backendUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-BELLA-CONSOLE': 'true',
        'Cookie': cookie,
      },
      credentials: 'include',
    });

    const data = await response.json();

    // 转发 Set-Cookie 头（后端会清除 Cookie）
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
    console.error('[Backend POST /openapi/logout Error]', error);
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
