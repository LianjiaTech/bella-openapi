'use client'

import { TopBar } from "@/components/layout/top-bar";
import { Button } from "@/components/common/button";
import { Plus, AlertCircle } from "lucide-react";
import { useState, useEffect, useCallback } from "react";
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

export default function ApiKeysPage() {
  const [apiKeys, setApiKeys] = useState<ApikeyInfo[]>([]);
  const [balances, setBalances] = useState<Record<string, ApiKeyBalance>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [page, setPage] = useState(1);
  const [hasMore, setHasMore] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [debouncedSearchQuery, setDebouncedSearchQuery] = useState("");
  const [isSearching, setIsSearching] = useState(false);
  const [showCreatedDialog, setShowCreatedDialog] = useState(false);
  const [newApiKey, setNewApiKey] = useState("");
  const [showResetDialog, setShowResetDialog] = useState(false);
  const [resetingAkCode, setResetingAkCode] = useState<string>("");
  const [resetting, setResetting] = useState(false);
  const [showTransferDialog, setShowTransferDialog] = useState(false);
  const [transferingApiKey, setTransferingApiKey] = useState<ApikeyInfo | null>(null);
  const [showDeleteDialog, setShowDeleteDialog] = useState(false);
  const [deletingAkCode, setDeletingAkCode] = useState<string>("");
  const [deleting, setDeleting] = useState(false);
  const [editingField, setEditingField] = useState<'name' | 'service' | null>(null);
  const [editingCode, setEditingCode] = useState<string>("");
  const [editingValue, setEditingValue] = useState<string>("");
  const [editSubmitting, setEditSubmitting] = useState(false);
  const [showSafeLevelDialog, setShowSafeLevelDialog] = useState(false);
  const [editingAkCode, setEditingAkCode] = useState<string>("");

  const { user } = useAuth();

  // 获取当前用户的 API Keys 列表
  const fetchApiKeys = useCallback(async (currentPage: number, search: string) => {
    if (showTransferDialog) return;
    if (!user?.userId) return;
    try {
      setLoading(true);
      setError(null);
      const response = await getApiKeys(user.userId.toString(), search, currentPage, "");
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
  }, [showTransferDialog, user?.userId]);

  // 搜索防抖 - 500ms 延迟
  useEffect(() => {
    // 如果搜索词和防抖后的搜索词不同,说明正在搜索中
    if (searchQuery !== debouncedSearchQuery) {
      setIsSearching(true);
    }

    const timer = setTimeout(() => {
      setDebouncedSearchQuery(searchQuery);
      setIsSearching(false);
    }, 500);

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

  const handleNextPage = useCallback(() => setPage(prev => prev + 1), []);
  const handlePrevPage = useCallback(() => setPage(prev => Math.max(1, prev - 1)), []);

  // 处理创建新密钥
  const handleCreateApiKey = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);

      const result = await applyApiKey({
        ownerCode: user?.userId?.toString() || "",
        ownerName: user?.userName || ""
      });
      setNewApiKey(result);
      setShowCreatedDialog(true);
    } catch (err) {
      setError(err instanceof Error ? err : new Error('Failed to create API key'));
    } finally {
      setLoading(false);
    }
  }, [user?.userId, user?.userName]);

  // 处理对话框关闭
  const handleDialogClose = useCallback(() => {
    setShowCreatedDialog(false);
    setNewApiKey("");
    // 刷新列表并重置到第一页
    setPage(1);
    fetchApiKeys(1, debouncedSearchQuery);
  }, [fetchApiKeys, debouncedSearchQuery]);

  // 处理重置点击
  const handleResetClick = useCallback((akCode: string) => {
    setResetingAkCode(akCode);
    setShowResetDialog(true);
  }, []);

  // 处理重置确认
  const handleResetConfirm = useCallback(async () => {
    if (!resetingAkCode) return;

    try {
      setResetting(true);
      setError(null);

      const newKey = await resetApiKey(resetingAkCode);

      // 关闭重置确认对话框
      setShowResetDialog(false);

      // 如果返回有效的新密钥，展示创建成功对话框
      if (newKey) {
        setNewApiKey(newKey);
        setShowCreatedDialog(true);
      }
    } catch (err) {
      setError(err instanceof Error ? err : new Error('重置 API Key 失败'));
      setShowResetDialog(false);
    } finally {
      setResetting(false);
      setResetingAkCode("");
    }
  }, [resetingAkCode]);

  // 处理转交点击
  const handleTransferClick = useCallback((apiKey: ApikeyInfo) => {
    setTransferingApiKey(apiKey);
    setShowTransferDialog(true);
  }, []);

  // 打开编辑名称弹窗
  const handleEditName = useCallback((code: string, currentName: string) => {
    setEditingCode(code);
    setEditingValue(currentName);
    setEditingField('name');
  }, []);

  // 打开编辑服务名弹窗
  const handleEditService = useCallback((code: string, currentServiceId: string) => {
    setEditingCode(code);
    setEditingValue(currentServiceId);
    setEditingField('service');
  }, []);

  // 关闭编辑弹窗
  const handleEditClose = useCallback(() => {
    setEditingField(null);
    setEditingCode("");
    setEditingValue("");
  }, []);

  // 提交编辑
  const handleEditConfirm = useCallback(async () => {
    if (!editingCode || !editingField) return;
    try {
      setEditSubmitting(true);
      if (editingField === 'name') {
        await renameApiKey(editingCode, editingValue);
      } else {
        await bindApiKeyService(editingCode, editingValue);
      }
      handleEditClose();
      fetchApiKeys(page, debouncedSearchQuery);
    } catch (err) {
      setError(err instanceof Error ? err : new Error('修改失败'));
    } finally {
      setEditSubmitting(false);
    }
  }, [editingCode, editingField, editingValue, handleEditClose, fetchApiKeys, page, debouncedSearchQuery]);

  // 处理删除点击
  const handleDeleteClick = useCallback((akCode: string) => {
    setDeletingAkCode(akCode);
    setShowDeleteDialog(true);
  }, []);

  // 处理删除确认
  const handleDeleteConfirm = useCallback(async () => {
    if (!deletingAkCode) return;

    try {
      setDeleting(true);
      setError(null);

      // 调用删除API
      await deleteApiKey(deletingAkCode);

      // 关闭删除确认对话框
      setShowDeleteDialog(false);

      // 刷新列表
      fetchApiKeys(page, debouncedSearchQuery);
    } catch (err) {
      setError(err instanceof Error ? err : new Error('删除 API Key 失败'));
      setShowDeleteDialog(false);
    } finally {
      setDeleting(false);
      setDeletingAkCode("");
    }
  }, [deletingAkCode, page, debouncedSearchQuery, fetchApiKeys]);

  // 处理编辑安全等级点击
  const handleEditSafetyLevelClick = useCallback((akCode: string) => {
    setEditingAkCode(akCode);
    setShowSafeLevelDialog(true);
  }, []);

  // 处理安全等级更新成功
  const handleSafeLevelUpdateSuccess = useCallback(() => {
    // 刷新列表
    fetchApiKeys(page, debouncedSearchQuery);
  }, [page, debouncedSearchQuery, fetchApiKeys]);

  return (
    <div>
      <TopBar title="我的密钥" description="管理您直接持有的 API 密钥，设置额度和安全等级" />
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
            onChange={(value) => {
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
          apiKey={newApiKey}
          isOpen={showCreatedDialog}
          onClose={handleDialogClose}
          onCopy={handleCopy}
        />

        {/* API Key 重置确认对话框 */}
        <ApiKeyResetDialog
          isOpen={showResetDialog}
          onClose={() => setShowResetDialog(false)}
          onConfirm={handleResetConfirm}
          loading={resetting}
        />

        {/* API Key 转交对话框 */}
        <ApiKeyTransferDialog
          isOpen={showTransferDialog}
          onClose={() => setShowTransferDialog(false)}
          apiKeyName={transferingApiKey?.name}
          apiKeyCode={transferingApiKey?.code}
          akDisplay={transferingApiKey?.akDisplay}
        />

        {/* API Key 删除确认对话框 */}
        <ApiKeyDeleteDialog
          isOpen={showDeleteDialog}
          onClose={() => setShowDeleteDialog(false)}
          onConfirm={handleDeleteConfirm}
          loading={deleting}
        />

        {/* 编辑名称 / 服务名弹窗 */}
        <Dialog open={!!editingField} onOpenChange={handleEditClose}>
          <DialogContent className="max-w-sm">
            <DialogHeader>
              <DialogTitle>{editingField === 'name' ? '修改名称' : '修改服务名'}</DialogTitle>
            </DialogHeader>
            <Input
              value={editingValue}
              onChange={(e) => setEditingValue(e.target.value)}
              placeholder={editingField === 'name' ? '请输入名称' : '请输入服务名'}
              onKeyDown={(e) => e.key === 'Enter' && handleEditConfirm()}
            />
            <DialogFooter>
              <Button variant="outline" onClick={handleEditClose} disabled={editSubmitting}>取消</Button>
              <Button onClick={handleEditConfirm} disabled={editSubmitting}>
                {editSubmitting ? '保存中...' : '确认'}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
        {/* 安全等级编辑对话框 */}
        <UpdateSafeLevel
          isOpen={showSafeLevelDialog}
          onClose={() => setShowSafeLevelDialog(false)}
          akCode={editingAkCode}
          onSuccess={handleSafeLevelUpdateSuccess}
        />

      </div>
    </div>
  );
}
