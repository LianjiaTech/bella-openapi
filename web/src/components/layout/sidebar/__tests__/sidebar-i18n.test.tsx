import { cleanup, render, screen } from '@testing-library/react'
import { LanguageProvider, useLanguage } from '@/components/providers/language-provider'
import { API_KEY_NAV_ITEM } from '../sidebar-active'

let mockLocale = 'zh-CN'

jest.mock('next-intl', () => {
  const messages = {
    'zh-CN': require('@/i18n/messages/zh-CN.json'),
    'en-US': require('@/i18n/messages/en-US.json'),
  }

  return {
    useLocale: () => mockLocale,
    useTranslations: (namespace: string) => (key: string) =>
      messages[mockLocale as keyof typeof messages][namespace]?.[key] ?? key,
  }
})

jest.mock('@/i18n/routing', () => ({
  usePathname: () => '/manager',
  useRouter: () => ({ replace: jest.fn() }),
}))

function ApiKeyNavigationLabel() {
  const { t } = useLanguage()
  return <span>{t(API_KEY_NAV_ITEM.label)}</span>
}

describe('API key sidebar translation', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  afterEach(() => {
    cleanup()
  })

  it.each([
    ['zh-CN', 'API Keys 管理'],
    ['en-US', 'API Keys Management'],
  ])('renders the %s first-level label through LanguageProvider.t', (locale, expected) => {
    mockLocale = locale

    render(
      <LanguageProvider>
        <ApiKeyNavigationLabel />
      </LanguageProvider>
    )

    expect(screen.getByText(expected)).toBeInTheDocument()
  })
})
