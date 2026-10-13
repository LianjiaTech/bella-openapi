import { render, screen, waitFor } from '@testing-library/react'
import {
  getChannelInfoSchema,
  getPriceInfoSchema,
  listProtocols,
} from '@/lib/api/metadata'
jest.mock('../fieldRenderer/FieldRenderer', () => ({
  FieldRenderer: ({ schema, value, onChange }: any) => (
    <input
      aria-label={schema.name}
      placeholder={schema.code === 'sessionId' ? '如：X-Prompt-Cache-Id' : schema.name}
      value={value || ''}
      onChange={(event) => onChange(event.target.value)}
    />
  ),
}))

import { ChannelConfigDialog } from '../ChannelConfigDialog'

jest.mock('@/lib/api/metadata', () => ({
  listProtocols: jest.fn(),
  getPriceInfoSchema: jest.fn(),
  getChannelInfoSchema: jest.fn(),
  createChannel: jest.fn(),
  updateChannel: jest.fn(),
  createPrivateChannel: jest.fn(),
  updatePrivateChannel: jest.fn(),
}))

const mockedListProtocols = listProtocols as jest.MockedFunction<typeof listProtocols>
const mockedGetPriceInfoSchema = getPriceInfoSchema as jest.MockedFunction<typeof getPriceInfoSchema>
const mockedGetChannelInfoSchema = getChannelInfoSchema as jest.MockedFunction<typeof getChannelInfoSchema>

function channel(channelCode: string, channelInfo: string, protocol = 'OpenAIAdaptor') {
  return {
    channelCode,
    channelInfo,
    protocol,
    priceInfo: '{}',
    priority: '1',
    url: 'https://example.com/v1/chat/completions',
    supplier: 'example',
    dataDestination: 'mainland',
    trialEnabled: 0,
    billingEnabled: 1,
    queueMode: 0,
    queueName: '',
  }
}

describe('ChannelConfigDialog channelInfo isolation', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    mockedListProtocols.mockResolvedValue({ OpenAIAdaptor: 'OpenAI' })
    mockedGetPriceInfoSchema.mockResolvedValue({ params: [] })
    mockedGetChannelInfoSchema.mockResolvedValue({
      params: [{
        code: 'sessionId',
        name: 'SessionId Header',
        valueType: 'string',
        selections: [],
      }],
    })
  })

  it('does not reuse SessionId Header when switching channels', async () => {
    const { rerender } = render(
      <ChannelConfigDialog
        open
        mode="edit"
        modelName="test-model"
        initialData={channel('channel-a', '{"sessionId":"A"}')}
        onOpenChange={jest.fn()}
      />
    )

    expect(await screen.findByPlaceholderText('如：X-Prompt-Cache-Id')).toHaveValue('A')

    rerender(
      <ChannelConfigDialog
        open
        mode="edit"
        modelName="test-model"
        initialData={channel('channel-b', '')}
        onOpenChange={jest.fn()}
      />
    )

    await waitFor(() => {
      expect(screen.getByPlaceholderText('如：X-Prompt-Cache-Id')).toHaveValue('')
    })
  })

  it('does not request channel schema when protocol is empty', async () => {
    render(
      <ChannelConfigDialog
        open
        mode="edit"
        modelName="test-model"
        initialData={channel('channel-empty-protocol', '{"sessionId":"A"}', '  ')}
        onOpenChange={jest.fn()}
      />
    )

    await waitFor(() => {
      expect(mockedGetChannelInfoSchema).not.toHaveBeenCalled()
    })
  })
})
