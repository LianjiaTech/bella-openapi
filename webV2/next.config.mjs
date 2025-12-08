/** @type {import('next').NextConfig} */
const nextConfig = {
    output: 'standalone',
    typescript: {
        ignoreBuildErrors: true,
    },
    async rewrites() {
        return [
            {
                source: '/v1/chat/completions',
                destination: 'http://localhost:3010/sse',
            },
            {
                source: '/v1/:path*',
                destination: 'http://localhost:8080/v1/:path*',
            },
            {
                source: '/console/:path*',
                destination: 'http://localhost:8080/console/:path*',
            },
            {
                source: '/openapi/:path*',
                destination: 'http://localhost:8080/openapi/:path*',
            },
        ]
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
        GITHUB_CLIENT_ID: process.env.GITHUB_CLIENT_ID,
        GITHUB_CLIENT_SECRET: process.env.GITHUB_CLIENT_SECRET
    },
};

export default nextConfig;
