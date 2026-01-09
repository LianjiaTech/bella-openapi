import createNextIntlPlugin from 'next-intl/plugin';

const withNextIntl = createNextIntlPlugin('./src/i18n/request.ts');

/** @type {import('next').NextConfig} */
const nextConfig = {
    output: 'standalone',
    typescript: {
        ignoreBuildErrors: false,
    },
    // 开发环境路由重写：将后端 API 路径重写到 Next.js API 路由
    // 生产环境由 nginx 代理，此配置不生效
    async rewrites() {
        // 只在开发环境启用 rewrites
        if (process.env.NODE_ENV === 'development') {
            return [
                {
                    source: '/v1/:path*',
                    destination: '/api/v1/:path*',
                },
                {
                    source: '/console/:path*',
                    destination: '/api/console/:path*',
                },
                {
                    source: '/openapi/:path*',
                    destination: '/api/openapi/:path*',
                },
            ];
        }
        return [];
    },
};

export default withNextIntl(nextConfig);

