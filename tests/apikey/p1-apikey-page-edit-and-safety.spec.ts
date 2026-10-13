import { expect, test } from '@playwright/test'
import {
  firstPageItems,
  fulfillJson,
  gotoApiKeyPage,
  pagePayload,
  renamedFirstPageItems,
  setupCommonMocks,
  type ApiKeyItem,
} from './apikey-page.helpers'

test.describe('apikey page - edit and safety', () => {
  test('edits api key name and refreshes list', async ({ page }) => {
    const renamePayloads: any[] = []
    let renamed = false

    await setupCommonMocks(page, {
      pageHandler: () => (renamed ? pagePayload(renamedFirstPageItems, false) : pagePayload(firstPageItems, false)),
    })

    await page.route('**/console/apikey/rename', async (route) => {
      renamePayloads.push(route.request().postDataJSON())
      renamed = true
      await fulfillJson(route, true)
    })

    await gotoApiKeyPage(page)

    await page.getByRole('row', { name: /主密钥/ }).getByRole('button').nth(0).click()
    await expect(page.getByRole('heading', { name: '修改名称' })).toBeVisible()

    const input = page.getByPlaceholder('请输入名称')
    await input.fill('已更新名称')
    await input.press('Enter')

    await expect(page.getByRole('heading', { name: '修改名称' })).not.toBeVisible()
    await expect(page.getByText('已更新名称')).toBeVisible()
    expect(renamePayloads).toEqual([{ code: 'ak-code-001', name: '已更新名称' }])
  })

  test('edits service name and sends bindService request', async ({ page }) => {
    const bindPayloads: any[] = []
    const updatedItems: ApiKeyItem[] = [{ ...firstPageItems[0], serviceId: 'svc-new' }, firstPageItems[1]]
    let updated = false

    await setupCommonMocks(page, {
      pageHandler: () => (updated ? pagePayload(updatedItems, false) : pagePayload(firstPageItems, false)),
    })

    await page.route('**/console/apikey/bindService', async (route) => {
      bindPayloads.push(route.request().postDataJSON())
      updated = true
      await fulfillJson(route, true)
    })

    await gotoApiKeyPage(page)

    await page.getByRole('row', { name: /主密钥/ }).getByRole('button').nth(1).click()
    await expect(page.getByRole('heading', { name: '修改服务名' })).toBeVisible()

    const input = page.getByPlaceholder('请输入服务名')
    await input.fill('svc-new')
    await page.getByRole('button', { name: '确认' }).click()

    await expect(page.getByRole('heading', { name: '修改服务名' })).not.toBeVisible()
    await expect(page.getByText('svc-new')).toBeVisible()
    expect(bindPayloads).toEqual([{ code: 'ak-code-001', serviceId: 'svc-new' }])
  })

  test('opens safe level dialog with expected content', async ({ page }) => {
    await setupCommonMocks(page)
    await gotoApiKeyPage(page)

    await page.getByTitle('编辑安全等级').first().click()

    await expect(page.getByRole('heading', { name: '安全认证' })).toBeVisible()
    await expect(page.getByText('安全合规申请')).toBeVisible()
    await expect(page.getByPlaceholder('输入新的安全认证码')).toBeVisible()
  })

  test('validates certify code is required before submit', async ({ page }) => {
    const certifyRequests: any[] = []

    await setupCommonMocks(page)
    await page.route('**/console/apikey/certify', async (route) => {
      certifyRequests.push(route.request().postDataJSON())
      await fulfillJson(route, true)
    })

    await gotoApiKeyPage(page)
    await page.getByTitle('编辑安全等级').first().click()
    await expect(page.getByRole('heading', { name: '安全认证' })).toBeVisible()

    await page.getByRole('button', { name: '确认' }).click()

    await expect(page.getByText('请输入安全认证码')).toBeVisible()
    expect(certifyRequests).toHaveLength(0)
  })

  test('submits certify code and refreshes list', async ({ page }) => {
    const certifyPayloads: any[] = []
    let certified = false

    await setupCommonMocks(page, {
      pageHandler: () =>
        pagePayload(
          certified ? [{ ...firstPageItems[0], safetyLevel: 40 }, firstPageItems[1]] : firstPageItems,
          false
        ),
    })

    await page.route('**/console/apikey/certify', async (route) => {
      certifyPayloads.push(route.request().postDataJSON())
      certified = true
      await fulfillJson(route, true)
    })

    await gotoApiKeyPage(page)
    await page.getByTitle('编辑安全等级').first().click()
    await expect(page.getByRole('heading', { name: '安全认证' })).toBeVisible()

    await page.getByPlaceholder('输入新的安全认证码').fill('CERT-12345')
    await page.getByRole('button', { name: '确认' }).click()

    await expect(page.getByText('安全认证码修改成功')).toBeVisible()
    await expect(page.getByRole('heading', { name: '安全认证' })).not.toBeVisible()

    expect(certifyPayloads).toEqual([{ certifyCode: 'CERT-12345', code: 'ak-code-001' }])
  })
})
