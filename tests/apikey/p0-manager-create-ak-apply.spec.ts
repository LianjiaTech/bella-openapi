import { expect, test } from '@playwright/test'
import { gotoManagerPage, setupCommonMocks } from './apikey-page.helpers'

test.describe('manager page - create api key apply entry', () => {
  const cityOrgApiKeyQuotaDocUrl =
    'https://doc.weixin.qq.com/doc/w3_AH4AOQbyANMCNUKaCWhoeS1iwRqRw?scode=AJMA1Qc4AAww35tgpPAH4AOQbyANM'

  test('reuses the api key type selection guide for organization and project apply', async ({ page }) => {
    await setupCommonMocks(page)
    await gotoManagerPage(page)

    await page.getByRole('button', { name: '申请创建 AK' }).click()
    const typeDialog = page.getByRole('dialog').filter({ hasText: '选择 APIKey 类型' })
    await expect(typeDialog).toBeVisible()

    await expect(typeDialog.getByText('组织 APIKey')).toBeVisible()
    await expect(typeDialog.getByText('组织通用密钥，用于组织长期调用，由组织管理者统一管控整体资源配额，支持 BPM 流程提额。')).toBeVisible()
    await expect(typeDialog.getByText('城市申请组织apikey额度，需完成预算调拨流程，具体参考')).toBeVisible()
    await expect(typeDialog.getByRole('link', { name: '文档' })).toHaveAttribute(
      'href',
      cityOrgApiKeyQuotaDocUrl
    )
    await expect(typeDialog.getByText('项目 APIKey')).toBeVisible()
    await expect(typeDialog.getByText('项目专属密钥，绑定独立项目，生命周期与项目同步启停，支持 BPM 流程提额。')).toBeVisible()
    await expect(typeDialog.getByText('个人 APIKey')).toHaveCount(0)
  })

  test('reports a clear error when the BPM create apply entry is missing', async ({ page }) => {
    test.skip(
      !!process.env.NEXT_PUBLIC_APIKEY_CREATE_APPLY_URL,
      'BPM 创建申请地址已配置，跳过未配置反馈断言'
    )

    await setupCommonMocks(page)
    await gotoManagerPage(page)

    await page.getByRole('button', { name: '申请创建 AK' }).click()
    const typeDialog = page.getByRole('dialog').filter({ hasText: '选择 APIKey 类型' })
    await expect(typeDialog).toBeVisible()

    await typeDialog.getByRole('button', { name: /项目 APIKey/ }).click()
    await expect(page.getByText('项目 APIKey 创建申请入口未配置或地址无效')).toBeVisible()
  })
})
