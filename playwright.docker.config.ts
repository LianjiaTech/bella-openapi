import { defineConfig, devices } from '@playwright/test';

const authMode = process.env.AUTH_MODE === 'cas' ? 'cas' : 'apikey';
const setupSpec = authMode === 'cas'
  ? '**/login-flow/cas-login.spec.ts'
  : '**/login-flow/api-key-login.spec.ts';
const loginFlowSpecs = '**/login-flow/*.spec.ts';

/**
 * Playwright Docker 测试环境配置
 *
 * 职责:
 * - 配置 Docker 容器内运行测试的环境参数
 * - 通过 BASE_URL 环境变量连接宿主机应用
 * - 配置浏览器启动参数(禁用沙箱等)
 * - 设置测试报告输出路径
 * - 配置全局登录态管理,所有测试复用登录状态
 *
 * 设计说明:
 * 1. baseURL 从环境变量读取,支持用户自定义测试地址
 * 2. 使用 Playwright 内置浏览器,确保版本匹配和功能完整性
 * 3. 禁用沙箱模式,允许在 Docker 容器内运行 Chromium
 * 4. 测试报告输出到挂载的宿主机目录
 * 5. testDir 统一指向 './tests',与本地配置保持一致
 * 6. 使用两阶段项目配置:
 *    - setup 项目:运行登录初始化用例并保存认证状态
 *    - chromium 项目:依赖 setup,加载认证状态执行其他测试
 * 7. 通过 dependencies 确保 setup 项目优先执行
 *
 * 避免 re-render:
 * - 本配置文件不涉及 React 组件,无 re-render 问题
 */

export default defineConfig({
  // 测试目录 - 统一路径配置
  testDir: './tests',

  // 测试匹配模式
  testMatch: '**/*.spec.ts',

  // 全局测试超时时间 (30秒)
  timeout: 30 * 1000,

  // 失败后不重试(Docker 环境通常用于 CI,快速失败)
  fullyParallel: true,
  retries: 0,

  // 并发执行的 worker 数量
  workers: 2,

  // 测试报告配置
  reporter: [
    ['html', { outputFolder: 'playwright-report', open: 'never' }],
    ['list'],
    ['json', { outputFile: 'test-results/results.json' }],
  ],

  // 全局配置
  use: {
    // 从环境变量读取测试地址,默认值为 localhost (host 网络模式)
    baseURL: process.env.BASE_URL || 'http://localhost',

    // 截图配置:仅在测试失败时截图
    screenshot: 'only-on-failure',

    // 录制视频:禁用(Docker 环境中 ffmpeg 不可用)
    video: 'off',

    // Trace 配置:仅在重试时记录
    trace: 'on-first-retry',

    // 浏览器上下文配置
    // viewport: { width: 1280, height: 720 },
    locale: 'zh-CN',
    timezoneId: 'Asia/Shanghai',
  },

  // 测试输出目录
  outputDir: 'test-results',

  // 项目配置 - 使用 setup + chromium 两阶段模式
  projects: [
    // Setup 项目: 执行登录并保存认证状态
    {
      name: 'setup',
      testMatch: setupSpec,
      use: {
        ...devices['Desktop Chrome'],

        // 浏览器启动配置 - 针对 Docker 容器环境优化
        launchOptions: {
          // 🔴 关键:Docker 容器内必须使用 headless 模式(无图形界面)
          headless: true,

          args: [
            // 禁用沙箱模式(Docker 容器内必需)
            '--no-sandbox',
            '--disable-setuid-sandbox',

            // 禁用共享内存(避免 Docker 容器内的 /dev/shm 限制)
            '--disable-dev-shm-usage',

            // 禁用 GPU 加速(容器内通常无 GPU)
            '--disable-gpu',

            // 🔧 禁用 IPv6,强制使用 IPv4
            '--disable-features=WebRtcHideLocalIpsWithMdns',
            '--disable-ipv6',

            // 其他优化参数
            '--disable-software-rasterizer',
            '--disable-extensions',
            '--disable-background-networking',
            '--disable-sync',
          ],

          // 超时配置
          timeout: 30000,
        },
      },
    },

    // 主测试项目: 复用 setup 项目保存的认证状态
    {
      name: 'chromium',
      dependencies: ['setup'],  // 🔑 关键:依赖 setup 项目,确保先执行登录
      testIgnore: [loginFlowSpecs],  // 🔑 关键:排除所有登录初始化用例,仅由 setup 项目执行
      use: {
        ...devices['Desktop Chrome'],

        // 🔑 关键:加载已保存的认证状态,跳过登录流程
        storageState: './tests/.auth/user.json',

        // 浏览器启动配置 - 针对 Docker 容器环境优化
        launchOptions: {
          // 🔴 关键:Docker 容器内必须使用 headless 模式(无图形界面)
          headless: true,

          args: [
            // 禁用沙箱模式(Docker 容器内必需)
            '--no-sandbox',
            '--disable-setuid-sandbox',

            // 禁用共享内存(避免 Docker 容器内的 /dev/shm 限制)
            '--disable-dev-shm-usage',

            // 禁用 GPU 加速(容器内通常无 GPU)
            '--disable-gpu',

            // 🔧 禁用 IPv6,强制使用 IPv4
            '--disable-features=WebRtcHideLocalIpsWithMdns',
            '--disable-ipv6',

            // 其他优化参数
            '--disable-software-rasterizer',
            '--disable-extensions',
            '--disable-background-networking',
            '--disable-sync',
          ],

          // 超时配置
          timeout: 30000,
        },
      },
    },
  ],

  // Web Server 配置 - Docker 环境中不启动本地服务器
  // 测试连接到宿主机上已运行的服务器 (通过 BASE_URL 环境变量指定)
  webServer: undefined,
});