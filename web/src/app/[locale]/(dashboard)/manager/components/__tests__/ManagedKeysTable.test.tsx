import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { ManagedKeysTable } from '../ManagedKeysTable';
import { getApiKeyBalance, getParentQuotaApplyInfo, getManagerApiKeys } from '@/lib/api/apiKeys';
import { buildChildQuotaApplyUrl } from '@/lib/integrations/apiKeyQuotaApply';
import { toast } from 'sonner';
import type { ApikeyInfo } from '@/lib/types/apikeys';

Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', {
  configurable: true,
  value: jest.fn(),
});

jest.mock('@/lib/api/apiKeys', () => ({
  getManagerApiKeys: jest.fn(),
  getApiKeyBalance: jest.fn(),
  getParentQuotaApplyInfo: jest.fn(),
}));

jest.mock('@/lib/integrations/apiKeyQuotaApply', () => ({
  isApiKeyQuotaApplyEnabled: () => true,
  buildParentQuotaApplyUrl: jest.fn(() => 'https://example.com/parent'),
  buildChildQuotaApplyUrl: jest.fn(() => 'https://example.com/child'),
}));

jest.mock('sonner', () => ({
  toast: {
    error: jest.fn(),
  },
}));

jest.mock('next/link', () => {
  return function MockLink({ children, href, ...props }: React.AnchorHTMLAttributes<HTMLAnchorElement>) {
    return <a href={typeof href === 'string' ? href : ''} {...props}>{children}</a>;
  };
});

const makeApiKey = (overrides: Partial<ApikeyInfo>): ApikeyInfo => ({
  code: 'ak-default',
  serviceId: 'service',
  akSha: '',
  akDisplay: 'sk-default',
  name: '默认 AK',
  outEntityCode: '',
  parentCode: '',
  ownerType: 'person',
  ownerCode: '1001',
  ownerName: '用户',
  roleCode: '',
  safetyLevel: 20,
  monthQuota: 50,
  status: 'active',
  remark: '',
  userId: 1001,
  managerCode: '1001',
  managerName: '当前用户',
  ...overrides,
});

const managedKeys = [
  makeApiKey({ code: 'ak-person', akDisplay: 'sk-person', name: '个人父AK', ownerType: 'person' }),
  makeApiKey({ code: 'ak-org', akDisplay: 'sk-org', name: '组织父AK', ownerType: 'org', ownerCode: 'org-1', ownerName: '组织一' }),
  makeApiKey({ code: 'ak-project', akDisplay: 'sk-project', name: '项目父AK', ownerType: 'project', ownerCode: 'project-1', ownerName: '项目一' }),
];

const assignedKey = makeApiKey({
  code: 'ak-child-person',
  akDisplay: 'sk-child-person',
  name: '个人子AK',
  parentCode: 'ak-person',
  ownerType: 'person',
});

const assignedOrgKey = makeApiKey({
  code: 'ak-child-org',
  akDisplay: 'sk-child-org',
  name: '组织子AK',
  parentCode: 'ak-org-parent',
  ownerType: 'org',
  ownerCode: 'org-1',
  managerCode: '1001',
  managerName: '子 AK 管理人',
});

function renderTable() {
  const handlers = {
    onCopy: jest.fn(),
    onReset: jest.fn(),
    onEditSafetyLevel: jest.fn(),
    onSetManager: jest.fn(),
    onViewHistory: jest.fn(),
    onEditName: jest.fn(),
    onEditService: jest.fn(),
    onDelete: jest.fn(),
  };

  render(
    <ManagedKeysTable
      managerCode="1001"
      {...handlers}
    />
  );

  return handlers;
}

describe('ManagedKeysTable', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    jest.mocked(getManagerApiKeys).mockImplementation(async (_page, _managerCode, _search, onlyChild) => ({
      data: onlyChild ? [assignedKey] : managedKeys,
      has_more: false,
      total: onlyChild ? 1 : 3,
    }));
    jest.mocked(getApiKeyBalance).mockResolvedValue({
      akCode: 'ak-default',
      month: '2026-07',
      cost: 0,
      quota: 50,
      balance: 50,
    });
    jest.mocked(getParentQuotaApplyInfo).mockResolvedValue({
      code: managedKeys[0].code,
      name: managedKeys[0].name,
      ownerType: managedKeys[0].ownerType,
      managerCode: managedKeys[0].managerCode,
      managerName: managedKeys[0].managerName,
    });
  });

  it('queries managed parents and assigned children with independent conditions', async () => {
    renderTable();

    await waitFor(() => {
      expect(getManagerApiKeys).toHaveBeenCalledWith(1, '1001', undefined, undefined, undefined);
      expect(getManagerApiKeys).toHaveBeenCalledWith(1, '1001', undefined, true, undefined);
    });

    const searchInputs = screen.getAllByPlaceholderText('搜索...');
    fireEvent.change(searchInputs[0], { target: { value: '组织' } });
    expect(searchInputs[0]).toHaveValue('组织');
    expect(searchInputs[1]).toHaveValue('');
  });

  it('filters managed and assigned AKs by independent owner types', async () => {
    renderTable();

    await waitFor(() => expect(getManagerApiKeys).toHaveBeenCalledTimes(2));

    const managedFilter = screen.getByLabelText('我管理的类型筛选');
    const assignedFilter = screen.getByLabelText('分配给我的类型筛选');
    expect(managedFilter).toHaveTextContent('全部类型');
    expect(assignedFilter).toHaveTextContent('全部类型');

    fireEvent.click(managedFilter);
    fireEvent.click(await screen.findByRole('option', { name: '组织' }));

    await waitFor(() => {
      expect(getManagerApiKeys).toHaveBeenCalledWith(1, '1001', undefined, undefined, 'org');
    });
    expect(managedFilter).toHaveTextContent('组织');
    expect(assignedFilter).toHaveTextContent('全部类型');

    fireEvent.click(assignedFilter);
    fireEvent.click(await screen.findByRole('option', { name: '项目' }));

    await waitFor(() => {
      expect(getManagerApiKeys).toHaveBeenCalledWith(1, '1001', undefined, true, 'project');
    });
    expect(managedFilter).toHaveTextContent('组织');
    expect(assignedFilter).toHaveTextContent('项目');
  });

  it('shows personal, organization and project type markers in both tabs', async () => {
    renderTable();

    expect(await screen.findByText('个人父AK')).toBeInTheDocument();
    expect(screen.getByText('组织父AK')).toBeInTheDocument();
    expect(screen.getByText('项目父AK')).toBeInTheDocument();
    expect(screen.getAllByText('个人')).toHaveLength(2);
    expect(screen.getByText('组织')).toBeInTheDocument();
    expect(screen.getByText('项目')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /分配给我的/ }));
    const childRow = screen.getByText('个人子AK').closest('tr');
    expect(childRow).not.toBeNull();
    expect(within(childRow!).getByText('个人')).toBeInTheDocument();
  });

  it('truncates managed and assigned name, service and remark fields', async () => {
    const managedLongKey = makeApiKey({
      code: 'ak-managed-long',
      name: '父密钥名称很长',
      serviceId: 'managed-service',
      remark: '父密钥备注很长',
    });
    const assignedLongKey = makeApiKey({
      code: 'ak-assigned-long',
      name: '子密钥名称很长',
      serviceId: 'assigned-service',
      remark: '子密钥备注很长',
      parentCode: 'ak-managed-long',
    });
    jest.mocked(getManagerApiKeys).mockImplementation(async (_page, _managerCode, _search, onlyChild) => ({
      data: onlyChild ? [assignedLongKey] : [managedLongKey],
      has_more: false,
      total: 1,
    }));

    renderTable();

    expect(await screen.findByText('父密钥名称…')).toBeInTheDocument();
    expect(screen.getByText('manag…')).toBeInTheDocument();
    expect(screen.getByText('父密钥备注…')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /分配给我的/ }));
    expect(screen.getByText('子密钥名称…')).toBeInTheDocument();
    expect(screen.getByText('assig…')).toBeInTheDocument();
    expect(screen.getByText('子密钥备注…')).toBeInTheDocument();
  });

  it('does not allow safety level editing for assigned child AKs', async () => {
    renderTable();

    expect(await screen.findByLabelText('修改安全等级 ak-person')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /分配给我的/ }));

    const childRow = screen.getByText('个人子AK').closest('tr');
    expect(childRow).not.toBeNull();
    expect(within(childRow!).getByText('低')).toBeInTheDocument();
    expect(within(childRow!).queryByLabelText('修改安全等级 ak-child-person')).not.toBeInTheDocument();
  });

  it('hides parent quota apply for personal AK and allows personal-parent child apply', async () => {
    const openSpy = jest.spyOn(window, 'open').mockImplementation(() => null);
    renderTable();

    const personalParentRow = (await screen.findByText('个人父AK')).closest('tr');
    const orgParentRow = screen.getByText('组织父AK').closest('tr');
    expect(personalParentRow).not.toBeNull();
    expect(orgParentRow).not.toBeNull();
    expect(within(personalParentRow!).queryByRole('button', { name: '提额' })).not.toBeInTheDocument();
    expect(within(orgParentRow!).getByRole('button', { name: '提额' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /分配给我的/ }));
    const childRow = screen.getByText('个人子AK').closest('tr');
    fireEvent.click(within(childRow!).getByRole('button', { name: '提额' }));

    await waitFor(() => expect(getParentQuotaApplyInfo).toHaveBeenCalledWith('ak-child-person'));
    expect(buildChildQuotaApplyUrl).toHaveBeenCalledWith(assignedKey, expect.objectContaining({ ownerType: 'person' }));
    expect(openSpy).toHaveBeenCalledWith('https://example.com/child', '_blank', 'noopener,noreferrer');
    openSpy.mockRestore();
  });
  it('uses child manager permission when the parent has a different manager', async () => {
    jest.mocked(getManagerApiKeys).mockImplementation(async (_page, _managerCode, _search, onlyChild) => ({
      data: onlyChild ? [assignedOrgKey] : managedKeys,
      has_more: false,
      total: onlyChild ? 1 : 3,
    }));
    const parentQuotaInfo = {
      code: 'ak-org-parent',
      name: '组织父AK',
      ownerType: 'org',
      managerCode: '3003',
      managerName: '父 AK 管理人',
    };
    jest.mocked(getParentQuotaApplyInfo).mockResolvedValue(parentQuotaInfo);
    const openSpy = jest.spyOn(window, 'open').mockImplementation(() => null);
    renderTable();

    fireEvent.click(screen.getByRole('button', { name: /分配给我的/ }));
    const childRow = (await screen.findByText('组织子AK')).closest('tr');
    fireEvent.click(within(childRow!).getByRole('button', { name: '提额' }));

    await waitFor(() => expect(getParentQuotaApplyInfo).toHaveBeenCalledWith('ak-child-org'));
    expect(buildChildQuotaApplyUrl).toHaveBeenCalledWith(assignedOrgKey, parentQuotaInfo);
    expect(openSpy).toHaveBeenCalledWith('https://example.com/child', '_blank', 'noopener,noreferrer');
    openSpy.mockRestore();
  });

  it('edits managed parent name and service from field actions', async () => {
    const handlers = renderTable();

    expect(await screen.findByText('个人父AK')).toBeInTheDocument();
    fireEvent.click(screen.getByLabelText('修改名称 ak-person'));
    fireEvent.click(screen.getByLabelText('修改服务名 ak-person'));

    expect(handlers.onEditName).toHaveBeenCalledWith(managedKeys[0]);
    expect(handlers.onEditService).toHaveBeenCalledWith(managedKeys[0]);
  });

  it('shows personal parent actions in the requested order', async () => {
    renderTable();

    expect(await screen.findByText('个人父AK')).toBeInTheDocument();
    fireEvent.click(screen.getByLabelText('打开父 AK 操作菜单 ak-person'));

    const menu = screen.getByText('管理子密钥').parentElement;
    expect(menu).not.toBeNull();
    expect(Array.from(menu!.children).map(item => item.textContent?.trim())).toEqual([
      '管理子密钥',
      '变更负责人',
      '变更历史',
      '复制ak code',
      '重置',
      '删除',
    ]);
  });

  it('shows manager actions for all parent types and delete only for personal AKs', async () => {
    const handlers = renderTable();

    expect(await screen.findByText('个人父AK')).toBeInTheDocument();

    fireEvent.click(screen.getByLabelText('打开父 AK 操作菜单 ak-org'));
    expect(screen.queryByRole('button', { name: '编辑' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '变更负责人' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '转交所有权' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '删除' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '变更负责人' }));
    expect(handlers.onSetManager).toHaveBeenCalledWith(managedKeys[1]);

    fireEvent.click(screen.getByLabelText('打开父 AK 操作菜单 ak-project'));
    expect(screen.getByRole('button', { name: '变更负责人' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '删除' })).not.toBeInTheDocument();

    fireEvent.click(screen.getByLabelText('打开父 AK 操作菜单 ak-person'));
    expect(screen.queryByRole('button', { name: '编辑' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '变更负责人' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '转交所有权' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '删除' }));
    expect(handlers.onDelete).toHaveBeenCalledWith(managedKeys[0]);
  });


});
