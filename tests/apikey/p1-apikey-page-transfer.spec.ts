import { expect, test } from '@playwright/test'
import { fulfillJson, gotoApiKeyPage, setupCommonMocks } from './apikey-page.helpers'

test.describe('apikey page - transfer', () => {
  test('opens transfer dialog with default search state', async ({ page }) => {
    await setupCommonMocks(page)
    await gotoApiKeyPage(page)

    await page.getByRole('row', { name: /主密钥/ }).getByRole('button').last().click()
    await page.getByText('转交').click()

    await expect(page.getByRole('heading', { name: '转交API Key - 搜索用户' })).toBeVisible()
    await expect(page.getByText('请输入关键词开始搜索')).toBeVisible()
    await expect(page.getByText('查看转交历史')).toBeVisible()
  })

  test('searches users in transfer dialog', async ({ page }) => {
    const mockUsers = [{ id: 101, userName: 'Alice Zhang', email: 'alice@example.com', sourceId: 'u101' }]

    await setupCommonMocks(page)
    await page.route('**/v1/userInfo/search**', async (route) => {
      await fulfillJson(route, mockUsers)
    })

    await gotoApiKeyPage(page)

    await page.getByRole('row', { name: /主密钥/ }).getByRole('button').last().click()
    await page.getByText('转交').click()
    await expect(page.getByRole('heading', { name: '转交API Key - 搜索用户' })).toBeVisible()

    await page.getByPlaceholder('输入用户名、邮箱或ID').fill('alice')
    await expect(page.getByText('输入中...')).toBeVisible()

    await page.waitForTimeout(650)
    await expect(page.getByText('Alice Zhang')).toBeVisible()
    await expect(page.getByText('alice@example.com')).toBeVisible()
  })

  test('shows no result state in transfer user search', async ({ page }) => {
    await setupCommonMocks(page)
    await page.route('**/v1/userInfo/search**', async (route) => {
      await fulfillJson(route, [])
    })

    await gotoApiKeyPage(page)

    await page.getByRole('row', { name: /主密钥/ }).getByRole('button').last().click()
    await page.getByText('转交').click()

    await page.getByPlaceholder('输入用户名、邮箱或ID').fill('nobody')
    await page.waitForTimeout(650)

    await expect(page.getByText('未找到匹配的用户')).toBeVisible()
  })

  test('requires transfer reason before confirming transfer', async ({ page }) => {
    const transferRequests: any[] = []
    const mockUsers = [{ id: 102, userName: 'Bob Li', email: 'bob@example.com', sourceId: 'u102' }]

    await setupCommonMocks(page)
    await page.route('**/v1/userInfo/search**', async (route) => {
      await fulfillJson(route, mockUsers)
    })
    await page.route('**/console/apikey/owner/transfer', async (route) => {
      transferRequests.push(route.request().postDataJSON())
      await fulfillJson(route, true)
    })

    await gotoApiKeyPage(page)

    await page.getByRole('row', { name: /主密钥/ }).getByRole('button').last().click()
    await page.getByText('转交').click()

    await page.getByPlaceholder('输入用户名、邮箱或ID').fill('bob')
    await page.waitForTimeout(650)
    await expect(page.getByText('Bob Li')).toBeVisible()
    await page.getByText('确认转交').first().click()

    await expect(page.getByRole('heading', { name: '转交API Key' })).toBeVisible()
    await page.getByRole('button', { name: '确认转交' }).click()

    await expect(page.getByText('请输入转交原因')).toBeVisible()
    expect(transferRequests).toHaveLength(0)
  })

  test('transfers api key successfully', async ({ page }) => {
    const transferPayloads: any[] = []
    const mockUsers = [{ id: 103, userName: 'Carol Wu', email: 'carol@example.com', sourceId: 'u103' }]

    await setupCommonMocks(page)
    await page.route('**/v1/userInfo/search**', async (route) => {
      await fulfillJson(route, mockUsers)
    })
    await page.route('**/console/apikey/owner/transfer', async (route) => {
      transferPayloads.push(route.request().postDataJSON())
      await fulfillJson(route, true)
    })

    await gotoApiKeyPage(page)

    await page.getByRole('row', { name: /主密钥/ }).getByRole('button').last().click()
    await page.getByText('转交').click()

    await page.getByPlaceholder('输入用户名、邮箱或ID').fill('carol')
    await page.waitForTimeout(650)
    await expect(page.getByText('Carol Wu')).toBeVisible()
    await page.getByText('确认转交').first().click()

    await page.getByPlaceholder('请输入转交原因 (必填)').fill('岗位调整，移交给新负责人')
    await page.getByRole('button', { name: '确认转交' }).click()

    await expect(page.getByRole('heading', { name: '转交API Key - 搜索用户' })).not.toBeVisible()
    await expect(page.getByRole('heading', { name: '转交API Key' })).not.toBeVisible()

    expect(transferPayloads).toEqual([
      {
        akCode: 'ak-code-001',
        targetUserId: 103,
        transferReason: '岗位调整，移交给新负责人',
      },
    ])
  })

  test('shows transfer history placeholder and can navigate back', async ({ page }) => {
    await setupCommonMocks(page)
    await gotoApiKeyPage(page)

    await page.getByRole('row', { name: /主密钥/ }).getByRole('button').last().click()
    await page.getByText('转交').click()
    await expect(page.getByRole('heading', { name: '转交API Key - 搜索用户' })).toBeVisible()

    await page.getByText('查看转交历史').click()

    await expect(page.getByRole('heading', { name: '转交历史' })).toBeVisible()
    await expect(page.getByText('暂无转交历史记录')).toBeVisible()

    await page.getByRole('button').filter({ has: page.locator('svg.lucide-arrow-left') }).click()
    await expect(page.getByRole('heading', { name: '转交API Key - 搜索用户' })).toBeVisible()
  })
})
