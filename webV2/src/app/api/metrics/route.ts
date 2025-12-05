import {NextRequest, NextResponse} from 'next/server';
import {
  workflow_apikey,
  workflow_url,
  tenant_id,
  metrics_workflow_id,
  isMetricsConfigComplete
} from "@/app/api/config";
import {callWorkflow} from '@/lib/api/workflow';

export const dynamic = 'force-dynamic';

const WORKFLOW_API_URL = workflow_url;
const API_KEY = workflow_apikey;
const TENANT_ID = tenant_id;
const WORKFLOW_ID = metrics_workflow_id;

export async function GET(request: NextRequest) {
  // 检查配置是否完整
  if (!isMetricsConfigComplete()) {
    console.error('监控功能配置不完整，请检查环境变量');
    return NextResponse.json(
      { error: '功能暂未开放' },
      { status: 503 }
    );
  }

  try {
    const searchParams = new URL(request.url).searchParams;
    const model = searchParams.get('model');
    const endpoint = searchParams.get('endpoint');
    const start = searchParams.get('start');
    const end = searchParams.get('end');

    if (!model || !endpoint || !start || !end) {
      return NextResponse.json(
        { error: 'Missing required parameters' },
        { status: 400 }
      );
    }

    const inputs = {
      code: model,
      endpoint: endpoint,
      startTime: start,
      endTime: end
    };

    const workflowResponse = await callWorkflow(
      WORKFLOW_API_URL || '',
      API_KEY || '',
      TENANT_ID || '',
      WORKFLOW_ID || '',
      inputs
    );

    // 处理返回的数据，将 metrics 字符串转换为对象
    const processedData = workflowResponse.data.outputs.result.list.map((item: any) => ({
      ...item,
      metrics: JSON.parse(item.metrics)
    }));

    return NextResponse.json(processedData);
  } catch (error) {
    console.error('Error fetching metrics:', error);
    return NextResponse.json(
      { error: error instanceof Error ? error.message : 'Unknown error' },
      { status: 500 }
    );
  }
}
