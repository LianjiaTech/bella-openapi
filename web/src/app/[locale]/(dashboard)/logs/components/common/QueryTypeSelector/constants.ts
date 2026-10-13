import { QueryType } from "./types"

// 查询类型到 API 字段名的映射
export const QUERY_TYPE_TO_FIELD_MAP: Record<QueryType, string> = {
  "AK Code": "data_info_msg_akCode",
  "Request ID": "data_info_msg_requestId",
  "Bella TraceID": "data_info_msg_bellaTraceId",
}
