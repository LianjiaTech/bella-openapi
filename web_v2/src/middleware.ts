import createMiddleware from 'next-intl/middleware';
import { routing } from './i18n/routing';

export default createMiddleware(routing);

export const config = {
  matcher: [
    // Match all pathnames except for
    // - … if they start with `/api`, `/_next` or `/_vercel`
    // - … if they start with `/v1`, `/console`, `/openapi` (for API rewrites)
    // - … the ones containing a dot (e.g. `favicon.ico`)
    '/((?!api/|_next|_vercel|v1|console|openapi|.*\\..*).*)',
    // However, match all pathnames within `/api`, except for the ones using `/api/trpc`
    // '/api/((?!trpc).*)'
  ]
};
