'use client'

import { TopBar } from "@/components/layout/top-bar";
import { Button } from "@/components/common/button";
import { Plus, AlertCircle } from "lucide-react";
import { useState, useEffect, useCallback, useReducer } from "react";
import { getApiKeys, getApiKeyBalance, applyApiKey, resetApiKey, deleteApiKey, renameApiKey, bindApiKeyService } from "@/lib/api/apiKeys";
import { ApikeyInfo, ApiKeyBalance } from "@/lib/types/apikeys";
import { KeysTable } from "./components/KeysTable";
import { Pagination } from "@/components/ui/pagination";
import { Input } from "@/components/common/input";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from "@/components/common/dialog";
import { ApiKeyCreatedDialog } from "./components/ApiKeyCreatedDialog";
import { ApiKeyResetDialog } from "./components/ApiKeyResetDialog";
import { ApiKeyTransferDialog } from "./components/apiKeyTransferDialog/ApiKeyTransferDialog";
import { ApiKeyDeleteDialog } from "./components/ApiKeyDeleteDialog";
import { UpdateSafeLevel } from "./components/UpdateSafeLevel";
import { SearchInput } from "./components/SearchInput";
import { copyToClipboard } from "@/lib/utils/clipboard";
import { toast } from "sonner";
import { useAuth } from "@/components/providers/auth-provider";
import { Alert, AlertTitle } from "@/components/common/alert";
import config from "@/config";

// 对话框状态类型
type DialogState = {
  created: {
    isOpen: boolean;
    apiKey: string;
  };
  reset: {
    isOpen: boolean;
    akCode: string;
    loading: boolean;
  };
  transfer: {
    isOpen: boolean;
    apiKey: ApikeyInfo | null;
  };
  delete: {
    isOpen: boolean;
    akCode: string;
    loading: boolean;
  };
  edit: {
    isOpen: boolean;
    field: 'name' | 'service' | null;
    code: string;
    value: string;
    loading: boolean;
  };
  safeLevel: {
    isOpen: boolean;
    akCode: string;
  };
};

// 对话框操作类型
type DialogAction =
  | { type: 'OPEN_CREATED_DIALOG'; payload: string }
  | { type: 'CLOSE_CREATED_DIALOG' }
  | { type: 'OPEN_RESET_DIALOG'; payload: string }
  | { type: 'CLOSE_RESET_DIALOG' }
  | { type: 'SET_RESET_LOADING'; payload: boolean }
  | { type: 'OPEN_TRANSFER_DIALOG'; payload: ApikeyInfo }
  | { type: 'CLOSE_TRANSFER_DIALOG' }
  | { type: 'OPEN_DELETE_DIALOG'; payload: string }
  | { type: 'CLOSE_DELETE_DIALOG' }
  | { type: 'SET_DELETE_LOADING'; payload: boolean }
  | { type: 'OPEN_EDIT_DIALOG'; payload: { field: 'name' | 'service'; code: string; value: string } }
  | { type: 'CLOSE_EDIT_DIALOG' }
  | { type: 'SET_EDIT_VALUE'; payload: string }
  | { type: 'SET_EDIT_LOADING'; payload: boolean }
  | { type: 'OPEN_SAFE_LEVEL_DIALOG'; payload: string }
  | { type: 'CLOSE_SAFE_LEVEL_DIALOG' };

// 对话框初始状态
const initialDialogState: DialogState = {
  created: {
    isOpen: false,
    apiKey: '',
  },
  reset: {
    isOpen: false,
    akCode: '',
    loading: false,
  },
  transfer: {
    isOpen: false,
    apiKey: null,
  },
  delete: {
    isOpen: false,
    akCode: '',
    loading: false,
  },
  edit: {
    isOpen: false,
    field: null,
    code: '',
    value: '',
    loading: false,
  },
  safeLevel: {
    isOpen: false,
    akCode: '',
  },
};

// 对话框 reducer
function dialogReducer(state: DialogState, action: DialogAction): DialogState {
  switch (action.type) {
    case 'OPEN_CREATED_DIALOG':
      return { ...state, created: { isOpen: true, apiKey: action.payload } };
    case 'CLOSE_CREATED_DIALOG':
      return { ...state, created: { isOpen: false, apiKey: '' } };

    case 'OPEN_RESET_DIALOG':
      return { ...state, reset: { isOpen: true, akCode: action.payload, loading: false } };
    case 'CLOSE_RESET_DIALOG':
      return { ...state, reset: { isOpen: false, akCode: '', loading: false } };
    case 'SET_RESET_LOADING':
      return { ...state, reset: { ...state.reset, loading: action.payload } };

    case 'OPEN_TRANSFER_DIALOG':
      return { ...state, transfer: { isOpen: true, apiKey: action.payload } };
    case 'CLOSE_TRANSFER_DIALOG':
      return { ...state, transfer: { isOpen: false, apiKey: null } };

    case 'OPEN_DELETE_DIALOG':
      return { ...state, delete: { isOpen: true, akCode: action.payload, loading: false } };
    case 'CLOSE_DELETE_DIALOG':
      return { ...state, delete: { isOpen: false, akCode: '', loading: false } };
    case 'SET_DELETE_LOADING':
      return { ...state, delete: { ...state.delete, loading: action.payload } };

    case 'OPEN_EDIT_DIALOG':
      return {
        ...state,
        edit: {
          isOpen: true,
          field: action.payload.field,
          code: action.payload.code,
          value: action.payload.value,
          loading: false
        }
      };
    case 'CLOSE_EDIT_DIALOG':
      return { ...state, edit: { isOpen: false, field: null, code: '', value: '', loading: false } };
    case 'SET_EDIT_VALUE':
      return { ...state, edit: { ...state.edit, value: action.payload } };
    case 'SET_EDIT_LOADING':
      return { ...state, edit: { ...state.edit, loading: action.payload } };

    case 'OPEN_SAFE_LEVEL_DIALOG':
      return { ...state, safeLevel: { isOpen: true, akCode: action.payload } };
    case 'CLOSE_SAFE_LEVEL_DIALOG':
      return { ...state, safeLevel: { isOpen: false, akCode: '' } };

    default:
      return state;
  }
}

export default function ApiKeysPage() {
  // 列表和搜索状态
  const [apiKeys, setApiKeys] = useState<ApikeyInfo[]>([]);
  const [balances, setBalances] = useState<Record<string, ApiKeyBalance>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [page, setPage] = useState(1);
  const [hasMore, setHasMore] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [debouncedSearchQuery, setDebouncedSearchQuery] = useState("");
  const [isSearching, setIsSearching] = useState(false);

  // 对话框状态 - 使用 useReducer 管理
  const [dialogState, dispatch] = useReducer(dialogReducer, initialDialogState);

  const { user } = useAuth()

  // 搜索防抖延迟时间(毫秒)
  const SEARCH_DEBOUNCE_DELAY = 500;

  // 获取 API Keys 列表
  const fetchApiKeys = useCallback(async (currentPage: number, search: string) => {
    if (dialogState.transfer.isOpen) return;
    if (!user?.userId) return;
    try {
      setLoading(true);
      setError(null);
      const response = await getApiKeys(user?.userId?.toString() || "", search, currentPage, "");

      setApiKeys(response.data || []);
      setHasMore(response.has_more);

      // 获取每个 API Key 的余额信息
      if (response.data && response.data.length > 0) {
        response.data.forEach(async (apiKey: ApikeyInfo) => {
          try {
            const balance = await getApiKeyBalance(apiKey.code);
            setBalances(prev => ({ ...prev, [apiKey.code]: balance }));
          } catch (err) {
            console.error(`Failed to fetch balance for ${apiKey.code}:`, err);
          }
        });
      }
    } catch (err) {
      setError(err instanceof Error ? err : new Error('Failed to fetch API keys'));
    } finally {
      setLoading(false);
    }
  }, [dialogState.transfer.isOpen, user?.userId]);

  // 搜索防抖 - 500ms 延迟
  useEffect(() => {
    // 如果搜索词和防抖后的搜索词不同,说明正在搜索中
    if (searchQuery !== debouncedSearchQuery) {
      setIsSearching(true);
    }

    const timer = setTimeout(() => {
      setDebouncedSearchQuery(searchQuery);
      setIsSearching(false);
    }, SEARCH_DEBOUNCE_DELAY);

    return () => clearTimeout(timer);
  }, [searchQuery, debouncedSearchQuery]);

  // 初始加载 - 使用防抖后的搜索值
  useEffect(() => {
    fetchApiKeys(page, debouncedSearchQuery);
  }, [fetchApiKeys, page, debouncedSearchQuery]);

  // 处理复制操作
  const handleCopy = useCallback(async (text: string) => {
    const success = await copyToClipboard(text);
    if (success) {
      toast.success('复制成功');
    } else {
      toast.error('复制失败，请手动复制');
    }
  }, []);

  // 处理下一页
  const handleNextPage = useCallback(() => {
    setPage(prev => prev + 1);
  }, []);

  // 处理上一页
  const handlePrevPage = useCallback(() => {
    setPage(prev => Math.max(1, prev - 1));
  }, []);

  // 处理创建新密钥
  const handleCreateApiKey = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);

      const result = await applyApiKey({
        ownerCode: user?.userId?.toString() || "",
        ownerName: user?.userName || ""
      });
      dispatch({ type: 'OPEN_CREATED_DIALOG', payload: result });
    } catch (err) {
      setError(err instanceof Error ? err : new Error('Failed to create API key'));
    } finally {
      setLoading(false);
    }
  }, [user?.userId]);

  // 处理对话框关闭
  const handleDialogClose = useCallback(() => {
    dispatch({ type: 'CLOSE_CREATED_DIALOG' });
    // 刷新列表并重置到第一页
    setPage(1);
    fetchApiKeys(1, debouncedSearchQuery);
  }, [fetchApiKeys, debouncedSearchQuery]);

  // 处理重置点击
  const handleResetClick = useCallback((akCode: string) => {
    dispatch({ type: 'OPEN_RESET_DIALOG', payload: akCode });
  }, []);

  // 处理重置确认
  const handleResetConfirm = useCallback(async () => {
    const akCode = dialogState.reset.akCode;
    if (!akCode) return;

    try {
      dispatch({ type: 'SET_RESET_LOADING', payload: true });
      setError(null);

      const newKey = await resetApiKey(akCode);

      // 关闭重置确认对话框
      dispatch({ type: 'CLOSE_RESET_DIALOG' });

      // 如果返回有效的新密钥，展示创建成功对话框
      if (newKey) {
        dispatch({ type: 'OPEN_CREATED_DIALOG', payload: newKey });
      }
    } catch (err) {
      setError(err instanceof Error ? err : new Error('重置 API Key 失败'));
      dispatch({ type: 'CLOSE_RESET_DIALOG' });
    }
  }, [dialogState.reset.akCode]);

  // 处理转交点击
  const handleTransferClick = useCallback((apiKey: ApikeyInfo) => {
    dispatch({ type: 'OPEN_TRANSFER_DIALOG', payload: apiKey });
  }, []);

  // 打开编辑名称弹窗
  const handleEditName = useCallback((code: string, currentName: string) => {
    dispatch({ type: 'OPEN_EDIT_DIALOG', payload: { field: 'name', code, value: currentName } });
  }, []);

  // 打开编辑服务名弹窗
  const handleEditService = useCallback((code: string, currentServiceId: string) => {
    dispatch({ type: 'OPEN_EDIT_DIALOG', payload: { field: 'service', code, value: currentServiceId } });
  }, []);

  // 关闭编辑弹窗
  const handleEditClose = useCallback(() => {
    dispatch({ type: 'CLOSE_EDIT_DIALOG' });
  }, []);

  // 提交编辑
  const handleEditConfirm = useCallback(async () => {
    const { code, field, value } = dialogState.edit;
    if (!code || !field) return;
    try {
      dispatch({ type: 'SET_EDIT_LOADING', payload: true });
      if (field === 'name') {
        await renameApiKey(code, value);
      } else {
        await bindApiKeyService(code, value);
      }
      handleEditClose();
      fetchApiKeys(page, debouncedSearchQuery);
    } catch (err) {
      setError(err instanceof Error ? err : new Error('修改失败'));
    } finally {
      dispatch({ type: 'SET_EDIT_LOADING', payload: false });
    }
  }, [dialogState.edit, handleEditClose, fetchApiKeys, page, debouncedSearchQuery]);

  // 处理删除点击
  const handleDeleteClick = useCallback((akCode: string) => {
    dispatch({ type: 'OPEN_DELETE_DIALOG', payload: akCode });
  }, []);

  // 处理删除确认
  const handleDeleteConfirm = useCallback(async () => {
    const akCode = dialogState.delete.akCode;
    if (!akCode) return;

    try {
      dispatch({ type: 'SET_DELETE_LOADING', payload: true });
      setError(null);

      // 调用删除API
      await deleteApiKey(akCode);

      // 关闭删除确认对话框
      dispatch({ type: 'CLOSE_DELETE_DIALOG' });

      // 刷新列表
      fetchApiKeys(page, debouncedSearchQuery);
    } catch (err) {
      setError(err instanceof Error ? err : new Error('删除 API Key 失败'));
      dispatch({ type: 'CLOSE_DELETE_DIALOG' });
    }
  }, [dialogState.delete.akCode, page, debouncedSearchQuery, fetchApiKeys]);

  // 处理编辑安全等级点击
  const handleEditSafetyLevelClick = useCallback((akCode: string) => {
    dispatch({ type: 'OPEN_SAFE_LEVEL_DIALOG', payload: akCode });
  }, []);

  // 处理安全等级更新成功
  const handleSafeLevelUpdateSuccess = useCallback(() => {
    // 刷新列表
    fetchApiKeys(page, debouncedSearchQuery);
  }, [page, debouncedSearchQuery, fetchApiKeys]);

  return (
    <div>
      <TopBar title="API Key管理" description="管理您的API密钥,设置额度和安全等级" />
      <div className="p-8">
      {config.tips.apiKeyPageTip && (
        <Alert variant="destructive" className="mb-4">
          <AlertTitle className="text-sm font-normal">{config.tips.apiKeyPageTip}</AlertTitle>
        </Alert>
      )}

        {/* 顶部操作栏 */}
        <div className="flex items-center justify-between mb-4">
          <Button onClick={handleCreateApiKey} className="cursor-pointer" disabled={loading}>
            <Plus className="h-4 w-4" />
            创建新密钥
          </Button>

          {/* 搜索框 */}
          <SearchInput
            value={searchQuery}
            onChange={(value:string) => {
              setSearchQuery(value);
              setPage(1); // 重置到第一页
            }}
            placeholder="搜索 API Key..."
            isSearching={isSearching}
          />
        </div>

        {/* 错误提示 */}
        {error && (
          <div className="mb-4 rounded-lg border border-red-500/20 bg-red-500/10 p-4">
            <div className="flex items-center gap-2">
              <AlertCircle className="h-5 w-5 flex-shrink-0 text-red-500" />
              <p className="text-sm text-red-500">{error.message}</p>
            </div>
          </div>
        )}

        {/* 表格容器 */}
        <div className="overflow-y-auto h-[calc(100vh-10rem)] [overscroll-behavior:contain]">
          <KeysTable
            apiKeys={apiKeys}
            balances={balances}
            loading={loading}
            searchQuery={debouncedSearchQuery}
            onCopy={handleCopy}
            onReset={handleResetClick}
            onTransfer={handleTransferClick}
            onDelete={handleDeleteClick}
            onEditName={handleEditName}
            onEditService={handleEditService}
            onEditSafetyLevel={handleEditSafetyLevelClick}
          />

          {/* 分页控件 */}
          {!loading && apiKeys.length > 0 && (
            <Pagination
              currentPage={page}
              hasMore={hasMore}
              onPrevPage={handlePrevPage}
              onNextPage={handleNextPage}
              loading={loading}
            />
          )}
        </div>

        {/* API Key 创建成功对话框 */}
        <ApiKeyCreatedDialog
          apiKey={dialogState.created.apiKey}
          isOpen={dialogState.created.isOpen}
          onClose={handleDialogClose}
          onCopy={handleCopy}
        />

        {/* API Key 重置确认对话框 */}
        <ApiKeyResetDialog
          isOpen={dialogState.reset.isOpen}
          onClose={() => dispatch({ type: 'CLOSE_RESET_DIALOG' })}
          onConfirm={handleResetConfirm}
          loading={dialogState.reset.loading}
        />

        {/* API Key 转交对话框 */}
        <ApiKeyTransferDialog
          isOpen={dialogState.transfer.isOpen}
          onClose={() => dispatch({ type: 'CLOSE_TRANSFER_DIALOG' })}
          apiKeyName={dialogState.transfer.apiKey?.name}
          apiKeyCode={dialogState.transfer.apiKey?.code}
          akDisplay={dialogState.transfer.apiKey?.akDisplay}
        />

        {/* API Key 删除确认对话框 */}
        <ApiKeyDeleteDialog
          isOpen={dialogState.delete.isOpen}
          onClose={() => dispatch({ type: 'CLOSE_DELETE_DIALOG' })}
          onConfirm={handleDeleteConfirm}
          loading={dialogState.delete.loading}
        />

        {/* 编辑名称 / 服务名弹窗 */}
        <Dialog open={dialogState.edit.isOpen} onOpenChange={handleEditClose}>
          <DialogContent className="max-w-sm">
            <DialogHeader>
              <DialogTitle>{dialogState.edit.field === 'name' ? '修改名称' : '修改服务名'}</DialogTitle>
            </DialogHeader>
            <Input
              value={dialogState.edit.value}
              onChange={(e) => dispatch({ type: 'SET_EDIT_VALUE', payload: e.target.value })}
              placeholder={dialogState.edit.field === 'name' ? '请输入名称' : '请输入服务名'}
              onKeyDown={(e) => e.key === 'Enter' && handleEditConfirm()}
            />
            <DialogFooter>
              <Button variant="outline" onClick={handleEditClose} disabled={dialogState.edit.loading}>取消</Button>
              <Button onClick={handleEditConfirm} disabled={dialogState.edit.loading}>
                {dialogState.edit.loading ? '保存中...' : '确认'}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
        {/* 安全等级编辑对话框 */}
        <UpdateSafeLevel
          isOpen={dialogState.safeLevel.isOpen}
          onClose={() => dispatch({ type: 'CLOSE_SAFE_LEVEL_DIALOG' })}
          akCode={dialogState.safeLevel.akCode}
          onSuccess={handleSafeLevelUpdateSuccess}
        />
      </div>
    </div>
  );
}
