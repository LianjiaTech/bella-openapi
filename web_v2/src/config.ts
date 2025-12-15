/**
 * 应用配置文件
 * 用于集中管理环境变量和全局配置
 */

export const config = {
  // API 基础配置
  api: {
    baseUrl: process.env.NEXT_PUBLIC_API_BASE_URL || '',
  },
  
  // 应用配置
  app: {
    name: 'Bella OpenAPI',
    version: '0.1.0',
  },
};

export default config;

