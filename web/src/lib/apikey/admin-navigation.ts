export type AdminApiKeySearchType = 'ak' | 'owner' | 'code' | 'manager'
export type AdminApiKeyOwnerTypeFilter = 'all' | 'person' | 'org' | 'project'

export interface AdminApiKeyListState {
  searchQuery: string
  searchType: AdminApiKeySearchType
  ownerTypeFilter: AdminApiKeyOwnerTypeFilter
  page: number
}

const DEFAULT_LIST_STATE: AdminApiKeyListState = {
  searchQuery: '',
  searchType: 'ak',
  ownerTypeFilter: 'all',
  page: 1,
}

function isAdminSearchType(value: string | null): value is AdminApiKeySearchType {
  return value === 'ak' || value === 'owner' || value === 'code' || value === 'manager'
}

function isOwnerTypeFilter(value: string | null): value is AdminApiKeyOwnerTypeFilter {
  return value === 'all' || value === 'person' || value === 'org' || value === 'project'
}

function parsePage(value: string | null): number {
  const page = Number(value)
  return Number.isInteger(page) && page > 0 ? page : DEFAULT_LIST_STATE.page
}

export function parseAdminApiKeyListState(searchParams: Pick<URLSearchParams, 'get'>): AdminApiKeyListState {
  const searchType = searchParams.get('searchType')
  const ownerTypeFilter = searchParams.get('ownerType')

  return {
    searchQuery: searchParams.get('searchQuery') ?? '',
    searchType: isAdminSearchType(searchType) ? searchType : DEFAULT_LIST_STATE.searchType,
    ownerTypeFilter: isOwnerTypeFilter(ownerTypeFilter) ? ownerTypeFilter : DEFAULT_LIST_STATE.ownerTypeFilter,
    page: parsePage(searchParams.get('page')),
  }
}

export function buildAdminApiKeyListHref(state: AdminApiKeyListState): string {
  const params = new URLSearchParams()
  const searchQuery = state.searchQuery.trim()

  if (searchQuery) {
    params.set('searchQuery', searchQuery)
  }
  if (state.searchType !== DEFAULT_LIST_STATE.searchType || searchQuery) {
    params.set('searchType', state.searchType)
  }
  if (state.ownerTypeFilter !== DEFAULT_LIST_STATE.ownerTypeFilter) {
    params.set('ownerType', state.ownerTypeFilter)
  }
  if (state.page !== DEFAULT_LIST_STATE.page) {
    params.set('page', String(state.page))
  }

  const queryString = params.toString()
  return queryString ? `/apikey-admin?${queryString}` : '/apikey-admin'
}

export function buildAdminSubAkHref(akCode: string, backHref: string): string {
  const params = new URLSearchParams({
    viewer: 'admin',
    backHref,
  })

  return `/apikey/sub-ak/${encodeURIComponent(akCode)}?${params.toString()}`
}

export function resolveSubAkBackHref(requestedBackHref: string | null, fallbackBackHref: string): string {
  if (!requestedBackHref || !requestedBackHref.startsWith('/') || requestedBackHref.startsWith('//')) {
    return fallbackBackHref
  }

  try {
    const base = 'http://agentrix.local'
    const requested = new URL(requestedBackHref, base)
    const fallback = new URL(fallbackBackHref, base)

    if (requested.origin !== base || requested.pathname !== fallback.pathname) {
      return fallbackBackHref
    }

    return `${requested.pathname}${requested.search}${requested.hash}`
  } catch {
    return fallbackBackHref
  }
}
