interface WorkflowResponse {
  data: {
    tenantId: string;
    workflowId: string;
    workflowRunId: string;
    status: string;
    error?: string;
    outputs: Record<string, any>;
  };
  message?: string;
}

export async function callWorkflow(
  url: string,
  apiKey: string,
  tenantId: string,
  workflowId: string,
  inputs: Record<string, any>
) {
  const response = await fetch(url, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${apiKey}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      tenantId,
      workflowId,
      inputs,
      responseMode: "blocking"
    }),
  });
  const data = await response.json() as WorkflowResponse;

  if (!response.ok) {
    throw new Error(data.message || 'Failed to fetch workflow data');
  }

  if(data.data.error) {
    throw new Error(data.data.error);
  }
  return data;
}
