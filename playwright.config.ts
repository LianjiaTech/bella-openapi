import { defineConfig, devices } from '@playwright/test';
import dotenv from 'dotenv';
dotenv.config();

const authMode = process.env.AUTH_MODE === 'cas' ? 'cas' : 'apikey';
const setupSpec = authMode === 'cas'
  ? '**/login-flow/cas-login.spec.ts'
  : '**/login-flow/api-key-login.spec.ts';
const loginFlowSpecs = '**/login-flow/*.spec.ts';

/**
 * Playwright 配置文件
 *
 * 职责:
 * - 配置使用本地 Chrome 浏览器进行 E2E 测试
 * - 设置测试目录、输出目录和基础 URL
 * - 配置测试超时、重试策略和并发数
 * - 启动开发服务器用于测试
 * - 配置全局登录态管理,所有测试复用登录状态
 */

export default defineConfig({
  // 测试目录
  testDir: './tests',

  // 测试输出目录
  outputDir: './test-results',

  // 全局超时: 30秒
  timeout: 30 * 1000,

  // 每个测试的全局 expect 超时: 5秒
  expect: {
    timeout: 5000
  },

  // 失败重试次数
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,

  // 并发 worker 数量
  workers: process.env.CI ? 1 : undefined,

  // 测试报告配置
  reporter: [
    ['html', { outputFolder: 'playwright-report' }],
    ['list'],
  ],

  // 共享配置
  use: {
    // 基础 URL - 支持通过环境变量覆盖(用于 Docker 容器内访问宿主机)
    baseURL: process.env.BASE_URL || 'http://bi.off.ke.com:3000',

    // 截图配置: 仅在失败时截图
    screenshot: 'only-on-failure',

    // 录制 trace: 仅在首次重试时
    trace: 'on-first-retry',

    // 视频配置: 容器环境禁用视频录制(需要 ffmpeg),本地环境启用
    video: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH ? 'off' : 'retain-on-failure',
  },

  // 测试项目配置
  projects: [
    // Setup 项目: 执行登录初始化并保存认证状态
    {
      name: 'setup',
      testMatch: setupSpec,
      use: {
        ...devices['Desktop Chrome'],
        // 根据环境变量决定使用本地 Chrome 还是容器 Chromium
        ...(process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH ? {
          channel: undefined,
          launchOptions: {
            executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH,
            args: [
              '--no-sandbox',
              '--disable-setuid-sandbox',
              '--disable-dev-shm-usage',
              '--disable-gpu',
              '--disable-software-rasterizer',
              '--disable-extensions',
            ],
          },
        } : {
          // 使用本地安装的 Chrome 浏览器
          channel: 'chrome',
        }),
      },
    },
    // 主测试项目: 复用 setup 项目保存的认证状态
    {
      name: 'chromium',
      dependencies: ['setup'],
      testIgnore: [loginFlowSpecs],
      use: {
        ...devices['Desktop Chrome'],
        // 加载已保存的认证状态,跳过登录流程
        storageState: './tests/.auth/user.json',
        // 根据环境变量决定使用本地 Chrome 还是容器 Chromium
        ...(process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH ? {
          channel: undefined,
          launchOptions: {
            executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH,
            args: [
              '--no-sandbox',
              '--disable-setuid-sandbox',
              '--disable-dev-shm-usage',
              '--disable-gpu',
              '--disable-software-rasterizer',
              '--disable-extensions',
            ],
          },
        } : {
          // 使用本地安装的 Chrome 浏览器
          channel: 'chrome',
        }),
      },
    },
  ],

  // Web Server 配置: 在测试前启动开发服务器
  // 如果设置了 BASE_URL 环境变量,则不启动 webServer (假设外部已启动)
  webServer: process.env.BASE_URL ? undefined : {
    command: 'cd web_v2 && npm run dev',
    url: 'http://bi.off.ke.com:3000',
    reuseExistingServer: !process.env.CI,
    timeout: 120 * 1000,
  },
});