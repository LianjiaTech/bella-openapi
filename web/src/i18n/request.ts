import { getRequestConfig } from 'next-intl/server';
import { routing } from './routing';
import { headers } from 'next/headers';

export default getRequestConfig(async ({ requestLocale }) => {
  // This typically corresponds to the `[locale]` segment
  let locale = await requestLocale;

  // If no locale from request, try to get from headers or cookies
  if (!locale || !routing.locales.includes(locale as any)) {
    const headersList = await headers();
    const cookieLocale = headersList.get('cookie')?.match(/NEXT_LOCALE=([^;]+)/)?.[1];
    
    if (cookieLocale && routing.locales.includes(cookieLocale as any)) {
      locale = cookieLocale;
    } else {
      locale = routing.defaultLocale;
    }
  }

  return {
    locale,
    messages: (await import(`./messages/${locale}.json`)).default
  };
});
