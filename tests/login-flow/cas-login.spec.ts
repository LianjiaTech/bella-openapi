import { test, expect } from '@playwright/test';
import { ensureAuthDir, getAuthFilePath } from '../utils/auth-state';
import { getCasCredentials, getCasSuccessUrl } from '../utils/read-cas-credentials';

test.describe.serial('登录态初始化 - CAS', () => {
  test.beforeAll(() => {
    ensureAuthDir();
  });

  test('CAS 登录并保存认证状态', async ({ page }) => {
    const { username, password } = getCasCredentials();
    const successUrl = getCasSuccessUrl();
    const casLoginUrl = 'https://test-login.ke.com/login';

    await page.goto('/apikey');

    await expect(page).toHaveURL(new RegExp(casLoginUrl.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));

    // 1) 点击员工，进入员工登录页面
    const employeeOption = page
      .locator('div.btn-select')
      .filter({ has: page.locator('p.p-account-name', { hasText: '员工' }) })
      .first();
    await expect(employeeOption).toBeVisible({ timeout: 10000 });
    await employeeOption.click();
    await expect(page.getByText('员工登录')).toBeVisible({ timeout: 10000 });

    // 2) 输入账号密码并点击登录
    const usernameInput = page.locator('#username').first();
    const passwordInput = page.locator('#password, input[type="password"]').first();
    const loginButton = page.locator('button[type="submit"]').first();
    await expect(usernameInput).toBeVisible({ timeout: 10000 });
    await expect(passwordInput).toBeVisible({ timeout: 10000 });
    await usernameInput.fill(username);
    await passwordInput.fill(password);
    await expect(loginButton).toBeVisible({ timeout: 10000 });
    await loginButton.click();

    if (successUrl) {
      await expect(page).toHaveURL(new RegExp(successUrl.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
    } else {
      await expect(page).toHaveURL(/\/(overview|apikey)/);
    }

    await expect(page.getByRole('button', { name: /设置|Settings/ })).toBeVisible();
    await page.context().storageState({ path: getAuthFilePath() });
  });
});
