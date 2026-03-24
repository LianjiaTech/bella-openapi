import createNextIntlPlugin from 'next-intl/plugin';

const withNextIntl = createNextIntlPlugin('./src/i18n/request.ts');

/** @type {import('next').NextConfig} */
const nextConfig = {
    output: 'standalone',
    compress: false,
    typescript: {
        ignoreBuildErrors: false,
    },
    env: {
        WORKFLOW_URL: process.env.WORKFLOW_URL,
        WORKFLOW_API_KEY: process.env.WORKFLOW_API_KEY,
        ES_URL: process.env.ES_URL,
        ES_API_KEY: process.env.ES_API_KEY,
        TENANT_ID: process.env.TENANT_ID,
        METRICS_WORKFLOW_ID: process.env.METRICS_WORKFLOW_ID,
        LOGS_TRACE_WORKFLOW_ID: process.env.LOGS_TRACE_WORKFLOW_ID,
        SERVICE_WORKFLOW_ID: process.env.SERVICE_WORKFLOW_ID,
    },
    // 开发环境路由重写：将后端 API 路径重写到 Next.js API 路由
    // 生产环境由 nginx 代理，此配置不生效
    async rewrites() {
        // Mock 模式：禁用所有 rewrites，使用 Next.js API Routes
        if (process.env.NEXT_PUBLIC_USE_MOCK === 'true') {
            console.log('🎭 Mock 模式已启用，禁用 rewrites 规则');
            return [];
        }

        // 只在开发环境启用 rewrites
        if (process.env.NODE_ENV === 'development') {
            console.log('🔗 开发模式：启用后端代理 rewrites');
            return [
                {
                    source: '/v1/:path*',
                    destination: 'http://test-bella-openapi.ke.com/v1/:path*',
                },
                {
                    source: '/console/:path*',
                    destination: 'http://test-bella-openapi.ke.com/console/:path*',
                },
                {
                    source: '/openapi/:path*',
                    destination: 'http://test-bella-openapi.ke.com/openapi/:path*',
                },
            ];
        }
        return [];
    },
};

export default withNextIntl(nextConfig);

