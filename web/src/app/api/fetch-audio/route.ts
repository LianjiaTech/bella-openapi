import { NextRequest, NextResponse } from 'next/server';

export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    const { url } = body;

    if (!url) {
      return NextResponse.json(
        { success: false, error: 'URL is required' },
        { status: 400 }
      );
    }

    const response = await fetch(url);

    if (!response.ok) {
      return NextResponse.json(
        { success: false, error: `Failed to fetch audio: ${response.statusText}` },
        { status: response.status }
      );
    }

    const contentType = response.headers.get('content-type');
    if (!contentType || !contentType.startsWith('audio/')) {
      return NextResponse.json(
        { success: false, error: 'URL does not point to a valid audio file' },
        { status: 400 }
      );
    }

    const arrayBuffer = await response.arrayBuffer();
    const buffer = Buffer.from(arrayBuffer);
    const base64Audio = `data:${contentType};base64,${buffer.toString('base64')}`;

    return NextResponse.json({
      success: true,
      audioData: base64Audio
    });
  } catch (error) {
    console.error('Error fetching audio:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to fetch audio' },
      { status: 500 }
    );
  }
}
