export function getSidebarActivePathname(pathname: string | null, viewer: string | null): string | null {
  if (viewer === 'admin' && pathname?.startsWith('/apikey/sub-ak/')) {
    return '/apikey-admin';
  }

  if (viewer === 'manager' && pathname?.startsWith('/apikey/sub-ak/')) {
    return '/manager';
  }

  return pathname;
}
