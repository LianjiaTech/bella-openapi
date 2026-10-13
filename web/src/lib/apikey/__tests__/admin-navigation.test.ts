import {
  buildAdminApiKeyListHref,
  buildAdminSubAkHref,
  parseAdminApiKeyListState,
  resolveSubAkBackHref,
} from '../admin-navigation'

describe('admin api key navigation', () => {
  it('parses admin list state from URL search params', () => {
    const state = parseAdminApiKeyListState(
      new URLSearchParams('searchQuery=ak-001&searchType=code&ownerType=org&page=3')
    )

    expect(state).toEqual({
      searchQuery: 'ak-001',
      searchType: 'code',
      ownerTypeFilter: 'org',
      page: 3,
    })
  })

  it('falls back to default state for invalid URL search params', () => {
    const state = parseAdminApiKeyListState(
      new URLSearchParams('searchType=unknown&ownerType=unknown&page=-1')
    )

    expect(state).toEqual({
      searchQuery: '',
      searchType: 'ak',
      ownerTypeFilter: 'all',
      page: 1,
    })
  })

  it('builds admin list and sub-ak hrefs that preserve list context', () => {
    const backHref = buildAdminApiKeyListHref({
      searchQuery: '主密钥',
      searchType: 'ak',
      ownerTypeFilter: 'person',
      page: 2,
    })

    expect(backHref).toBe('/apikey-admin?searchQuery=%E4%B8%BB%E5%AF%86%E9%92%A5&searchType=ak&ownerType=person&page=2')

    const subAkHref = buildAdminSubAkHref('ak-code-001', backHref)
    expect(subAkHref).toBe(
      '/apikey/sub-ak/ak-code-001?viewer=admin&backHref=%2Fapikey-admin%3FsearchQuery%3D%25E4%25B8%25BB%25E5%25AF%2586%25E9%2592%25A5%26searchType%3Dak%26ownerType%3Dperson%26page%3D2'
    )
  })

  it('only resolves back hrefs that match the fallback path', () => {
    expect(
      resolveSubAkBackHref('/apikey-admin?searchQuery=ak-001&searchType=code&page=2', '/apikey-admin')
    ).toBe('/apikey-admin?searchQuery=ak-001&searchType=code&page=2')

    expect(resolveSubAkBackHref('/manager?searchQuery=ak-001', '/apikey-admin')).toBe('/apikey-admin')
    expect(resolveSubAkBackHref('https://example.com/apikey-admin', '/apikey-admin')).toBe('/apikey-admin')
    expect(resolveSubAkBackHref('//example.com/apikey-admin', '/apikey-admin')).toBe('/apikey-admin')
  })
})
