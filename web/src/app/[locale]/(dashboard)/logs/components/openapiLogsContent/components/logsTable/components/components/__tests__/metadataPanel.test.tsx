import { render, screen } from '@testing-library/react'
import type { LogEntry } from '@/lib/types/logs'
import { MetadataPanel } from '../metadataPanel'

const createLog = (overrides: Partial<LogEntry>): LogEntry => ({
  data_info_msg_metrics: '{}',
  data_info_msg_requestId: 'request-id',
  data_info_msg_forwardUrl: '',
  data_info_msg_bellaTraceId: '',
  data_info_msg_request: '{}',
  data_info_msg_user: '',
  data_info_msg_requestTime: 0,
  data_info_msg_response: '{}',
  data_info_msg_model: 'test-model',
  ...overrides,
})

describe('MetadataPanel cost field', () => {
  it('renders cost in yuan with six decimal places', () => {
    render(<MetadataPanel data={createLog({ data_info_msg_cost: 123 })} />)

    expect(screen.getByText('费用（元）')).toBeInTheDocument()
    expect(screen.getByText('1.230000')).toBeInTheDocument()
  })

  it('renders a fallback when cost is missing', () => {
    render(<MetadataPanel data={createLog({})} />)

    expect(screen.getByText('费用（元）')).toBeInTheDocument()
    expect(screen.getAllByText('-')).not.toHaveLength(0)
  })
})
