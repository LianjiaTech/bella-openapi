import { getSidebarActivePathname } from '../sidebar-active';

describe('getSidebarActivePathname', () => {
  it('keeps admin sub-key pages under the admin API key navigation entry', () => {
    expect(getSidebarActivePathname('/apikey/sub-ak/ak-001', 'admin')).toBe('/apikey-admin');
  });

  it('keeps manager sub-key pages under the manager navigation entry', () => {
    expect(getSidebarActivePathname('/apikey/sub-ak/ak-001', 'manager')).toBe('/manager');
  });

  it('keeps default sub-key pages under the personal API key navigation entry', () => {
    expect(getSidebarActivePathname('/apikey/sub-ak/ak-001', null)).toBe('/apikey/sub-ak/ak-001');
  });

  it('does not remap neighboring API key routes for manager viewer', () => {
    expect(getSidebarActivePathname('/apikey', 'manager')).toBe('/apikey');
  });
});
