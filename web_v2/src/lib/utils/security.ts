/**
 * 安全工具函数
 * 提供 URL 验证、XSS 防护等安全相关功能
 */

/**
 * 允许重定向的可信域名列表
 * - 默认允许当前域名和相对路径
 * - 可根据实际需求添加其他登录/认证域名
 */
const ALLOWED_REDIRECT_ORIGINS: string[] = [
    // 当前域名会在运行时动态添加（见 isValidRedirectUrl）
    // 如需添加其他可信域名，取消注释并配置：
    // 'https://login.beike.com',
    // 'https://sso.internal.com',
  ];
  
  /**
   * 验证重定向 URL 是否安全
   *
   * 安全策略：
   * 1. 允许绝对路径（以单个 / 开头）
   * 2. 拒绝协议相对 URL（以 // 开头，可能跳转到恶意域名）
   * 3. 允许白名单内的完整 URL
   *
   * @param url - 待验证的重定向 URL
   * @returns 是否为安全的重定向地址
   *
   * @example
   * isValidRedirectUrl('/auth/login')           // ✅ true
   * isValidRedirectUrl('//attacker.com')        // ❌ false
   * isValidRedirectUrl('https://evil.com')      // ❌ false
   * isValidRedirectUrl(window.location.origin)  // ✅ true
   */
  export const isValidRedirectUrl = (url: string): boolean => {
    try {
      // 1. 相对路径检查：仅允许绝对路径（以单个 / 开头）
      //    拒绝协议相对 URL（// 开头）
      if (url.startsWith('/')) {
        return !url.startsWith('//'); // 排除 //evil.com
      }
  
      // 2. 完整 URL 检查：解析并验证域名
      //    仅在浏览器环境执行（SSR 环境跳过）
      if (typeof window === 'undefined') {
        return false; // 服务端不应触发重定向
      }
  
      const parsedUrl = new URL(url);
      const allowedOrigins = [
        window.location.origin, // 动态获取当前域名
        ...ALLOWED_REDIRECT_ORIGINS,
      ];
  
      return allowedOrigins.includes(parsedUrl.origin);
    } catch {
      // URL 解析失败（格式错误），拒绝重定向
      return false;
    }
  };