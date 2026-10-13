import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { SubAkTable } from '../SubAkTable'
import { getAdminApiKeys, getApiKeyBalance, getApiKeys } from '@/lib/api/apiKeys'
import type { ApikeyInfo } from '@/lib/types/apikeys'
import type { SubAkCapability } from '../../hooks/useSubAkCapability'

jest.mock('@/lib/api/apiKeys', () => ({
  getApiKeys: jest.fn(),
  getAdminApiKeys: jest.fn(),
  getApiKeyBalance: jest.fn(),
}))

const apiKey: ApikeyInfo = {
  code: 'ak-child',
  serviceId: 'service',
  akSha: '',
  akDisplay: 'sk-child',
  name: '子密钥名称很长',
  outEntityCode: '用途标识很长',
  parentCode: 'ak-parent',
  ownerType: 'person',
  ownerCode: '1001',
  ownerName: '用户',
  roleCode: '',
  safetyLevel: 20,
  monthQuota: 50,
  status: 'active',
  remark: '子密钥备注很长',
  userId: 1001,
  managerCode: '1001',
  managerName: '当前用户',
}

const capability: SubAkCapability = {
  fetchMode: 'admin',
  backHref: '/manager',
  backLabel: 'AK 管理',
  canCreate: true,
  canEditQuota: true,
  canEditModelWhitelist: true,
  canReset: true,
  canDelete: true,
  canSetManager: true,
}

describe('SubAkTable', () => {
  it('truncates name, purpose and remark fields after five characters', async () => {
    jest.mocked(getAdminApiKeys).mockResolvedValue({
      data: [apiKey],
      has_more: false,
      total: 1,
      page: 1,
      limit: 10,
    })
    jest.mocked(getApiKeyBalance).mockResolvedValue({
      akCode: apiKey.code,
      month: '2026-07',
      cost: 0,
      quota: 50,
      balance: 50,
    })

    const onEditField = jest.fn()
    const onCopy = jest.fn()

    render(
      <SubAkTable
        ownerCode="1001"
        parentCode="ak-parent"
        capability={capability}
        onCopy={onCopy}
        onEditField={onEditField}
        onEditQuota={jest.fn()}
        onReset={jest.fn()}
        onDelete={jest.fn()}
        onSetManager={jest.fn()}
      />
    )

    expect(await screen.findByText('子密钥名称…')).toBeInTheDocument()
    expect(screen.getByText('用途标识很…')).toBeInTheDocument()
    expect(screen.getByText('子密钥备注…')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '编辑' })).not.toBeInTheDocument()
    expect(screen.getByText('ak-child')).toBeInTheDocument()
    fireEvent.click(screen.getByLabelText('复制密钥ID ak-child'))
    expect(onCopy).toHaveBeenCalledWith('ak-child')

    fireEvent.click(screen.getByLabelText('修改名称 ak-child'))
    fireEvent.click(screen.getByLabelText('修改用途标识 ak-child'))
    fireEvent.click(screen.getByLabelText('修改安全等级 ak-child'))
    fireEvent.click(screen.getByLabelText('修改备注 ak-child'))

    expect(onEditField).toHaveBeenNthCalledWith(1, expect.objectContaining({ code: apiKey.code }), 'name')
    expect(onEditField).toHaveBeenNthCalledWith(2, expect.objectContaining({ code: apiKey.code }), 'outEntityCode')
    expect(onEditField).toHaveBeenNthCalledWith(3, expect.objectContaining({ code: apiKey.code }), 'safetyLevel')
    expect(onEditField).toHaveBeenNthCalledWith(4, expect.objectContaining({ code: apiKey.code }), 'remark')
  })

  it('queries by exact code within the current parent scope', async () => {
    jest.mocked(getAdminApiKeys).mockResolvedValue({
      data: [apiKey],
      has_more: false,
      total: 1,
      page: 1,
      limit: 10,
    })
    jest.mocked(getApiKeyBalance).mockResolvedValue({
      akCode: apiKey.code,
      month: '2026-07',
      cost: 0,
      quota: 50,
      balance: 50,
    })

    render(
      <SubAkTable
        ownerCode="1001"
        parentCode="ak-parent"
        capability={capability}
        onCopy={jest.fn()}
        onEditField={jest.fn()}
        onEditQuota={jest.fn()}
        onReset={jest.fn()}
        onDelete={jest.fn()}
        onSetManager={jest.fn()}
      />
    )

    fireEvent.change(screen.getByPlaceholderText('输入完整 AK Code'), { target: { value: 'ak-child' } })

    await waitFor(() => {
      expect(getAdminApiKeys).toHaveBeenCalledWith(1, {
        searchType: 'ak',
        code: 'ak-child',
        parentCode: 'ak-parent',
      })
    })
  })

  it('keeps user-mode parent scope when querying by exact code', async () => {
    jest.mocked(getApiKeys).mockResolvedValue({
      data: [apiKey],
      has_more: false,
      total: 1,
      page: 1,
      limit: 10,
    })
    jest.mocked(getApiKeyBalance).mockResolvedValue({
      akCode: apiKey.code,
      month: '2026-07',
      cost: 0,
      quota: 50,
      balance: 50,
    })

    render(
      <SubAkTable
        ownerCode="1001"
        parentCode="ak-parent"
        capability={{ ...capability, fetchMode: 'user' }}
        onCopy={jest.fn()}
        onEditField={jest.fn()}
        onEditQuota={jest.fn()}
        onReset={jest.fn()}
        onDelete={jest.fn()}
        onSetManager={jest.fn()}
      />
    )

    fireEvent.change(screen.getByPlaceholderText('输入完整 AK Code'), { target: { value: 'ak-child' } })

    await waitFor(() => {
      expect(getApiKeys).toHaveBeenCalledWith('1001', undefined, 1, 'ak-parent', 'ak-child')
    })
  })
})
