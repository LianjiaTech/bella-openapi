import { expect, type Page, type Route } from '@playwright/test'

export type ApiKeyItem = {
  code: string
  akDisplay: string
  name: string
  serviceId: string
  monthQuota: number
  safetyLevel: number
  remark: string
}

export type PageResponse = {
  data: ApiKeyItem[]
  has_more: boolean
}

export const mockUser = {
  userId: 9527,
  userName: 'Playwright User',
  email: 'playwright@example.com',
  tenantId: null,
  spaceCode: 'space-playwright',
  source: 'cas',
  sourceId: '9527',
  managerAk: 'manager-ak',
  optionalInfo: {},
}

export const firstPageItems: ApiKeyItem[] = [
  {
    code: 'ak-code-001',
    akDisplay: 'sk-live-001',
    name: '主密钥',
    serviceId: 'svc-core',
    monthQuota: 50,
    safetyLevel: 20,
    remark: '主环境',
  },
  {
    code: 'ak-code-002',
    akDisplay: 'sk-live-002',
    name: '备用密钥',
    serviceId: 'svc-backup',
    monthQuota: 80,
    safetyLevel: 30,
    remark: '备份环境',
  },
]

export const renamedFirstPageItems: ApiKeyItem[] = [
  {
    ...firstPageItems[0],
    name: '已更新名称',
  },
  firstPageItems[1],
]

export const balances = {
  'ak-code-001': { akCode: 'ak-code-001', month: '2026-04', cost: 12, quota: 50, balance: 38 },
  'ak-code-002': { akCode: 'ak-code-002', month: '2026-04', cost: 7, quota: 80, balance: 73 },
}

export function pagePayload(items: ApiKeyItem[], hasMore = false): PageResponse {
  return { data: items, has_more: hasMore }
}

export async function fulfillJson(route: Route, data: unknown) {
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ code: 200, data }),
  })
}

export async function setupCommonMocks(
  page: Page,
  options?: {
    pageHandler?: (searchParam: string, pageNumber: number) => PageResponse
    applyKey?: string
    resetKey?: string
  }
) {
  const pageHandler =
    options?.pageHandler ??
    ((searchParam: string, pageNumber: number) => {
      if (searchParam === 'missing') {
        return pagePayload([], false)
      }

      if (pageNumber === 2) {
        return pagePayload(
          [
            {
              code: 'ak-code-003',
              akDisplay: 'sk-live-003',
              name: '第二页密钥',
              serviceId: 'svc-page-2',
              monthQuota: 100,
              safetyLevel: 40,
              remark: '第二页',
            },
          ],
          false
        )
      }

      return pagePayload(firstPageItems, true)
    })

  await page.route('**/openapi/userInfo', async (route) => {
    await fulfillJson(route, mockUser)
  })

  await page.route('**/console/apikey/page**', async (route) => {
    const url = new URL(route.request().url())
    const searchParam = url.searchParams.get('searchParam') ?? ''
    const pageNumber = Number(url.searchParams.get('page') ?? '1')
    await fulfillJson(route, pageHandler(searchParam, pageNumber))
  })

  await page.route('**/console/apikey/balance/*', async (route) => {
    const akCode = route.request().url().split('/').pop() ?? ''
    await fulfillJson(route, balances[akCode as keyof typeof balances] ?? {
      akCode,
      month: '2026-04',
      cost: 0,
      quota: 0,
      balance: 0,
    })
  })

  await page.route('**/console/apikey/apply', async (route) => {
    await fulfillJson(route, options?.applyKey ?? 'sk-created-001')
  })

  await page.route('**/console/apikey/reset', async (route) => {
    await fulfillJson(route, options?.resetKey ?? 'sk-reset-001')
  })

  await page.route('**/console/apikey/inactivate', async (route) => {
    await fulfillJson(route, true)
  })

  await page.route('**/console/apikey/rename', async (route) => {
    await fulfillJson(route, true)
  })

  await page.route('**/console/apikey/bindService', async (route) => {
    await fulfillJson(route, true)
  })
}

export async function gotoApiKeyPage(page: Page) {
  await page.goto('/apikey')
  await expect(page.locator('h1').filter({ hasText: '我的密钥' })).toBeVisible()
}
