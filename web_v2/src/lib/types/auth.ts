/**
 * 认证相关类型定义
 * 定义与后端API交互的数据结构
 */

/**
 * 用户信息
 * 对应后端: GET /console/userInfo 返回的数据结构
 */
export interface UserInfo {
    userId: number
    userName: string
    email: string
    tenantId: number | null
    spaceCode: string
    source: string
    sourceId: string
    managerAk: string
    optionalInfo?: {
      roles?: string[]        // 权限规则（Ant Path模式，如 ["/admin/**", "/api/*"]）
      excludes?: string[]     // 排除规则（优先级高于roles）
      [key: string]: any      // 其他扩展字段
    }
  }
  
  /**
   * OAuth提供商
   * 单个OAuth提供商的配置信息
   */
  export interface OAuthProvider {
    name: string              // 提供商名称: github, google, twitter 等
    displayName: string       // 显示名称: GitHub, Google, Twitter 等
    authUrl: string          // 授权URL（已包含state和redirect参数）
  }
  
  /**
   * OAuth配置响应
   * 对应后端: GET /openapi/oauth/config 返回的数据结构
   */
  export interface OAuthConfig {
    providers: OAuthProvider[]
  }
  
  /**
   * 登录请求
   * 对应后端: POST /openapi/login 的请求体
   */
  export interface LoginRequest {
    secret: string
  }
  
  /**
   * 登录响应
   * 对应后端: POST /openapi/login 返回的数据结构
   */
  export interface LoginResponse {
    success: boolean
    user?: UserInfo
    message?: string
  }
  
  /**
   * 认证错误类
   * 封装认证相关的错误信息
   */
  export class AuthError extends Error {
    constructor(
      message: string,
      public statusCode?: number,
      public code?: string
    ) {
      super(message)
      this.name = 'AuthError'
    }
  }
  