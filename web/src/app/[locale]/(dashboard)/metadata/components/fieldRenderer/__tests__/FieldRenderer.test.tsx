import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
jest.mock('../components/PriceRangeConfig/PriceRangeConfig', () => ({
  PriceRangeConfig: () => null,
}))

import { FieldRenderer } from '../FieldRenderer'

const sessionIdSchema = {
  code: 'sessionId',
  name: 'SessionId Header',
  valueType: 'string',
  selections: [],
}

describe('FieldRenderer sessionId', () => {
  it('uses the configured label and descriptive SessionId Header placeholder', async () => {
    const onChange = jest.fn()
    const user = userEvent.setup()

    render(
      <FieldRenderer
        mode="create"
        schema={sessionIdSchema}
        value=""
        onChange={onChange}
      />
    )

    expect(screen.getByText('SessionId Header')).toBeInTheDocument()
    const input = screen.getByPlaceholderText('如：X-Prompt-Cache-Id')

    await user.type(input, 'X-Prompt-Cache-Id')
    expect(onChange).toHaveBeenCalled()
  })

  it('keeps zero visible and emits undefined when a number field is cleared', async () => {
    const onChange = jest.fn()
    const user = userEvent.setup()

    render(
      <FieldRenderer
        mode="create"
        schema={{
          code: 'inputAudioPricePerSecond',
          name: '输入音频素材单价（元/秒）',
          valueType: 'number',
          selections: [],
        }}
        value={0}
        onChange={onChange}
      />
    )

    const input = screen.getByPlaceholderText('输入 输入音频素材单价（元/秒）')
    expect(input).toHaveValue(0)

    await user.clear(input)
    expect(onChange).toHaveBeenLastCalledWith(undefined)
  })
})
