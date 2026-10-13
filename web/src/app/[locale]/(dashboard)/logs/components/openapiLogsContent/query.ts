import { QUERY_TYPE_TO_FIELD_MAP } from "../common/QueryTypeSelector/constants"
import type { QueryType } from "../common/QueryTypeSelector/types"

const AK_CODE_BATCH_EXCLUDE_CONDITION = "(NOT data_info_msg_batch:true)"

// 智能问答对应的多 endpoint 分组
const CHAT_ENDPOINT_GROUP: Record<string, string[]> = {
  "/v1/chat/completions": ["/v1/chat/completions", "/v1/messages", "/v1/responses"],
}

export interface BuildOpenapiLogsQueryParams {
  queryType: QueryType
  queryValue: string
  httpCode?: string
  endpointCode?: string
  modelName?: string
}

export function buildOpenapiLogsQuery({
  queryType,
  queryValue,
  httpCode,
  endpointCode,
  modelName,
}: BuildOpenapiLogsQueryParams): string {
  const fieldName = QUERY_TYPE_TO_FIELD_MAP[queryType]
  const conditions: string[] = [`${fieldName}:"${queryValue}"`]

  if (queryType === "AK Code") {
    conditions.push(AK_CODE_BATCH_EXCLUDE_CONDITION)
  }

  // 添加 HTTP 状态码筛选
  if (httpCode && httpCode.trim()) {
    conditions.push(`data_info_msg_response: (\"\\\"httpCode\\\"\\: ${httpCode.trim()}\")`)
  }

  // 添加能力点筛选
  if (endpointCode && endpointCode.trim()) {
    const groupedEndpoints = CHAT_ENDPOINT_GROUP[endpointCode.trim()]
    if (groupedEndpoints) {
      const orCondition = groupedEndpoints
        .map(ep => `data_info_msg_endpoint:"${ep}"`)
        .join(' OR ')
      conditions.push(`(${orCondition})`)
    } else {
      conditions.push(`data_info_msg_endpoint:"${endpointCode.trim()}"`)
    }
  }

  // 添加模型筛选
  if (modelName && modelName.trim()) {
    conditions.push(`data_info_msg_model.keyword:"${modelName.trim()}"`)
  }

  return conditions.join(' AND ')
}
