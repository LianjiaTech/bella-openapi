/**
 * Common type definitions for the application
 */

/**
 * Generic API response wrapper
 */
export interface ApiResponse<T = any> {
  code: number;
  message: string;
  data: T;
  success: boolean;
}

/**
 * Pagination parameters
 */
export interface PaginationParams {
  page: number;
  pageSize: number;
}

/**
 * Paginated response data
 */
export interface PaginatedData<T> {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
  totalPages: number;
}

/**
 * Sort order
 */
export type SortOrder = "asc" | "desc";

/**
 * Sort parameters
 */
export interface SortParams {
  field: string;
  order: SortOrder;
}

/**
 * Filter operator
 */
export type FilterOperator = "eq" | "ne" | "gt" | "gte" | "lt" | "lte" | "like" | "in";

/**
 * Filter condition
 */
export interface FilterCondition {
  field: string;
  operator: FilterOperator;
  value: any;
}
