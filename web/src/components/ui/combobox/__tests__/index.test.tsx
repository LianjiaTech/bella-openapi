import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Combobox } from '..'

const options = [
  { value: 'model-a', label: 'Model A' },
  { value: 'model-b', label: 'Model B' },
]

describe('Combobox', () => {
  it('does not commit an unmatched search in fixed-option mode', async () => {
    const user = userEvent.setup()
    const onValueChange = jest.fn()

    render(
      <Combobox
        options={options}
        value="model-a"
        onValueChange={onValueChange}
        searchPlaceholder="Search models"
      />
    )

    await user.click(screen.getByRole('combobox'))
    await user.type(screen.getByPlaceholderText('Search models'), 'missing-model')
    await user.keyboard('{Escape}')

    expect(onValueChange).not.toHaveBeenCalled()
    expect(screen.getByRole('combobox')).toHaveValue('Model A')
  })

  it('does not commit a matching search until the option is clicked', async () => {
    const user = userEvent.setup()
    const onValueChange = jest.fn()

    render(
      <Combobox
        options={options}
        value="model-a"
        onValueChange={onValueChange}
        searchPlaceholder="Search models"
      />
    )

    await user.click(screen.getByRole('combobox'))
    await user.type(screen.getByPlaceholderText('Search models'), 'model-b')
    await user.keyboard('{Escape}')

    expect(onValueChange).not.toHaveBeenCalled()
  })

  it('commits only the clicked option in fixed-option mode', async () => {
    const user = userEvent.setup()
    const onValueChange = jest.fn()

    render(
      <Combobox options={options} value="model-a" onValueChange={onValueChange} />
    )

    await user.click(screen.getByRole('combobox'))
    await user.click(screen.getByText('Model B'))

    expect(onValueChange).toHaveBeenCalledWith('model-b')
  })

  it('commits an unmatched search when custom values are enabled', async () => {
    const user = userEvent.setup()
    const onValueChange = jest.fn()

    render(
      <Combobox
        options={options}
        value="model-a"
        onValueChange={onValueChange}
        allowCustomValue
        searchPlaceholder="Search models"
      />
    )

    await user.click(screen.getByRole('combobox'))
    await user.type(screen.getByPlaceholderText('Search models'), 'custom-model')
    await user.keyboard('{Escape}')

    expect(onValueChange).toHaveBeenCalledWith('custom-model')
  })

  it('shows the placeholder for an unknown fixed value', () => {
    render(
      <Combobox options={options} value="unknown-model" placeholder="Choose model" />
    )

    expect(screen.getByRole('combobox')).toHaveValue('')
    expect(screen.getByPlaceholderText('Choose model')).toBeInTheDocument()
  })

  it('cannot be opened when disabled', async () => {
    const user = userEvent.setup()

    render(
      <Combobox
        options={options}
        disabled
        searchPlaceholder="Search models"
      />
    )

    await user.click(screen.getByRole('combobox'))

    expect(screen.getByRole('combobox')).toBeDisabled()
    expect(screen.queryByPlaceholderText('Search models')).not.toBeInTheDocument()
  })
})
