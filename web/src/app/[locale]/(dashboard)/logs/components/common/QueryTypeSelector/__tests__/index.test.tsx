import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { getManagerApiKeys, getOwnedOrManagedApiKeys } from '@/lib/api/apiKeys';
import { QueryTypeSelector } from '../index';

jest.mock('@/lib/api/apiKeys', () => ({
  getManagerApiKeys: jest.fn(),
  getOwnedOrManagedApiKeys: jest.fn(),
}));

jest.mock('@/components/providers/auth-provider', () => ({
  useAuth: () => ({ user: { userId: 1001 } }),
}));

jest.mock('@/components/providers/language-provider', () => ({
  useLanguage: () => ({ t: (key: string) => key }),
}));

jest.mock('@/components/common/prefixed-input', () => ({
  PrefixedInput: ({ onSuggestionsOpenChange }: { onSuggestionsOpenChange: (open: boolean) => void }) => (
    <button type="button" onClick={() => onSuggestionsOpenChange(true)}>open suggestions</button>
  ),
}));

describe('QueryTypeSelector AK suggestions', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    jest.mocked(getOwnedOrManagedApiKeys).mockResolvedValue({ data: [], has_more: false, total: 0, page: 1, limit: 10 });
    jest.mocked(getManagerApiKeys).mockResolvedValue({ data: [], has_more: false, total: 0, page: 1, limit: 10 });
  });

  it('uses owner-or-manager for top-level AKs and manager-only for child AKs', async () => {
    render(
      <QueryTypeSelector
        queryType="AK Code"
        queryValue=""
        onQueryTypeChange={jest.fn()}
        onQueryValueChange={jest.fn()}
        availableTypes={['AK Code']}
        enableAkCodeSuggestions
      />
    );

    fireEvent.click(screen.getByRole('button', { name: 'open suggestions' }));

    await waitFor(() => {
      expect(getOwnedOrManagedApiKeys).toHaveBeenCalledWith(1, '1001', undefined);
      expect(getManagerApiKeys).toHaveBeenCalledWith(1, '1001', undefined, true);
    });
  });
});
