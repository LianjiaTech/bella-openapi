import { NextResponse } from 'next/server';

// 强制动态渲染
export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export async function GET() {
  const clientId = process.env.GITHUB_CLIENT_ID;
  
  if (!clientId) {
    return NextResponse.json({ error: 'GitHub client ID not configured' }, { status: 500 });
  }
  
  return NextResponse.json({ clientId });
}