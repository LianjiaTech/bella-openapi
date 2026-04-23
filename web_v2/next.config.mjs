import createNextIntlPlugin from 'next-intl/plugin';

const withNextIntl = createNextIntlPlugin('./src/i18n/request.ts');

function normalizeOrigin(value) {
  if (!value) return '';

  const withProtocol = /^https?:\/\//.test(value) ? value : `http://${value}`;
  return withProtocol.replace(/\/$/, '');
}

function getBackendOrigin() {
  const configuredOrigin =
    process.env.BACKEND_API_URL ||
    process.env.NEXT_PUBLIC_API_ORIGIN ||
    process.env.NEXT_PUBLIC_API_BASE_URL;

  if (configuredOrigin) {
    return normalizeOrigin(configuredOrigin);
  }

  return normalizeOrigin(process.env.NEXT_PUBLIC_API_HOST);
}

const backendOrigin = getBackendOrigin();

/** @type {import('next').NextConfig} */
const nextConfig = {
    output: 'standalone',
    compress: false,
    typescript: {
        ignoreBuildErrors: false,
    },
    // 生产环境自动删除 console.log/info/debug，保留 error 和 warn
    compiler: {
        removeConsole: process.env.NODE_ENV === 'production' ? {
            exclude: ['error', 'warn'],
        } : false,
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
        // TODO: If web_v2 restores GitHub OAuth/star features from web, add GITHUB_CLIENT_ID/GITHUB_CLIENT_SECRET here.
    },
    async rewrites() {
        if (process.env.NEXT_PUBLIC_USE_MOCK === 'true') {
            return [];
        }

        if (process.env.NODE_ENV === 'development' && backendOrigin) {
            return [
                {
                    source: '/v1/:path*',
                    destination: `${backendOrigin}/v1/:path*`,
                },
                {
                    source: '/console/:path*',
                    destination: `${backendOrigin}/console/:path*`,
                },
                {
                    source: '/openapi/:path*',
                    destination: `${backendOrigin}/openapi/:path*`,
                },
            ];
        }

        return [];
    },
};

export default withNextIntl(nextConfig);
