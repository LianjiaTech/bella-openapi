import { defineRouting } from 'next-intl/routing';
import { createNavigation } from 'next-intl/navigation';

export const routing = defineRouting({
  locales: ['zh-CN', 'en-US'],
  defaultLocale: 'zh-CN',
  localePrefix: 'as-needed' // Only add locale prefix when not using default locale
});

export const { Link, redirect, usePathname, useRouter } = createNavigation(routing);
