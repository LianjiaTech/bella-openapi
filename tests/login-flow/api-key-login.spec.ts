import { test, expect } from '@playwright/test';
import { ensureAuthDir, getAuthFilePath } from '../utils/auth-state';
import { getSystemApiKey } from '../utils/read-system-apikey';

test.describe.serial('登录态初始化 - API Key', () => {
  test.beforeAll(() => {
    ensureAuthDir();
  });

  test('系统密钥登录并保存认证状态', async ({ page }) => {
    const systemApiKey = getSystemApiKey();

    await page.goto('/');

    const apikeyInput = page.getByPlaceholder('请输入您的密钥');
    await apikeyInput.fill(systemApiKey);
    await expect(apikeyInput).toHaveValue(systemApiKey);

    await page.getByRole('button', { name: '登录' }).click();

    await expect(page).toHaveURL(/\/overview/);
    await expect(page.getByRole('button', { name: /设置|Settings/ })).toBeVisible();

    await page.context().storageState({ path: getAuthFilePath() });
  });
});
