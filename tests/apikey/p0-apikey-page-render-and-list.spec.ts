import { expect, test } from '@playwright/test'
import { gotoApiKeyPage } from './apikey-page.helpers'

test.describe('apikey page - render and list', () => {
  // 验证“API Key 列表页首次进入时的基础渲染和首屏请求参数”是否正确
  test('renders page shell and requests first page list', async ({ page }) => {
    const pageRequestPromise = page.waitForRequest((request) =>
      request.url().includes('/console/apikey/page')
    )

    await gotoApiKeyPage(page)
    const firstRequestUrl = (await pageRequestPromise).url()

    await expect(page.getByText('管理您直接持有的 API 密钥，设置额度和安全等级')).toBeVisible()
    await expect(page.getByRole('button', { name: '创建新密钥' })).toBeVisible()
    await expect(page.getByPlaceholder('搜索 API Key...')).toBeVisible()
    await expect(page.getByRole('columnheader', { name: '密钥代码' })).toBeVisible()
    await expect(page.getByRole('columnheader', { name: '名称' })).toBeVisible()
    await expect(page.getByRole('columnheader', { name: '服务名' })).toBeVisible()
    await expect(page.getByRole('columnheader', { name: '月额度配置' })).toBeVisible()
    await expect(page.getByRole('columnheader', { name: '安全等级' })).toBeVisible()
    await expect(page.getByRole('columnheader', { name: '月额度使用' })).toBeVisible()
    await expect(page.getByRole('columnheader', { name: '备注' })).toBeVisible()
    await expect(page.getByRole('columnheader', { name: '操作' })).toBeVisible()

    const firstRequest = new URL(firstRequestUrl)
    expect(firstRequest.searchParams.get('page')).toBe('1')
    expect(firstRequest.searchParams.get('searchParam')).toBe('')
    expect(firstRequest.searchParams.get('status')).toBe('active')
  })

  test('renders either list rows or empty state from real data', async ({ page }) => {
    await gotoApiKeyPage(page)

    const bodyRows = page.locator('tbody tr')
    const emptyState = page.getByText('暂无数据')

    await expect(async () => {
      const [rowCount, emptyVisible] = await Promise.all([bodyRows.count(), emptyState.isVisible()])
      expect(rowCount > 0 || emptyVisible).toBe(true)
    }).toPass()
  })

  test('paginates when next-page button is available', async ({ page }) => {
    await gotoApiKeyPage(page)
    const nextButton = page.getByRole('button', { name: '下一页' })

    if (!(await nextButton.isVisible())) {
      test.skip(true, '当前账号没有下一页数据，跳过分页断言')
    }

    const secondPageRequestPromise = page.waitForRequest(
      (request) =>
        request.url().includes('/console/apikey/page') &&
        new URL(request.url()).searchParams.get('page') === '2'
    )

    await nextButton.click()
    await secondPageRequestPromise

    const firstPageRequestPromise = page.waitForRequest(
      (request) =>
        request.url().includes('/console/apikey/page') &&
        new URL(request.url()).searchParams.get('page') === '1'
    )
    await page.getByRole('button', { name: '上一页' }).click()
    await firstPageRequestPromise
  })
})
