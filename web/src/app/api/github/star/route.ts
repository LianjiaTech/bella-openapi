import { NextRequest, NextResponse } from 'next/server';

const TARGET_REPO = 'LianjiaTech/bella-openapi';

export async function GET(request: NextRequest) {
  try {
    const token = request.headers.get('authorization')?.replace('Bearer ', '');

    if (!token) {
      return NextResponse.json({ error: 'Authorization token is required' }, { status: 401 });
    }

    // Check if user has starred the repo
    const response = await fetch(`https://api.github.com/user/starred/${TARGET_REPO}`, {
      headers: {
        Authorization: `token ${token}`,
        Accept: 'application/vnd.github.v3+json',
      },
    });

    const isStarred = response.status === 204;
    return NextResponse.json({ isStarred });
  } catch (error) {
    console.error('GitHub star check error:', error);
    return NextResponse.json({ error: 'Failed to check star status' }, { status: 500 });
  }
}

export async function PUT(request: NextRequest) {
  try {
    const token = request.headers.get('authorization')?.replace('Bearer ', '');

    if (!token) {
      return NextResponse.json({ error: 'Authorization token is required' }, { status: 401 });
    }

    // Star the repository
    const response = await fetch(`https://api.github.com/user/starred/${TARGET_REPO}`, {
      method: 'PUT',
      headers: {
        Authorization: `token ${token}`,
        Accept: 'application/vnd.github.v3+json',
        'Content-Length': '0',
      },
    });

    if (response.status === 204) {
      return NextResponse.json({ success: true });
    } else {
      return NextResponse.json({ error: 'Failed to star repository' }, { status: response.status });
    }
  } catch (error) {
    console.error('GitHub star error:', error);
    return NextResponse.json({ error: 'Failed to star repository' }, { status: 500 });
  }
}