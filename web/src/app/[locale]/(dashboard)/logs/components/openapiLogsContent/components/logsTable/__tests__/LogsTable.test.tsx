import { render, screen, within } from '@testing-library/react'
import type { LogEntry } from '@/lib/types/logs'
import { LogsTable } from '../LogsTable'

jest.mock('@/components/ui/pagination', () => ({
  Pagination: () => null,
}))

jest.mock('../components/LogDetailDrawer', () => ({
  LogDetailDrawer: () => null,
}))

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

describe('LogsTable cost column', () => {
  it('renders cost values and falls back for logs without cost', () => {
    render(
      <LogsTable
        data={[
          createLog({ data_info_msg_requestId: 'numeric-cost', data_info_msg_cost: 123 }),
          createLog({ data_info_msg_requestId: 'string-cost', data_info_msg_cost: '0.01' }),
          createLog({ data_info_msg_requestId: 'zero-cost', data_info_msg_cost: 0 }),
          createLog({ data_info_msg_requestId: 'without-cost' }),
        ]}
      />
    )

    expect(screen.getByRole('columnheader', { name: '费用（元）' })).toBeInTheDocument()

    const rows = screen.getAllByRole('row')
    expect(within(rows[1]).getAllByRole('cell')[4]).toHaveTextContent('1.230000')
    expect(within(rows[2]).getAllByRole('cell')[4]).toHaveTextContent('0.000100')
    expect(within(rows[3]).getAllByRole('cell')[4]).toHaveTextContent('0.000000')
    expect(within(rows[4]).getAllByRole('cell')[4]).toHaveTextContent('-')
  })
})
