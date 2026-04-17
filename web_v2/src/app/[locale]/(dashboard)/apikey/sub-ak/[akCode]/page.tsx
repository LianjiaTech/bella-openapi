'use client'

/**
 * 子密钥管理页面
 *
 * 职责：管理指定父 AK 下的子密钥列表。
 *
 * 视角参数（viewer）：
 *   - 无参数 / 'user'：普通用户视角，查自己的父 AK，返回 /apikey
 *   - 'admin'：管理员视角，查全量父 AK，返回 /apikey-admin
 *   - 'manager'：管理者视角（预留），返回 /manager
 *
 * capability 由 useSubAkCapability hook 集中计算后传给子组件，
 * 子组件只消费 canXxx 布尔值，不直接判断 viewer 字符串。
 */

import { TopBar } from "@/components/layout/top-bar";
import { Button } from "@/components/common/button";
import { ArrowLeft, AlertCircle, Plus } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState, useEffect, use, useRef, useMemo } from "react";
import { getApiKeys, getApiKeyByCode, resetApiKey, deleteApiKey } from "@/lib/api/apiKeys";
import { ApikeyInfo } from "@/lib/types/apikeys";
import { SubAkTable, SubAkTableRef } from "./components/SubAkTable";
import { CreateSubApiKeyDialog } from "./components/CreateSubApiKeyDialog";
import { ApiKeyResetDialog } from "../../components/ApiKeyResetDialog";
import { ApiKeyCreatedDialog } from "../../components/ApiKeyCreatedDialog";
import { ApiKeyDeleteDialog } from "../../components/ApiKeyDeleteDialog";
import { ManagerDialog } from "../../components/ManagerDialog";
import { copyToClipboard } from "@/lib/utils/clipboard";
import { useToast } from "@/hooks/use-toast";
import { useAuth } from "@/components/providers/auth-provider";
import { useSubAkCapability } from "./hooks/useSubAkCapability";

interface SubAkPageProps {
  params: Promise<{ akCode: string }>;
  searchParams: Promise<{ viewer?: string }>;
}

export default function SubAkPage({ params, searchParams }: SubAkPageProps) {
  const { akCode } = use(params);
  const { viewer } = use(searchParams);

  const { toast } = useToast();
  const router = useRouter();
  const subAkTableRef = useRef<SubAkTableRef>(null);
  const { user } = useAuth();

  const [parentApiKey, setParentApiKey] = useState<ApikeyInfo | null>(null);
  const [error, setError] = useState<Error | null>(null);
  const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false);
  const [editingSubApiKey, setEditingSubApiKey] = useState<ApikeyInfo | null>(null);
  const [showResetDialog, setShowResetDialog] = useState(false);
  const [resetingAkCode, setResetingAkCode] = useState<string>("");
  const [resetting, setResetting] = useState(false);
  const [newApiKey, setNewApiKey] = useState("");
  const [showCreatedDialog, setShowCreatedDialog] = useState(false);
  const [showDeleteDialog, setShowDeleteDialog] = useState(false);
  const [deletingAkCode, setDeletingAkCode] = useState<string>("");
  const [deleting, setDeleting] = useState(false);

  // 设置管理人弹窗：为子 AK 指派管理者
  const [showManagerDialog, setShowManagerDialog] = useState(false);
  const [managerTargetSubAk, setManagerTargetSubAk] = useState<ApikeyInfo | null>(null);

  /** 超级管理员判断：影响 capability.canDelete（admin 视角下） */
  const isSuperAdmin = useMemo(
    () => user?.optionalInfo?.['roleCode'] === 'all',
    [user]
  );

  /** 集中计算当前视角下的操作能力，组件层不再直接判断 viewer */
  const capability = useSubAkCapability(viewer, isSuperAdmin);

  useEffect(() => {
    if (!akCode) return;
    fetchParentApiKey();
  }, [akCode, capability.fetchMode]);

  /**
   * 查询父 AK 信息：
   *   - fetchMode=admin：getAdminApiKeys（不绑定 ownerCode，按 akCode 精确匹配）
   *   - fetchMode=user：getApiKeys（ownerCode = 当前用户）
   */
  const fetchParentApiKey = async () => {
    try {
      if (capability.fetchMode === 'admin') {
        // TODO(manager-viewer): manager 视角下 getApiKeyByCode 同样适用（管理者可查被管理AK详情）
        // 若未来需要 manager 专属查询（仅查 managerCode 匹配的AK），在此判断 viewer==='manager'
        const parent = await getApiKeyByCode(akCode);
        if (parent) setParentApiKey(parent);
      } else {
        const ownerCode = user?.userId?.toString() || "";
        const response = await getApiKeys(ownerCode, "", 1, "");
        const parent = response.data?.find(key => key.code === akCode);
        if (parent) setParentApiKey(parent);
      }
    } catch (err) {
      console.error('Failed to fetch parent API key:', err);
    }
  };

  const handleBack = () => router.push(capability.backHref);

  const handleEditSubApiKey = (apiKey: ApikeyInfo) => {
    setEditingSubApiKey(apiKey);
    setIsCreateDialogOpen(true);
  };

  const handleCloseDialog = () => {
    setIsCreateDialogOpen(false);
    setEditingSubApiKey(null);
  };

  const handleCloseCreatedDialog = () => {
    setShowCreatedDialog(false);
    setNewApiKey("");
    // 刷新子密钥列表
    subAkTableRef.current?.refresh();
  };

  const handleReset = (akCode: string) => {
    setResetingAkCode(akCode);
    setShowResetDialog(true);
  };

  const handleResetConfirm = async () => {
    if (!resetingAkCode) return;

    try {
      setResetting(true);
      setError(null);

      const newKey = await resetApiKey(resetingAkCode);

      // 关闭重置确认对话框
      setShowResetDialog(false);

      // 如果返回有效的新密钥，保存并显示对话框
      if (newKey) {
        setNewApiKey(newKey);
        setShowCreatedDialog(true);
      }
    } catch (err) {
      console.error('重置子密钥失败:', err);
      setError(err instanceof Error ? err : new Error('重置 API Key 失败'));
      setShowResetDialog(false);
    } finally {
      setResetting(false);
      setResetingAkCode("");
    }
  };

  const handleDelete = (akCode: string) => {
    setDeletingAkCode(akCode);
    setShowDeleteDialog(true);
  };

  const handleDeleteConfirm = async () => {
    if (!deletingAkCode) return;

    try {
      setDeleting(true);
      setError(null);

      // 调用删除API
      await deleteApiKey(deletingAkCode);

      // 关闭删除确认对话框
      setShowDeleteDialog(false);

      // 刷新子密钥列表
      subAkTableRef.current?.refresh();
    } catch (err) {
      console.error('删除子密钥失败:', err);
      setError(err instanceof Error ? err : new Error('删除 API Key 失败'));
      setShowDeleteDialog(false);
    } finally {
      setDeleting(false);
      setDeletingAkCode("");
    }
  };

  // 处理为子 AK 设置管理人
  const handleSetManagerClick = (apiKey: ApikeyInfo) => {
    setManagerTargetSubAk(apiKey);
    setShowManagerDialog(true);
  };

  // 设置管理人成功：刷新子 AK 列表
  const handleManagerSuccess = () => {
    subAkTableRef.current?.refresh();
  };

  const handleCopy = async (text: string) => {
    const success = await copyToClipboard(text);
    toast({
      title: success ? '复制成功' : '复制失败',
      description: success ? '复制成功' : '请手动复制',
      variant: success ? 'default' : 'destructive',
    });
  };

  return (
    <div>
      <TopBar
        title="子密钥管理"
        description={parentApiKey ? `管理 ${parentApiKey.name || parentApiKey.akDisplay} 的子密钥` : '管理子密钥'}
      />
      <div className="p-8">
        {/* 返回按钮和创建子密钥按钮 */}
        <div className="mb-4 flex items-center justify-between">
          <Button variant="ghost" onClick={handleBack} className="cursor-pointer">
            <ArrowLeft className="h-4 w-4 mr-2" />
            返回 {capability.backLabel}
          </Button>
          {/* 创建子密钥入口：由 canCreate 控制显示 */}
          {capability.canCreate && (
            <Button
              variant="default"
              className="cursor-pointer"
              onClick={() => setIsCreateDialogOpen(true)}
            >
              <Plus className="h-4 w-4" />
              创建子密钥
            </Button>
          )}
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

        {/* 子 AK 表格 */}
        <div className="overflow-y-auto h-[calc(100vh-10rem)] [overscroll-behavior:contain]">
          <SubAkTable
            ref={subAkTableRef}
            ownerCode={user?.userId?.toString() || ""}
            parentCode={akCode}
            capability={capability}
            onCopy={handleCopy}
            onEdit={handleEditSubApiKey}
            onReset={handleReset}
            onDelete={handleDelete}
            onSetManager={handleSetManagerClick}
          />
        </div>

        {/* Dialog 同时服务创建（canCreate）和编辑（canEditQuota）两个入口，
            只要 parentApiKey 已加载即挂载，内部创建按钮由 isEditMode 判断 */}
        {parentApiKey && (
          <CreateSubApiKeyDialog
            isOpen={isCreateDialogOpen}
            onClose={handleCloseDialog}
            parentCode={akCode}
            parentApiKey={parentApiKey}
            editingApiKey={editingSubApiKey}
            onSuccess={() => {
              handleCloseDialog();
              subAkTableRef.current?.refresh();
            }}
          />
        )}

        {/* API Key 重置确认对话框 */}
        <ApiKeyResetDialog
          isOpen={showResetDialog}
          onClose={() => setShowResetDialog(false)}
          onConfirm={handleResetConfirm}
          loading={resetting}
        />

        {/* API Key 创建成功对话框 */}
        <ApiKeyCreatedDialog
          apiKey={newApiKey}
          isOpen={showCreatedDialog}
          onClose={handleCloseCreatedDialog}
          onCopy={() => handleCopy(newApiKey)}
        />

        {/* API Key 删除确认对话框 */}
        <ApiKeyDeleteDialog
          isOpen={showDeleteDialog}
          onClose={() => setShowDeleteDialog(false)}
          onConfirm={handleDeleteConfirm}
          loading={deleting}
        />

        {/* 为子 AK 设置管理人弹窗（capability.canSetManager=true 时可触发） */}
        <ManagerDialog
          isOpen={showManagerDialog}
          onClose={() => setShowManagerDialog(false)}
          akCode={managerTargetSubAk?.code ?? ""}
          akDisplay={managerTargetSubAk?.akDisplay}
          onSuccess={handleManagerSuccess}
        />
      </div>
    </div>
  );
}
