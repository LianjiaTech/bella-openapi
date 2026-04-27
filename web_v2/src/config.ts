/**
 * 应用配置文件
 * 用于集中管理环境变量和全局配置
 */

function normalizePublicOrigin(value?: string): string {
  if (!value) return '';

  const withProtocol = /^https?:\/\//.test(value) ? value : `http://${value}`;
  return withProtocol.replace(/\/$/, '');
}

const publicBackendOrigin = normalizePublicOrigin(
  process.env.NEXT_PUBLIC_API_ORIGIN ||
  process.env.NEXT_PUBLIC_API_BASE_URL ||
  process.env.NEXT_PUBLIC_API_HOST
);

const defaultApiEndpoint =
  process.env.NEXT_PUBLIC_DEFAULT_API_ENDPOINT ||
  (publicBackendOrigin ? `${publicBackendOrigin}/v1` : '/v1');

export const config = {
  // API 基础配置
  api: {
    baseUrl: publicBackendOrigin || '',
    // 默认 API 端点（用于代码示例展示）
    defaultEndpoint: defaultApiEndpoint,
    // Playground 服务地址
    workflowPlayground: process.env.NEXT_PUBLIC_WORKFLOW_PLAYGROUND,
    documentParsePlayground: process.env.NEXT_PUBLIC_DOCUMENT_PARSE_PLAYGROUND,
    // 配置后可在调试日志中展示真实后端地址；未配置时走相对路径
    realBackendUrl: publicBackendOrigin,
    // 文档地址（用于首页"浏览文档"按钮，为空时隐藏按钮）
    docsUrl: process.env.NEXT_PUBLIC_DOCS_URL || '',
  },

  // 应用配置
  app: {
    name: 'Bella OpenAPI',
    version: '0.1.0',
  },

  tips: {
    apiKeyPageTip: process.env.NEXT_PUBLIC_APIKEY_PAGE_TIP || '',
  },

  // 安全合规配置
  compliance: {
    privacyUrl: process.env.NEXT_PUBLIC_PRIVACY_URL || '',
  },
};

export default config;
