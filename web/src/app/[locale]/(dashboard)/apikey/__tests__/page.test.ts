import { redirect } from '@/i18n/routing';
import LegacyApiKeysPage from '../page';

jest.mock('@/i18n/routing', () => ({
  redirect: jest.fn(),
}));

describe('legacy API key page', () => {
  it('redirects to the localized unified AK management page', async () => {
    await LegacyApiKeysPage({ params: Promise.resolve({ locale: 'en-US' }) });
    expect(redirect).toHaveBeenCalledWith({ href: '/manager', locale: 'en-US' });
  });
});
