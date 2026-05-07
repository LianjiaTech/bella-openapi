import { expect, test } from '@playwright/test'
import { gotoApiKeyPage } from './apikey-page.helpers'

test.describe('apikey page - search', () => {
  test('searches with debounce and clears search', async ({ page }) => {
    const requests: Array<{ page: string | null; searchParam: string | null }> = []
    page.on('request', (request) => {
      if (!request.url().includes('/console/apikey/page')) return
      const url = new URL(request.url())
      requests.push({
        page: url.searchParams.get('page'),
        searchParam: url.searchParams.get('searchParam'),
      })
    })

    await gotoApiKeyPage(page)

    const searchInput = page.getByPlaceholder('搜索 API Key...')
    const query = `pw-${Date.now()}`
    await searchInput.fill(query)
    await expect(page.locator('svg.animate-spin')).toBeVisible()
    await page.waitForTimeout(650)

    expect(requests.at(-1)).toEqual({ page: '1', searchParam: query })

    await page.getByRole('button').filter({ has: page.locator('svg.lucide-x') }).click()
    await page.waitForTimeout(650)

    await expect(searchInput).toHaveValue('')
    await expect(page.getByText('主密钥')).toBeVisible()
    expect(requests.at(-1)).toEqual({ page: '1', searchParam: '' })
  })

  test('resets page to 1 when search query changes', async ({ page }) => {
    const pageRequests: Array<{ page: string; searchParam: string }> = []
    page.on('request', (request) => {
      if (!request.url().includes('/console/apikey/page')) return
      const url = new URL(request.url())
      pageRequests.push({
        page: url.searchParams.get('page') ?? '1',
        searchParam: url.searchParams.get('searchParam') ?? '',
      })
    })

    await gotoApiKeyPage(page)
    const nextButton = page.getByRole('button', { name: '下一页' })

    if (!(await nextButton.isVisible())) {
      test.skip(true, '当前账号没有下一页数据，跳过分页归位断言')
    }

    await nextButton.click()

    const query = `pw-${Date.now()}`
    await page.getByPlaceholder('搜索 API Key...').fill(query)
    await page.waitForTimeout(650)

    const lastRequest = pageRequests.at(-1)
    expect(lastRequest?.page).toBe('1')
    expect(lastRequest?.searchParam).toBe(query)
  })
})
