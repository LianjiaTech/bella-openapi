export type QueryType = "AK Code" | "Request ID" | "Bella TraceID"

export interface QueryTypeSelectorProps {
  queryType: QueryType
  queryValue: string
  error?: string
  onQueryTypeChange: (type: QueryType) => void
  onQueryValueChange: (value: string) => void
  className?: string
  availableTypes?: QueryType[]  // 可用的查询类型（可选）
}
