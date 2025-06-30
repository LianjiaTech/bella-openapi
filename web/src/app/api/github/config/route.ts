import { NextResponse } from 'next/server';

export async function GET() {
  const clientId = process.env.GITHUB_CLIENT_ID;
  
  if (!clientId) {
    return NextResponse.json({ error: 'GitHub client ID not configured' }, { status: 500 });
  }
  
  return NextResponse.json({ clientId });
}