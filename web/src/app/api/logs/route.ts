import {NextResponse} from 'next/server';
import {es_apikey, es_url, isEsConfigComplete} from "../config";
import {gunzipSync} from 'zlib';

const API_KEY = es_apikey;
const API_URL = es_url;

function decompressField(value: string): string {
  try {
    const buffer = Buffer.from(value, 'base64');
    const decompressed = gunzipSync(buffer);
    return decompressed.toString('utf-8');
  } catch {
    return value;
  }
}

function decompressLogFields(hit: Record<string, unknown>): Record<string, unknown> {
  if (hit.data_info_msg_requestCompressed === true || hit.data_info_msg_requestCompressed === 'true') {
    const request = hit.data_info_msg_request;
    if (typeof request === 'string') {
      hit.data_info_msg_request = decompressField(request);
    }
  }
  if (hit.data_info_msg_responseCompressed === true || hit.data_info_msg_responseCompressed === 'true') {
    const response = hit.data_info_msg_response;
    if (typeof response === 'string') {
      hit.data_info_msg_response = decompressField(response);
    } else if (!response) {
      // responseCompressed uses responseRaw field
      const responseRaw = hit.data_info_msg_responseRaw;
      if (typeof responseRaw === 'string') {
        hit.data_info_msg_response = decompressField(responseRaw);
      }
    }
  }
  return hit;
}

export async function POST(request: Request) {
  if(!isEsConfigComplete()) {
    console.error('日志功能配置不完整，请检查环境变量');
    return NextResponse.json(
      { error: '功能暂未开放' },
      { status: 503 }
    );
  }
  try {
    const body = await request.json();

    const response = await fetch(API_URL || '', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'api_key': API_KEY || '',
      },
      body: JSON.stringify({
        stime: body.startTime,
        etime: body.endTime,
        fields: [
          "data_info_msg_akCode",
          "data_info_msg_forwardUrl",
          "data_info_msg_model",
          "data_info_msg_request",
          "data_info_msg_response",
          "data_info_msg_responseRaw",
          "data_info_msg_requestCompressed",
          "data_info_msg_responseCompressed",
          "data_info_msg_endpoint",
          "data_info_msg_requestId",
          "data_info_msg_bellaTraceId",
          "data_info_msg_requestTime",
          "data_info_msg_user",
          "data_info_msg_metrics"
        ],
        index: "index-14812-15368",
        queryString: body.query,
        size: body.limit || 100
      })
    });

    const data = await response.json();

    // Decompress gzip+base64 fields if marked as compressed
    if (data && Array.isArray(data.data)) {
      data.data = data.data.map((hit: Record<string, unknown>) => decompressLogFields(hit));
    } else if (data && data.hits && Array.isArray(data.hits.hits)) {
      data.hits.hits = data.hits.hits.map((hit: { _source: Record<string, unknown> }) => {
        if (hit._source) {
          hit._source = decompressLogFields(hit._source);
        }
        return hit;
      });
    }

    return NextResponse.json(data);
  } catch (error) {
    return NextResponse.json({ error: 'Failed to fetch logs' }, { status: 500 });
  }
}
