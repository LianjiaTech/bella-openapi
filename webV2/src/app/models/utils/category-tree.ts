import { CategoryTree } from "@/lib/types/openapi"

/**
 * 扁平化后的 Endpoint 数据结构
 */
export interface EndpointWithCategory {
  categoryCode: string
  categoryName: string
  endpoint: string
  endpointCode: string
  endpointName: string
  ctime: string
  cuName: string
  mtime: string
  muName: string
  status: string
}

/**
 * 递归遍历分类树节点
 */
const traverseTree = (tree: CategoryTree, result: EndpointWithCategory[]): void => {
  // 处理当前节点的 endpoints
  if (tree.endpoints && tree.endpoints.length > 0) {
    tree.endpoints.forEach((endpoint) => {
      result.push({
        categoryCode: tree.categoryCode,
        categoryName: tree.categoryName,
        endpoint: endpoint.endpoint,
        endpointCode: endpoint.endpointCode,
        endpointName: endpoint.endpointName,
        ctime: endpoint.ctime,
        cuName: endpoint.cuName,
        mtime: endpoint.mtime,
        muName: endpoint.muName,
        status: endpoint.status,
      })
    })
  }

  // 递归处理子节点
  if (tree.children && tree.children.length > 0) {
    tree.children.forEach((child) => traverseTree(child, result))
  }
}

/**
 * 扁平化 CategoryTree 结构,按 endpoint 组织数据
 * 将树形结构转换为扁平数组,便于遍历和展示
 */
export const flattenCategoryTrees = (trees: CategoryTree[]): EndpointWithCategory[] => {
  const result: EndpointWithCategory[] = []

  trees.forEach((tree) => traverseTree(tree, result))

  return result
}
