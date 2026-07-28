import { apiClient } from '@/lib/api/client';
import { bindApiKeyService, getApiKeyByCode, getManagerApiKeys, getParentQuotaApplyInfo, renameApiKey } from '../apiKeys';

jest.mock('@/lib/api/client', () => ({
  apiClient: {
    get: jest.fn(),
    post: jest.fn(),
  },
}));

describe('getManagerApiKeys', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    jest.mocked(apiClient.get).mockResolvedValue({ data: [], has_more: false, total: 0 });
  });

  it('queries top-level AKs by the current manager without excluding personal owners', async () => {
    await getManagerApiKeys(2, '1001', 'demo');

    expect(apiClient.get).toHaveBeenCalledWith('/console/apikey/page', {
      params: {
        status: 'active',
        managerCode: '1001',
        page: 2,
        searchParam: 'demo',
      },
    });
  });

  it('passes owner type filtering to the paged manager query', async () => {
    await getManagerApiKeys(3, '1001', undefined, true, 'project');

    expect(apiClient.get).toHaveBeenCalledWith('/console/apikey/page', {
      params: {
        status: 'active',
        managerCode: '1001',
        page: 3,
        onlyChild: true,
        ownerType: 'project',
      },
    });
  });

  it('queries assigned AKs by their own manager code and onlyChild filter', async () => {
    await getManagerApiKeys(1, '1001', undefined, true);

    expect(apiClient.get).toHaveBeenCalledWith('/console/apikey/page', {
      params: {
        status: 'active',
        managerCode: '1001',
        page: 1,
        onlyChild: true,
      },
    });
  });


  it('reuses the permission-protected code lookup endpoint', async () => {
    await getApiKeyByCode('ak-person');

    expect(apiClient.get).toHaveBeenCalledWith('/console/apikey/fetchByCode', {
      params: { code: 'ak-person', onlyActive: false },
    });
  });

  it('queries parent quota info using the child authorization context', async () => {
    await getParentQuotaApplyInfo('ak-child');

    expect(apiClient.get).toHaveBeenCalledWith('/console/apikey/parentQuotaInfo', {
      params: { childCode: 'ak-child' },
    });
  });

  it('reuses the original parent field update endpoints', async () => {
    jest.mocked(apiClient.post).mockResolvedValue(true);

    await renameApiKey('ak-org', '新名称');
    await bindApiKeyService('ak-org', 'new-service');

    expect(apiClient.post).toHaveBeenNthCalledWith(1, '/console/apikey/rename', { code: 'ak-org', name: '新名称' });
    expect(apiClient.post).toHaveBeenNthCalledWith(2, '/console/apikey/bindService', { code: 'ak-org', serviceId: 'new-service' });
  });

});
