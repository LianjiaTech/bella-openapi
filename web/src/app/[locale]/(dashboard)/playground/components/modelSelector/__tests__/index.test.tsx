import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { Model } from '@/lib/types/openapi'
import { ModelSelector } from '..'

const mockUseModelList = jest.fn()
const mockGetSearchParam = jest.fn()

jest.mock('../../../hooks/useModelList', () => ({
  useModelList: (...args: unknown[]) => mockUseModelList(...args),
}))

jest.mock('next/navigation', () => ({
  useSearchParams: () => ({ get: mockGetSearchParam }),
}))

jest.mock('@/components/ui/combobox', () => ({
  Combobox: ({ options, value, onValueChange, disabled }: {
    options: Array<{ value: string; label: string }>
    value: string
    onValueChange: (value: string) => void
    disabled: boolean
  }) => (
    <div>
      <span data-testid="value">{value}</span>
      <span data-testid="disabled">{String(disabled)}</span>
      {options.map((option) => (
        <button key={option.value} onClick={() => onValueChange(option.value)}>
          {option.label}
        </button>
      ))}
      <button onClick={() => onValueChange('invalid-model')}>Invalid model</button>
    </div>
  ),
}))

const models = [
  { modelName: 'model-a', ownerName: 'Owner A' },
  { modelName: 'model-b', ownerName: 'Owner B' },
] as Model[]

describe('ModelSelector', () => {
  beforeEach(() => {
    mockUseModelList.mockReturnValue({ models, loading: false, error: null })
    mockGetSearchParam.mockReturnValue(null)
  })

  it('falls back to the first available model for an invalid URL model', async () => {
    const onValueChange = jest.fn()
    mockGetSearchParam.mockReturnValue('missing-model')

    render(<ModelSelector value="" onValueChange={onValueChange} />)

    await waitFor(() => expect(onValueChange).toHaveBeenCalledWith('model-a'))
  })

  it('accepts a valid URL model', async () => {
    const onValueChange = jest.fn()
    mockGetSearchParam.mockReturnValue('model-b')

    render(<ModelSelector value="" onValueChange={onValueChange} />)

    await waitFor(() => expect(onValueChange).toHaveBeenCalledWith('model-b'))
  })

  it('forwards valid selections and ignores values outside the model list', async () => {
    const user = userEvent.setup()
    const onValueChange = jest.fn()

    render(<ModelSelector value="model-a" onValueChange={onValueChange} />)

    await user.click(screen.getByRole('button', { name: 'Invalid model' }))
    expect(onValueChange).not.toHaveBeenCalled()

    await user.click(screen.getByRole('button', { name: 'model-b' }))
    expect(onValueChange).toHaveBeenCalledWith('model-b')
  })

  it('does not reapply the URL model after initialization', async () => {
    const onValueChange = jest.fn()
    mockGetSearchParam.mockReturnValue('model-b')

    const { rerender } = render(
      <ModelSelector value="" onValueChange={onValueChange} />
    )
    await waitFor(() => expect(onValueChange).toHaveBeenCalledWith('model-b'))

    onValueChange.mockClear()
    rerender(<ModelSelector value="model-a" onValueChange={onValueChange} />)

    expect(onValueChange).not.toHaveBeenCalled()
  })
})
