import { expect, test } from '@playwright/test'
import { fulfillJson, pagePayload, setupCommonMocks, type ApiKeyItem } from './apikey-page.helpers'

test.describe('sub-ak page - manager viewer', () => {
  test('shows delete action and deletes a child key for delegated parent manager', async ({ page }) => {
    const parentCode = 'parent-org-ak-code'
    const childCode = 'child-ak-code-001'
    const parentApiKey: ApiKeyItem = {
      code: parentCode,
      akDisplay: 'sk-parent-org',
      name: '组织父密钥',
      serviceId: 'svc-org',
      monthQuota: 100,
      safetyLevel: 30,
      remark: '组织父 AK',
      ownerType: 'org',
      ownerCode: 'org-001',
      ownerName: '组织 001',
      parentCode: '',
      managerCode: '9527',
      managerName: 'Playwright User',
    }
    const childApiKey: ApiKeyItem = {
      code: childCode,
      akDisplay: 'sk-child-001',
      name: '委托子密钥',
      serviceId: 'svc-child',
      monthQuota: 20,
      safetyLevel: 20,
      remark: '子 AK',
      outEntityCode: 'child-service',
      ownerType: 'org',
      ownerCode: 'org-001',
      ownerName: '组织 001',
      parentCode,
      managerCode: '9527',
      managerName: 'Playwright User',
    }

    let deleted = false
    const inactivatePayloads: unknown[] = []

    await setupCommonMocks(page, {
      pageHandler: () => pagePayload(deleted ? [] : [childApiKey], false),
    })
    await page.route('**/console/apikey/fetchByCode**', async (route) => {
      await fulfillJson(route, parentApiKey)
    })
    await page.route('**/console/apikey/inactivate', async (route) => {
      inactivatePayloads.push(route.request().postDataJSON())
      deleted = true
      await fulfillJson(route, true)
    })

    await page.goto(`/apikey/sub-ak/${parentCode}?viewer=manager`)

    await expect(page.locator('h1').filter({ hasText: '子密钥管理' })).toBeVisible()
    await expect(page.getByRole('row', { name: /委托子密钥/ })).toBeVisible()

    const row = page.getByRole('row', { name: /委托子密钥/ })
    await row.getByRole('button').last().click()
    await expect(page.getByText('删除')).toBeVisible()

    await page.getByText('删除').click()
    await expect(page.getByRole('heading', { name: '删除 API Key' })).toBeVisible()
    await expect(page.getByRole('button', { name: '取消' })).toBeVisible()
    await page.getByRole('button', { name: '确认' }).click()

    await expect(page.getByRole('heading', { name: '删除 API Key' })).not.toBeVisible()
    await expect(page.getByText('暂无数据')).toBeVisible()
    expect(inactivatePayloads).toEqual([{ code: childCode }])
  })
})
