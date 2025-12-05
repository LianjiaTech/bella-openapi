import {NextResponse} from "next/server";
import {
  workflow_apikey,
  workflow_url,
  tenant_id,
  service_workflow_id,
  isServiceConfigComplete
} from "@/app/api/config";
import {callWorkflow} from '@/lib/api/workflow';
export const dynamic = 'force-dynamic';
export const revalidate = 0;

const WORKFLOW_API_URL = workflow_url;
const API_KEY = workflow_apikey;
const TENANT_ID = tenant_id;
const WORKFLOW_ID = service_workflow_id;

export async function GET() {
    // 检查配置是否完整
    if (!isServiceConfigComplete()) {
        console.error('服务列表功能配置不完整，请检查环境变量');
        return NextResponse.json(
            { error: '功能暂未开放' },
            { status: 503 }
        );
    }

    try {
        const inputs = {};
        const workflowResponse = await callWorkflow(
            WORKFLOW_API_URL || '',
            API_KEY || '',
            TENANT_ID || '',
            WORKFLOW_ID || '',
            inputs
        );
        return NextResponse.json(workflowResponse.data.outputs.result || []);
    } catch (error) {
        console.error('Error fetching service:', error);
        return NextResponse.json(
            { error: error instanceof Error ? error.message : 'Unknown error' },
            { status: 500 }
        );
    }
}
