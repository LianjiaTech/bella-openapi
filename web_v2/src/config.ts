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
    // Playground 服务地址
    workflowPlayground: process.env.NEXT_PUBLIC_WORKFLOW_PLAYGROUND,
    documentParsePlayground: process.env.NEXT_PUBLIC_DOCUMENT_PARSE_PLAYGROUND,
    // 真实后端域名（用于调试日志，显示完整请求 URL）
    // 开发环境示例: http://test-bella-openapi.ke.com
    // 生产环境可留空，由 Nginx 代理
    realBackendUrl: process.env.NEXT_PUBLIC_API_HOST || '',
  },
  
  // 应用配置
  app: {
    name: 'Bella OpenAPI',
    version: '0.1.0',
  },
};

export default config;

