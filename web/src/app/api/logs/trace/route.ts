import {NextRequest, NextResponse} from 'next/server';
import {
  workflow_apikey,
  workflow_url,
  tenant_id,
  logs_trace_workflow_id,
  isLogsTraceConfigComplete
} from "@/app/api/config";
import {callWorkflow} from '@/lib/api/workflow';
import {gunzipSync} from 'zlib';

export const dynamic = 'force-dynamic';

const WORKFLOW_API_URL = workflow_url;
const API_KEY = workflow_apikey;
const TENANT_ID = tenant_id;
const WORKFLOW_ID = logs_trace_workflow_id;

function decompressField(value: string): string {
    try {
        const buffer = Buffer.from(value, 'base64');
        const decompressed = gunzipSync(buffer);
        return decompressed.toString('utf-8');
    } catch {
        return value;
    }
}

function decompressTraceLogFields(logs: Record<string, any>[]): Record<string, any>[] {
    return logs.map(log => {
        if (log.data_info_msg_requestCompressed === true || log.data_info_msg_requestCompressed === 'true') {
            if (typeof log.data_info_msg_request === 'string') {
                log.data_info_msg_request = decompressField(log.data_info_msg_request);
            }
        }
        if (log.data_info_msg_responseCompressed === true || log.data_info_msg_responseCompressed === 'true') {
            if (typeof log.data_info_msg_response === 'string') {
                log.data_info_msg_response = decompressField(log.data_info_msg_response);
            } else if (typeof log.data_info_msg_responseRaw === 'string') {
                log.data_info_msg_response = decompressField(log.data_info_msg_responseRaw);
            }
        }
        return log;
    });
}

export async function GET(request: NextRequest) {
    // 检查配置是否完整
    if (!isLogsTraceConfigComplete()) {
        console.error('日志跟踪功能配置不完整，请检查环境变量');
        return NextResponse.json(
            { error: '功能暂未开放' },
            { status: 503 }
        );
    }

    try {
        const searchParams = new URL(request.url).searchParams;
        const serviceId = searchParams.get('serviceId');
        const traceId = searchParams.get('traceId');
        const akCode = searchParams.get('akCode');
        const start = searchParams.get('start');
        const end = searchParams.get('end');

        if (!serviceId || !start || !end || (!traceId && !akCode)) {
            return NextResponse.json(
                { error: 'Missing required parameters' },
                { status: 400 }
            );
        }

        const inputs = {
            serviceId: serviceId,
            traceId: traceId,
            akCode: akCode,
            start: start,
            end: end
        };

        const workflowResponse = await callWorkflow(
            WORKFLOW_API_URL || '',
            API_KEY || '',
            TENANT_ID || '',
            WORKFLOW_ID || '',
            inputs
        );

        const body = workflowResponse.data.outputs.body || [];
        return NextResponse.json(decompressTraceLogFields(body));
    } catch (error) {
        console.error('Error fetching logs:', error);
        return NextResponse.json(
            { error: error instanceof Error ? error.message : 'Unknown error' },
            { status: 500 }
        );
    }
}
