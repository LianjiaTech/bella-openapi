import { expect, test } from '@playwright/test'
import { gotoApiKeyPage, setupCommonMocks } from './apikey-page.helpers'

test.describe('apikey page - row actions', () => {
  const cityOrgApiKeyQuotaDocUrl =
    'https://doc.weixin.qq.com/doc/w3_AH4AOQbyANMCNUKaCWhoeS1iwRqRw?scode=AJMA1Qc4AAww35tgpPAH4AOQbyANM'

  test('shows api key type selection options and city quota note', async ({ page }) => {
    await setupCommonMocks(page)
    await gotoApiKeyPage(page)

    await page.getByRole('button', { name: '创建新密钥' }).click()
    const typeDialog = page.getByRole('dialog').filter({ hasText: '选择 APIKey 类型' })
    await expect(typeDialog).toBeVisible()

    await expect(typeDialog.getByText('组织 APIKey')).toBeVisible()
    await expect(typeDialog.getByText('组织通用密钥，用于组织长期调用，由组织管理者统一管控整体资源配额，支持 BPM 流程提额。')).toBeVisible()
    await expect(typeDialog.getByText('城市申请组织apikey额度，需完成预算调拨流程，具体参考')).toBeVisible()
    await expect(typeDialog.getByRole('link', { name: '文档' })).toHaveAttribute(
      'href',
      cityOrgApiKeyQuotaDocUrl
    )
    await expect(typeDialog.getByText(cityOrgApiKeyQuotaDocUrl)).toHaveCount(0)
    await expect(typeDialog.getByText('项目 APIKey')).toBeVisible()
    await expect(typeDialog.getByText('项目专属密钥，绑定独立项目，生命周期与项目同步启停，支持 BPM 流程提额。')).toBeVisible()
    await expect(typeDialog.getByText('个人 APIKey')).toBeVisible()
    await expect(typeDialog.getByText('个人调试密钥，仅本地测试使用，固定 50元 基础配额，不支持提额。')).toBeVisible()
  })

  test('creates then resets then deletes the same api key in strict order', async ({ page }) => {
    await page.context().grantPermissions(['clipboard-read', 'clipboard-write'])

    const lifecycleRequests: Array<{ endpoint: 'apply' | 'reset' | 'inactivate'; payload: unknown }> = []
    page.on('request', (request) => {
      if (request.method() !== 'POST') return
      const url = request.url()
      if (!url.includes('/console/apikey/')) return

      const payload = request.postDataJSON()
      if (url.includes('/console/apikey/apply')) {
        lifecycleRequests.push({ endpoint: 'apply', payload })
      } else if (url.includes('/console/apikey/reset')) {
        lifecycleRequests.push({ endpoint: 'reset', payload })
      } else if (url.includes('/console/apikey/inactivate')) {
        lifecycleRequests.push({ endpoint: 'inactivate', payload })
      }
    })

    await gotoApiKeyPage(page)

    // 1) create
    await page.getByRole('button', { name: '创建新密钥' }).click()
    await expect(page.getByRole('dialog').filter({ hasText: '选择 APIKey 类型' })).toBeVisible()
    await page.getByRole('button', { name: /个人 APIKey/ }).click()
    const createdDialog = page.getByRole('dialog').filter({ hasText: 'API Key 创建成功' })
    await expect(createdDialog).toBeVisible()
    const createdApiKey = (await createdDialog.locator('div.font-mono').first().textContent())?.trim()
    expect(createdApiKey).toMatch(/^sk-/)
    await createdDialog.getByRole('button', { name: '确认并关闭' }).click()
    await expect(createdDialog).not.toBeVisible()

    // 锁定到刚创建的 key，确保后续操作对象一致
    const searchInput = page.getByPlaceholder('搜索 API Key...')
    await searchInput.fill(createdApiKey!)
    await page.waitForTimeout(650)
    const createdRow = page.locator('tbody tr').filter({ hasText: createdApiKey! }).first()
    await expect(createdRow).toBeVisible()

    // 先拿到 ak code，供 reset/delete 一致性校验
    await createdRow.getByRole('button').last().click()
    await page.getByText('复制ak code').click()
    await expect(page.getByText('复制成功')).toBeVisible()
    const createdAkCode = await page.evaluate(() => navigator.clipboard.readText())
    expect(createdAkCode).toBeTruthy()

    // 2) reset
    await createdRow.getByRole('button').last().click()
    await page.getByText('重置').click()
    await expect(page.getByRole('heading', { name: '重置 API Key' })).toBeVisible()
    await page.getByRole('button', { name: '确认' }).click()

    const resetDialog = page.getByRole('dialog').filter({ hasText: 'API Key 创建成功' })
    await expect(resetDialog).toBeVisible()
    const resetApiKey = (await resetDialog.locator('div.font-mono').first().textContent())?.trim()
    expect(resetApiKey).toMatch(/^sk-/)
    await resetDialog.getByRole('button', { name: '确认并关闭' }).click()
    await expect(resetDialog).not.toBeVisible()

    // 3) delete（同一个 ak code）
    await searchInput.fill(createdAkCode)
    await page.waitForTimeout(650)
    const resetRow = page.locator('tbody tr').first()
    await expect(resetRow).toBeVisible()
    await resetRow.getByRole('button').last().click()
    await page.getByText('删除').click()
    await expect(page.getByRole('heading', { name: '删除 API Key' })).toBeVisible()
    await page.getByRole('button', { name: '确认' }).click()

    await expect(async () => {
      await searchInput.fill(createdAkCode)
      await page.waitForTimeout(650)
      const rowCount = await page.locator('tbody tr').count()
      const hasEmptyState = await page.getByText('暂无数据').isVisible()
      expect(rowCount === 0 || hasEmptyState).toBe(true)
    }).toPass()

    // 顺序和对象一致性断言：必须严格 create -> reset -> delete，且 reset/delete 同一 code
    expect(lifecycleRequests.map((item) => item.endpoint)).toEqual(['apply', 'reset', 'inactivate'])
    expect((lifecycleRequests[1].payload as { code?: string }).code).toBe(createdAkCode)
    expect((lifecycleRequests[2].payload as { code?: string }).code).toBe(createdAkCode)
  })

  test('shows api key type selection and reports missing BPM apply entry', async ({ page }) => {
    test.skip(
      !!process.env.NEXT_PUBLIC_APIKEY_CREATE_APPLY_URL,
      'BPM 创建申请地址已配置，跳过未配置反馈断言'
    )

    await setupCommonMocks(page)
    await gotoApiKeyPage(page)

    await page.getByRole('button', { name: '创建新密钥' }).click()
    const typeDialog = page.getByRole('dialog').filter({ hasText: '选择 APIKey 类型' })
    await expect(typeDialog).toBeVisible()

    await typeDialog.getByRole('button', { name: /组织 APIKey/ }).click()
    await expect(page.getByText('组织 APIKey 创建申请入口未配置或地址无效')).toBeVisible()
  })

  test('shows row actions for first available api key', async ({ page }) => {
    await gotoApiKeyPage(page)
    const bodyRows = page.locator('tbody tr')
    if ((await bodyRows.count()) === 0) {
      test.skip(true, '当前账号没有 API Key 数据，跳过行操作菜单断言')
    }

    await bodyRows.first().getByRole('button').last().click()
    await expect(page.getByText('管理子AK')).toBeVisible()
    await expect(page.getByText('重置')).toBeVisible()
    await expect(page.getByText('转交')).toBeVisible()
    await expect(page.getByText('复制ak code')).toBeVisible()
    await expect(page.getByText('删除')).toBeVisible()
  })

  test('copies ak code from action menu and shows success toast', async ({ page }) => {
    await page.context().grantPermissions(['clipboard-read', 'clipboard-write'])
    await gotoApiKeyPage(page)
    await expect(page.getByText('加载中...')).not.toBeVisible()

    const bodyRows = page.locator('tbody tr')
    if ((await bodyRows.count()) === 0) {
      test.skip(true, '当前账号没有 API Key 数据，跳过复制断言')
    }

    await bodyRows.first().getByRole('button').last().click()
    await page.getByText('复制ak code').click()

    await expect(page.getByText('复制成功')).toBeVisible()
  })

  test('copies ak code from table column and shows success toast', async ({ page }) => {
    await page.context().grantPermissions(['clipboard-read', 'clipboard-write'])
    await gotoApiKeyPage(page)
    await expect(page.getByText('加载中...')).not.toBeVisible()

    const bodyRows = page.locator('tbody tr')
    if ((await bodyRows.count()) === 0 || (await page.getByText('暂无数据').isVisible())) {
      test.skip(true, '当前账号没有 API Key 数据，跳过列复制断言')
    }

    const firstRow = bodyRows.first()
    const akCode = (await firstRow.locator('td').nth(1).locator('span').first().textContent())?.trim()
    if (!akCode) {
      throw new Error('ak-code cell is empty')
    }
    await firstRow.getByRole('button', { name: `复制 ${akCode}` }).click()

    await expect(page.getByText('复制成功')).toBeVisible()
    await expect.poll(() => page.evaluate(() => navigator.clipboard.readText())).toBe(akCode)
  })

  test('navigates to sub-ak management page from action menu', async ({ page }) => {
    await gotoApiKeyPage(page)
    const bodyRows = page.locator('tbody tr')
    if ((await bodyRows.count()) === 0) {
      test.skip(true, '当前账号没有 API Key 数据，跳过子 AK 页面跳转断言')
    }

    await bodyRows.first().getByRole('button').last().click()
    const navigationPromise = page.waitForURL(/\/apikey\/sub-ak\//)
    await page.getByText('管理子AK').click()
    await navigationPromise
  })
})
