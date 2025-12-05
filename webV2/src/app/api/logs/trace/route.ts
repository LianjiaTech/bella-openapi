import {NextRequest, NextResponse} from 'next/server';
import {
  workflow_apikey,
  workflow_url,
  tenant_id,
  logs_trace_workflow_id,
  isLogsTraceConfigComplete
} from "@/app/api/config";
import {callWorkflow} from '@/lib/api/workflow';

export const dynamic = 'force-dynamic';

const WORKFLOW_API_URL = workflow_url;
const API_KEY = workflow_apikey;
const TENANT_ID = tenant_id;
const WORKFLOW_ID = logs_trace_workflow_id;

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

        return NextResponse.json(workflowResponse.data.outputs.body || []);
    } catch (error) {
        console.error('Error fetching logs:', error);
        return NextResponse.json(
            { error: error instanceof Error ? error.message : 'Unknown error' },
            { status: 500 }
        );
    }
}
