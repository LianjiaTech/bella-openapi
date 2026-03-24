'use client'

import { TopBar } from "@/components/layout/top-bar";
import { Button } from "@/components/common/button";
import { ArrowLeft, AlertCircle, Plus } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState, useEffect, use, useRef } from "react";
import { getApiKeys, resetApiKey, deleteApiKey } from "@/lib/api/apiKeys";
import { ApikeyInfo } from "@/lib/types/apikeys";
import { SubAkTable, SubAkTableRef } from "./components/SubAkTable";
import { CreateSubApiKeyDialog } from "./components/CreateSubApiKeyDialog";
import { ApiKeyResetDialog } from "../../components/ApiKeyResetDialog";
import { ApiKeyCreatedDialog } from "../../components/ApiKeyCreatedDialog";
import { ApiKeyDeleteDialog } from "../../components/ApiKeyDeleteDialog";
import { copyToClipboard } from "@/lib/utils/clipboard";
import { useToast } from "@/hooks/use-toast";
import { useAuth } from "@/components/providers/auth-provider";

interface SubAkPageProps {
  params: Promise<{
    akCode: string;
  }>;
}

export default function SubAkPage({ params }: SubAkPageProps) {
  const { akCode } = use(params);
  const { toast } = useToast();
  const router = useRouter();
  const subAkTableRef = useRef<SubAkTableRef>(null);
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
  const { user } = useAuth();
  // 临时硬编码 ownerCode,实际应该从用户会话或上下文中获取
  const ownerCode = user?.userId?.toString() || "";

  // 获取父密钥信息
  useEffect(() => {
    if (akCode) {
      fetchParentApiKey();
    }
  }, [akCode]);

  const fetchParentApiKey = async () => {
    try {
      const response = await getApiKeys(ownerCode, "", 1, "");
      const parent = response.data?.find(key => key.code === akCode);
      if (parent) {
        setParentApiKey(parent);
      }
    } catch (err) {
      console.error('Failed to fetch parent API key:', err);
    }
  };

  const handleBack = () => {
    router.push('/api-keys');
  };

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

  const handleCopy = async (text: string) => {
    const success = await copyToClipboard(text);
    if (!success) {
      toast({
        title: '复制失败',
        description: '请手动复制',
        variant: 'destructive',
      });
    } else {
      toast({
        title: '复制成功',
        description: '复制成功',
        variant: 'default',
      });
    }
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
          <Button
            variant="ghost"
            onClick={handleBack}
            className="cursor-pointer"
          >
            <ArrowLeft className="h-4 w-4 mr-2" />
            返回 API Keys 列表
          </Button>
          <Button
            variant="default"
            className="cursor-pointer"
            onClick={() => {
              console.log("create sub api key");
              setIsCreateDialogOpen(true)
            }}
          >
            <Plus className="h-4 w-4" />
            创建子密钥
          </Button>
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
            ownerCode={ownerCode}
            parentCode={akCode}
            onCopy={handleCopy}
            onEdit={handleEditSubApiKey}
            onReset={handleReset}
            onDelete={handleDelete}
          />
        </div>

        {/* 创建/编辑子密钥对话框 */}
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
      </div>
    </div>
  );
}
