import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { toast } from 'sonner';
import { applyApiKey } from '@/lib/api/apiKeys';
import { CreateApiKeyApplyButton } from '../CreateApiKeyApplyButton';

jest.mock('@/components/providers/auth-provider', () => ({
  useAuth: () => ({ user: { userId: 1001, userName: '测试用户' } }),
}));

jest.mock('@/lib/api/apiKeys', () => ({
  applyApiKey: jest.fn(),
}));

jest.mock('sonner', () => ({
  toast: {
    error: jest.fn(),
  },
}));

jest.mock('@/app/[locale]/(dashboard)/apikey/components/ApiKeyTypeSelectionDialog', () => ({
  ApiKeyTypeSelectionDialog: ({ isOpen, onSelectPersonal }: { isOpen: boolean; onSelectPersonal: () => void }) =>
    isOpen ? <button onClick={onSelectPersonal}>创建个人 APIKey</button> : null,
}));

describe('CreateApiKeyApplyButton', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('shows the backend business error when personal API key creation fails', async () => {
    jest.mocked(applyApiKey).mockRejectedValue(new Error('个人父AK数量上限为10个'));
    const user = userEvent.setup();

    render(<CreateApiKeyApplyButton onPersonalCreated={jest.fn()} />);
    await user.click(screen.getByRole('button', { name: '创建 AK' }));
    await user.click(screen.getByRole('button', { name: '创建个人 APIKey' }));

    expect(toast.error).toHaveBeenCalledWith('个人父AK数量上限为10个');
  });
});
