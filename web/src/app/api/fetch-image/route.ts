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
        { success: false, error: `Failed to fetch image: ${response.statusText}` },
        { status: response.status }
      );
    }

    const contentType = response.headers.get('content-type');
    if (!contentType || !contentType.startsWith('image/')) {
      return NextResponse.json(
        { success: false, error: 'URL does not point to a valid image' },
        { status: 400 }
      );
    }

    const arrayBuffer = await response.arrayBuffer();
    const buffer = Buffer.from(arrayBuffer);
    const base64Image = `data:${contentType};base64,${buffer.toString('base64')}`;

    return NextResponse.json({
      success: true,
      imageData: base64Image
    });
  } catch (error) {
    console.error('Error fetching image:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to fetch image' },
      { status: 500 }
    );
  }
}