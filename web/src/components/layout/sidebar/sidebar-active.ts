export const API_KEY_NAV_ITEM = {
  label: 'apiKeys',
  href: '/manager',
} as const;

export function getSidebarActivePathname(pathname: string | null, viewer: string | null): string | null {
  if (viewer === 'admin' && pathname?.startsWith('/apikey/sub-ak/')) {
    return '/apikey-admin';
  }

  if (pathname === '/apikey' || pathname === '/manager' || pathname?.startsWith('/apikey/sub-ak/')) {
    return '/manager';
  }

  return pathname;
}
