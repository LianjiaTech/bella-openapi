// Server-side only configuration
export const workflow_url = process.env.WORKFLOW_URL;
export const workflow_apikey = process.env.WORKFLOW_API_KEY;
export const es_url = process.env.ES_URL;
export const es_apikey = process.env.ES_API_KEY;

// 租户ID
export const tenant_id = process.env.TENANT_ID;
// 工作流ID - 监控
export const metrics_workflow_id = process.env.METRICS_WORKFLOW_ID;
// 工作流ID - 日志跟踪
export const logs_trace_workflow_id = process.env.LOGS_TRACE_WORKFLOW_ID;
// 工作流ID - 服务列表
export const service_workflow_id = process.env.SERVICE_WORKFLOW_ID;

// 检查配置是否完整
export const isWorkflowConfigComplete = () => {
  return !!(workflow_url && workflow_apikey && tenant_id);
};

// 检查特定功能的配置是否完整
export const isMetricsConfigComplete = () => {
  return !!(isWorkflowConfigComplete() && metrics_workflow_id);
};

export const isLogsTraceConfigComplete = () => {
  return !!(isWorkflowConfigComplete() && logs_trace_workflow_id);
};

export const isServiceConfigComplete = () => {
  return !!(isWorkflowConfigComplete() && service_workflow_id);
};

// 检查配置是否完整
export const isEsConfigComplete = () => {
  return !!(es_url && es_apikey);
};