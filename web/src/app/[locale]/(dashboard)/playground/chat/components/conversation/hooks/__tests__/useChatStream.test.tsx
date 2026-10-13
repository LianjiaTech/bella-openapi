import { act, renderHook } from '@testing-library/react'
import type { Model } from '@/lib/types/openapi'
import { useChatStream } from '../useChatStream'

const mockSend = jest.fn()
const mockStart = jest.fn().mockResolvedValue(undefined)
const mockStoreState = {
  streamingMessageId: null,
  addUserMessage: jest.fn(),
  startAssistantMessage: jest.fn(),
  startBlock: jest.fn(),
  flushStreamingTextBlock: jest.fn(),
  addImageBlock: jest.fn(),
  appendTextToken: jest.fn(),
  finishStreaming: jest.fn(),
  getMessage: jest.fn(),
  setMessageStatus: jest.fn(),
  setMessageError: jest.fn(),
}
function mockUseChatStore(selector: (state: typeof mockStoreState) => unknown) {
  return selector(mockStoreState)
}

mockUseChatStore.getState = jest.fn(() => mockStoreState)
mockUseChatStore.setState = jest.fn()

jest.mock('@/lib/chat/store/chat/store', () => ({
  useChatStore: mockUseChatStore,
}))

jest.mock('@/components/providers/auth-provider', () => ({
  useAuth: () => ({ user: { userId: 'user-1' } }),
}))

jest.mock('@/lib/api/client', () => ({
  getBaseURL: () => 'https://example.test',
}))

jest.mock('@/lib/chat/transport/requestDispatcher', () => ({
  RequestDispatcher: jest.fn().mockImplementation(() => ({
    send: mockSend,
  })),
}))

const config = {
  model: 'model-a',
  temperature: 0.7,
  maxTokens: 1024,
  thinkingMode: false,
  streaming: true,
  systemPrompt: 'You are helpful.',
}

describe('useChatStream', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    mockStart.mockResolvedValue(undefined)
    mockSend.mockReturnValue({ start: mockStart, abort: jest.fn() })
  })

  it('blocks requests when the selected model does not match the config', async () => {
    const selectedModel = { modelName: 'model-b' } as Model
    const { result } = renderHook(() => useChatStream(config, selectedModel))

    await act(async () => {
      await result.current.startStream('hello')
    })

    expect(mockSend).not.toHaveBeenCalled()
    expect(mockStoreState.addUserMessage).not.toHaveBeenCalled()
  })

  it('sends the confirmed model from the available model object', async () => {
    const selectedModel = { modelName: 'model-a' } as Model
    const { result } = renderHook(() => useChatStream(config, selectedModel))

    await act(async () => {
      await result.current.startStream('hello')
    })

    expect(mockSend).toHaveBeenCalledWith(
      expect.objectContaining({
        body: expect.objectContaining({ model: 'model-a' }),
      })
    )
    expect(mockStart).toHaveBeenCalled()
  })
})
