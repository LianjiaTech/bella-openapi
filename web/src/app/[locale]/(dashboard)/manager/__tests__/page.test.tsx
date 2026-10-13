import { render, screen, within } from '@testing-library/react'
import ManagerPage from '../page'

jest.mock('@/components/layout/top-bar', () => ({
  TopBar: ({ action }: { action?: React.ReactNode }) => (
    <header aria-label="页面标题栏">
      {action}
      <button type="button">修改主题颜色</button>
    </header>
  ),
}))

jest.mock('../components/CreateApiKeyApplyButton', () => ({
  CreateApiKeyApplyButton: () => <button type="button">创建 AK</button>,
}))

jest.mock('../components/ManagedKeysTable', () => ({
  ManagedKeysTable: () => <div>AK 列表</div>,
}))

jest.mock('@/app/[locale]/(dashboard)/apikey/components/ApiKeyResetDialog', () => ({
  ApiKeyResetDialog: () => null,
}))

jest.mock('@/app/[locale]/(dashboard)/apikey/components/ApiKeyCreatedDialog', () => ({
  ApiKeyCreatedDialog: () => null,
}))

jest.mock('@/app/[locale]/(dashboard)/apikey/components/UpdateSafeLevel', () => ({
  UpdateSafeLevel: () => null,
}))

jest.mock('@/app/[locale]/(dashboard)/apikey/components/ManagerDialog', () => ({
  ManagerDialog: () => null,
}))

jest.mock('@/app/[locale]/(dashboard)/apikey/components/ApiKeyDeleteDialog', () => ({
  ApiKeyDeleteDialog: () => null,
}))

jest.mock('@/app/[locale]/(dashboard)/(admin)/apikey-admin/components/ApiKeyHistoryDialog', () => ({
  ApiKeyHistoryDialog: () => null,
}))

jest.mock('@/components/providers/auth-provider', () => ({
  useAuth: () => ({ user: { userId: 1001, userName: '测试用户' } }),
}))

describe('ManagerPage', () => {
  it('places the create AK action in the top bar beside the theme button', () => {
    render(<ManagerPage />)

    const topBar = screen.getByLabelText('页面标题栏')
    expect(within(topBar).getByRole('button', { name: '创建 AK' })).toBeInTheDocument()
    expect(Array.from(topBar.children).map(child => child.textContent)).toEqual([
      '创建 AK',
      '修改主题颜色',
    ])
    expect(screen.getAllByRole('button', { name: '创建 AK' })).toHaveLength(1)
  })
})
