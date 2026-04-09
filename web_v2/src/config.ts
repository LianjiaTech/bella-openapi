/**
 * 应用配置文件
 * 用于集中管理环境变量和全局配置
 */

export const config = {
  // API 基础配置
  api: {
    baseUrl: process.env.NEXT_PUBLIC_API_BASE_URL || '',
    // 默认 API 端点（用于代码示例展示）
    defaultEndpoint: process.env.NEXT_PUBLIC_DEFAULT_API_ENDPOINT || '//localhost/v1',
  },
  
  // 应用配置
  app: {
    name: 'Bella OpenAPI',
    version: '0.1.0',
  },

  // 页面提示配置（通过环境变量注入，区分不同部署环境）
  tips: {
    // API Key 页面顶部提示，为空则不显示
    apiKeyPageTip: process.env.NEXT_PUBLIC_APIKEY_PAGE_TIP || '',
  },
};

export default config;

