import { API_KEY_NAV_ITEM, getSidebarActivePathname } from '../sidebar-active';

describe('getSidebarActivePathname', () => {
  it('exposes the unified API key page as a first-level navigation entry', () => {
    expect(API_KEY_NAV_ITEM).toEqual({
      label: 'apiKeys',
      href: '/manager',
    });
  });

  it('keeps admin sub-key pages under the admin API key navigation entry', () => {
    expect(getSidebarActivePathname('/apikey/sub-ak/ak-001', 'admin')).toBe('/apikey-admin');
  });

  it('keeps manager sub-key pages under the unified navigation entry', () => {
    expect(getSidebarActivePathname('/apikey/sub-ak/ak-001', 'manager')).toBe('/manager');
  });

  it('keeps legacy personal sub-key pages under the unified navigation entry', () => {
    expect(getSidebarActivePathname('/apikey/sub-ak/ak-001', null)).toBe('/manager');
  });

  it('maps the legacy personal API key route to the unified navigation entry', () => {
    expect(getSidebarActivePathname('/apikey', null)).toBe('/manager');
  });
});
