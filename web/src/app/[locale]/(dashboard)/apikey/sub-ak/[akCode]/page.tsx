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
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/components/common/dialog";
import { Input } from "@/components/common/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/common/select";
import { ArrowLeft, AlertCircle, Plus } from "lucide-react";
import { useRouter, useParams, useSearchParams } from "next/navigation";
import { useState, useEffect, useRef, useMemo } from "react";
import { getApiKeyByCode, resetApiKey, deleteApiKey, updateSubApiKey } from "@/lib/api/apiKeys";
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

type SubAkEditableField = 'name' | 'outEntityCode' | 'safetyLevel' | 'remark';

const SUB_AK_FIELD_LABELS: Record<SubAkEditableField, string> = {
  name: '名称',
  outEntityCode: '用途标识',
  safetyLevel: '安全等级',
  remark: '备注',
};

export default function SubAkPage() {
  const { toast } = useToast();
  const router = useRouter();
  const params = useParams<{ akCode: string }>();
  const searchParams = useSearchParams();
  const akCode = params.akCode;
  const viewer = searchParams.get('viewer') ?? undefined;
  const subAkTableRef = useRef<SubAkTableRef>(null);
  const { user } = useAuth();

  const [parentApiKey, setParentApiKey] = useState<ApikeyInfo | null>(null);
  const [error, setError] = useState<Error | null>(null);
  const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false);
  const [fieldEditingSubAk, setFieldEditingSubAk] = useState<ApikeyInfo | null>(null);
  const [fieldEditingName, setFieldEditingName] = useState<SubAkEditableField | null>(null);
  const [fieldEditingValue, setFieldEditingValue] = useState("");
  const [fieldEditingError, setFieldEditingError] = useState("");
  const [fieldEditingSubmitting, setFieldEditingSubmitting] = useState(false);
  const [showResetDialog, setShowResetDialog] = useState(false);
  const [resetingAkCode, setResetingAkCode] = useState<string>("");
  const [resetting, setResetting] = useState(false);
  const [newApiKey, setNewApiKey] = useState("");
  const [showCreatedDialog, setShowCreatedDialog] = useState(false);
  const [showDeleteDialog, setShowDeleteDialog] = useState(false);
  const [deletingAkCode, setDeletingAkCode] = useState<string>("");
  const [deleting, setDeleting] = useState(false);
  const [showQuotaDialog, setShowQuotaDialog] = useState(false);
  const [quotaEditingSubAk, setQuotaEditingSubAk] = useState<ApikeyInfo | null>(null);
  const [quotaEditingValue, setQuotaEditingValue] = useState("");
  const [quotaError, setQuotaError] = useState("");
  const [quotaSubmitting, setQuotaSubmitting] = useState(false);

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
   * 按 code 查询父 AK，权限由后端统一校验。
   * 避免 owner 深链受列表第一页限制，也支持已委托管理的 owner AK。
   */
  const fetchParentApiKey = async () => {
    try {
      const parent = await getApiKeyByCode(akCode);
      if (parent) setParentApiKey(parent);
    } catch (err) {
      console.error('Failed to fetch parent API key:', err);
      setError(err instanceof Error ? err : new Error('无法加载父 AK'));
    }
  };

  const handleBack = () => router.push(capability.backHref);

  const handleEditField = (apiKey: ApikeyInfo, field: SubAkEditableField) => {
    setFieldEditingSubAk(apiKey);
    setFieldEditingName(field);
    setFieldEditingValue(String(apiKey[field] ?? ''));
    setFieldEditingError('');
  };

  const handleEditFieldClose = () => {
    setFieldEditingSubAk(null);
    setFieldEditingName(null);
    setFieldEditingValue('');
    setFieldEditingError('');
  };

  const handleEditFieldConfirm = async () => {
    if (!fieldEditingSubAk || !fieldEditingName) return;
    const value = fieldEditingValue.trim();
    if (fieldEditingName !== 'remark' && !value) {
      setFieldEditingError(`请输入${SUB_AK_FIELD_LABELS[fieldEditingName]}`);
      return;
    }

    if (fieldEditingName === 'safetyLevel') {
      const level = Number(value);
      if (![10, 20, 30, 40].includes(level)) {
        setFieldEditingError('请选择有效的安全等级');
        return;
      }
      if (parentApiKey && level > parentApiKey.safetyLevel) {
        setFieldEditingError('安全等级不能超过父 AK');
        return;
      }
    }

    try {
      setFieldEditingSubmitting(true);
      setFieldEditingError('');
      await updateSubApiKey({
        code: fieldEditingSubAk.code,
        [fieldEditingName]: fieldEditingName === 'safetyLevel' ? Number(value) : value,
      });
      handleEditFieldClose();
      subAkTableRef.current?.refresh();
    } catch (err) {
      setFieldEditingError(err instanceof Error ? err.message : '修改子密钥失败');
    } finally {
      setFieldEditingSubmitting(false);
    }
  };

  const handleEditQuotaClick = (apiKey: ApikeyInfo) => {
    setQuotaEditingSubAk(apiKey);
    setQuotaEditingValue(apiKey.monthQuota?.toString() || "");
    setQuotaError("");
    setShowQuotaDialog(true);
  };

  const handleQuotaClose = () => {
    setShowQuotaDialog(false);
    setQuotaEditingSubAk(null);
    setQuotaEditingValue("");
    setQuotaError("");
  };

  const handleQuotaConfirm = async () => {
    if (!quotaEditingSubAk) return;

    const quota = Number(quotaEditingValue);
    if (isNaN(quota) || quota <= 0) {
      setQuotaError('月额度必须为正数');
      return;
    }
    if (parentApiKey?.monthQuota != null && quota > parentApiKey.monthQuota) {
      setQuotaError(`月额度不能超过 ￥${parentApiKey.monthQuota}`);
      return;
    }

    try {
      setQuotaSubmitting(true);
      setError(null);
      await updateSubApiKey({
        code: quotaEditingSubAk.code,
        monthQuota: quota,
      });
      handleQuotaClose();
      subAkTableRef.current?.refresh();
    } catch (err) {
      console.error('修改子密钥额度失败:', err);
      setQuotaError(err instanceof Error ? err.message : '修改子密钥额度失败');
    } finally {
      setQuotaSubmitting(false);
    }
  };

  const handleCloseDialog = () => {
    setIsCreateDialogOpen(false);
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

      setShowDeleteDialog(false);

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
            onEditField={handleEditField}
            onEditQuota={handleEditQuotaClick}
            onReset={handleReset}
            onDelete={handleDelete}
            onSetManager={handleSetManagerClick}
          />
        </div>

        {/* 创建子密钥 */}
        {parentApiKey && (
          <CreateSubApiKeyDialog
            isOpen={isCreateDialogOpen}
            onClose={handleCloseDialog}
            parentCode={akCode}
            parentApiKey={parentApiKey}
            onSuccess={() => {
              handleCloseDialog();
              subAkTableRef.current?.refresh();
            }}
          />
        )}

        <Dialog open={!!fieldEditingSubAk && !!fieldEditingName} onOpenChange={(open) => !open && handleEditFieldClose()}>
          <DialogContent className="max-w-sm">
            <DialogHeader>
              <DialogTitle>修改{fieldEditingName ? SUB_AK_FIELD_LABELS[fieldEditingName] : ''}</DialogTitle>
            </DialogHeader>
            {fieldEditingName === 'safetyLevel' ? (
              <Select value={fieldEditingValue} onValueChange={(value) => { setFieldEditingValue(value); setFieldEditingError(''); }}>
                <SelectTrigger><SelectValue placeholder="请选择安全等级" /></SelectTrigger>
                <SelectContent>
                  {[10, 20, 30, 40]
                    .filter(level => !parentApiKey || level <= parentApiKey.safetyLevel)
                    .map(level => (
                      <SelectItem key={level} value={String(level)}>
                        {({ 10: '极低', 20: '低', 30: '中', 40: '高' } as Record<number, string>)[level]}
                      </SelectItem>
                    ))}
                </SelectContent>
              </Select>
            ) : (
              <Input
                value={fieldEditingValue}
                onChange={(event) => { setFieldEditingValue(event.target.value); setFieldEditingError(''); }}
                placeholder={fieldEditingName ? `请输入${SUB_AK_FIELD_LABELS[fieldEditingName]}` : ''}
                onKeyDown={(event) => event.key === 'Enter' && handleEditFieldConfirm()}
                maxLength={fieldEditingName === 'remark' ? 1024 : 64}
                autoFocus
              />
            )}
            {fieldEditingError && <p className="text-xs text-red-500">{fieldEditingError}</p>}
            <DialogFooter>
              <Button variant="outline" onClick={handleEditFieldClose} disabled={fieldEditingSubmitting}>取消</Button>
              <Button
                onClick={handleEditFieldConfirm}
                disabled={fieldEditingSubmitting || (fieldEditingName !== 'remark' && !fieldEditingValue.trim())}
              >
                {fieldEditingSubmitting ? '保存中...' : '确认'}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>

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


        {/* 子 AK 月额度编辑：复用 /v1/apikey/update，仅提交 code + monthQuota */}
        <Dialog open={showQuotaDialog} onOpenChange={handleQuotaClose}>
          <DialogContent className="max-w-sm">
            <DialogHeader>
              <DialogTitle>修改月额度</DialogTitle>
            </DialogHeader>
            <Input
              type="number"
              min={1}
              max={parentApiKey?.monthQuota}
              value={quotaEditingValue}
              onChange={(e) => {
                setQuotaEditingValue(e.target.value);
                setQuotaError("");
              }}
              placeholder="请输入月额度"
              onKeyDown={(e) => e.key === 'Enter' && handleQuotaConfirm()}
            />
            {quotaError && (
              <p className="text-xs text-red-500">{quotaError}</p>
            )}
            <DialogFooter>
              <Button variant="outline" onClick={handleQuotaClose} disabled={quotaSubmitting}>
                取消
              </Button>
              <Button
                onClick={handleQuotaConfirm}
                disabled={quotaSubmitting || !quotaEditingValue || Number(quotaEditingValue) <= 0}
              >
                {quotaSubmitting ? '保存中...' : '确认'}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>

        {/* 为子 AK 设置管理人弹窗（capability.canSetManager=true 时可触发） */}
        <ManagerDialog
          isOpen={showManagerDialog}
          onClose={() => setShowManagerDialog(false)}
          akCode={managerTargetSubAk?.code ?? ""}
          akDisplay={managerTargetSubAk?.akDisplay}
          onSuccess={handleManagerSuccess}
          excludeSelf={false}
        />

      </div>
    </div>
  );
}
