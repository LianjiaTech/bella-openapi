import { defineRouting } from 'next-intl/routing';
import { createNavigation } from 'next-intl/navigation';

export const routing = defineRouting({
  locales: ['zh-CN', 'en-US'],
  defaultLocale: 'zh-CN',
  localePrefix: 'as-needed' // Only add locale prefix when not using default locale
});

const localeSet = new Set(routing.locales)

export function stripLocalePrefix(pathname: string): string {
  const segments = pathname.split('/')
  const locale = segments[1]

  if (!localeSet.has(locale as (typeof routing.locales)[number])) {
    return pathname
  }

  const strippedPath = `/${segments.slice(2).join('/')}`
  return strippedPath === '/' ? strippedPath : strippedPath.replace(/\/$/, '')
}

export function isLoginPath(pathname: string): boolean {
  const normalizedPath = stripLocalePrefix(pathname)
  return normalizedPath === '/login' || normalizedPath.startsWith('/login/')
}

export const { Link, redirect, usePathname, useRouter } = createNavigation(routing);
