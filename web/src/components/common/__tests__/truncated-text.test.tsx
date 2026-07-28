import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { TruncatedText } from '../truncated-text'

describe('TruncatedText', () => {
  it('shows short and empty values without truncation', () => {
    const { rerender } = render(<TruncatedText value="五字符" />)

    expect(screen.getByText('五字符')).toBeInTheDocument()

    rerender(<TruncatedText value="" />)
    expect(screen.getByText('-')).toBeInTheDocument()
  })

  it('truncates after five Unicode characters and shows the full value on hover', async () => {
    render(<TruncatedText value="名称超过五个字符" />)

    const trigger = screen.getByText('名称超过五…')
    expect(trigger).toBeInTheDocument()

    fireEvent.focus(trigger)

    await waitFor(() => {
      expect(screen.getByRole('tooltip')).toHaveTextContent('名称超过五个字符')
    })
  })
})
