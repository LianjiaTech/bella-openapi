import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ApiKeyTypeSelectionDialog } from '../ApiKeyTypeSelectionDialog'

describe('ApiKeyTypeSelectionDialog', () => {
  it('can show only organization and project apply options', async () => {
    const user = userEvent.setup()
    const handleSelectApplyType = jest.fn()

    render(
      <ApiKeyTypeSelectionDialog
        isOpen
        onClose={jest.fn()}
        onSelectApplyType={handleSelectApplyType}
        showPersonalOption={false}
        description="根据调用场景选择组织或项目 APIKey，进入对应 BPM 创建申请流程。"
      />
    )

    expect(screen.getByRole('dialog', { name: '选择 APIKey 类型' })).toBeInTheDocument()
    expect(screen.getByText('组织 APIKey')).toBeInTheDocument()
    expect(screen.getByText('项目 APIKey')).toBeInTheDocument()
    expect(screen.queryByText('个人 APIKey')).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: '文档' })).toHaveAttribute(
      'href',
      'https://doc.weixin.qq.com/doc/w3_AH4AOQbyANMCNUKaCWhoeS1iwRqRw?scode=AJMA1Qc4AAww35tgpPAH4AOQbyANM'
    )

    await user.click(screen.getByRole('button', { name: /组织 APIKey/ }))
    await user.click(screen.getByRole('button', { name: /项目 APIKey/ }))

    expect(handleSelectApplyType).toHaveBeenNthCalledWith(1, 'org')
    expect(handleSelectApplyType).toHaveBeenNthCalledWith(2, 'project')
  })
})
