/**
 * 认证 Mock 数据配置
 * 用于开发环境模拟不同的登录场景
 *
 * 使用方式：
 * 1. 设置环境变量 NEXT_PUBLIC_USE_MOCK=true 启用 Mock 模式
 * 2. 通过修改 currentScenario 切换不同场景
 */

import type { UserInfo, OAuthConfig, LoginResponse } from '@/lib/types/auth'

/**
 * Mock 场景类型
 */
export type MockScenario =
  | 'oauth-github-google'  // OAuth模式：GitHub + Google
  | 'oauth-github-only'    // OAuth模式：仅GitHub
  | 'cas-mode'             // CAS企业登录模式
  | 'no-auth'              // 未登录状态

/**
 * 当前激活的 Mock 场景
 * 修改这个值来切换不同的测试场景
 */
export const currentScenario: MockScenario = 'oauth-github-google'

/**
 * Mock 用户数据
 */
export const mockUsers = {
  // 已登录用户
  loggedInUser: {
    userId: 10001,
    userName: 'MockUser',
    email: 'mock@example.com',
    tenantId: 1,
    spaceCode: 'default',
    source: 'mock',
    sourceId: 'mock-123',
    managerAk: 'ak-mock-manager-key',
    optionalInfo: {
      roles: ['/admin/**', '/api/**'],
      excludes: [],
    }
  } as UserInfo,

  // OAuth 登录后的用户
  oauthUser: {
    userId: 10002,
    userName: 'GitHubUser',
    email: 'github@example.com',
    tenantId: 1,
    spaceCode: 'default',
    source: 'github',
    sourceId: 'github-456',
    managerAk: 'ak-github-key',
    optionalInfo: {
      roles: ['/user/**'],
    }
  } as UserInfo,

  // 密钥登录后的用户
  secretUser: {
    userId: 10003,
    userName: 'SecretUser',
    email: 'secret@example.com',
    tenantId: 1,
    spaceCode: 'default',
    source: 'secret',
    sourceId: 'secret-789',
    managerAk: 'ak-secret-key',
    optionalInfo: {
      roles: ['/user/**'],
    }
  } as UserInfo,
}

/**
 * OAuth 配置 - GitHub + Google 模式
 */
export const oauthConfigGithubGoogle: OAuthConfig = {
  providers: [
    {
      name: 'github',
      displayName: 'GitHub',
      authUrl: 'https://github.com/login/oauth/authorize?client_id=mock-github-id&redirect_uri=http://localhost:3000/api/oauth/callback&state=mock-state-123'
    },
    {
      name: 'google',
      displayName: 'Google',
      authUrl: 'https://accounts.google.com/o/oauth2/v2/auth?client_id=mock-google-id&redirect_uri=http://localhost:3000/api/oauth/callback&state=mock-state-456'
    }
  ]
}

/**
 * OAuth 配置 - 仅 GitHub 模式
 */
export const oauthConfigGithubOnly: OAuthConfig = {
  providers: [
    {
      name: 'github',
      displayName: 'GitHub',
      authUrl: 'https://github.com/login/oauth/authorize?client_id=mock-github-id&redirect_uri=http://localhost:3000/api/oauth/callback&state=mock-state-123'
    }
  ]
}

/**
 * CAS 企业登录模式 - 空 OAuth 配置
 */
export const oauthConfigCasMode: OAuthConfig = {
  providers: []
}

/**
 * 有效的测试密钥列表
 */
export const validSecrets = [
  'test-secret-123',
  'demo-key-456',
  'mock-password'
]

/**
 * 根据当前场景获取 OAuth 配置
 */
export function getOAuthConfigByScenario(scenario: MockScenario = currentScenario): OAuthConfig {
  switch (scenario) {
    case 'oauth-github-google':
      return oauthConfigGithubGoogle
    case 'oauth-github-only':
      return oauthConfigGithubOnly
    case 'cas-mode':
      return oauthConfigCasMode
    case 'no-auth':
      return oauthConfigCasMode
    default:
      return oauthConfigGithubGoogle
  }
}

/**
 * 模拟密钥登录
 * @param secret 用户输入的密钥
 * @returns 登录响应
 */
export function mockSecretLogin(secret: string): LoginResponse {
  // 检查密钥是否有效
  if (validSecrets.includes(secret)) {
    return {
      success: true,
      user: mockUsers.secretUser,
      message: '登录成功'
    }
  } else {
    return {
      success: false,
      message: '密钥无效，请重试'
    }
  }
}

/**
 * 根据当前场景获取用户信息
 * @param scenario Mock 场景
 * @param isLoggedIn 是否已登录（用于测试未登录状态）
 * @returns 用户信息或 null
 */
export function getUserInfoByScenario(
  scenario: MockScenario = currentScenario,
  isLoggedIn: boolean = false
): UserInfo | null {
  // 未登录状态
  if (!isLoggedIn) {
    return null
  }

  // 根据场景返回对应用户
  switch (scenario) {
    case 'oauth-github-google':
    case 'oauth-github-only':
      return mockUsers.oauthUser
    case 'cas-mode':
      return mockUsers.loggedInUser
    default:
      return mockUsers.loggedInUser
  }
}

/**
 * 场景说明文档
 */
export const scenarioDescriptions = {
  'oauth-github-google': {
    name: 'OAuth模式（GitHub + Google）',
    description: '返回两个OAuth提供商，用户可选择GitHub或Google登录',
    testSteps: [
      '1. 访问 /login 页面',
      '2. 看到 "使用 GitHub 登录" 和 "使用 Google 登录" 按钮',
      '3. 点击按钮会跳转到对应的OAuth授权页面（Mock URL）'
    ]
  },
  'oauth-github-only': {
    name: 'OAuth模式（仅GitHub）',
    description: '只返回GitHub一个OAuth提供商',
    testSteps: [
      '1. 访问 /login 页面',
      '2. 看到 "使用 GitHub 登录" 按钮',
      '3. 看到密钥登录表单'
    ]
  },
  'cas-mode': {
    name: 'CAS企业登录模式',
    description: '返回空providers数组，模拟企业SSO登录',
    testSteps: [
      '1. 访问 /login 页面',
      '2. 看到 "正在跳转到企业登录页面..." 提示',
      '3. 实际跳转由client.ts的401拦截器处理'
    ]
  },
  'no-auth': {
    name: '未登录状态',
    description: '测试未登录时的路由守卫和重定向',
    testSteps: [
      '1. 访问受保护页面（如 /apikey）',
      '2. 自动重定向到 /login',
      '3. 可以测试登录流程'
    ]
  }
}