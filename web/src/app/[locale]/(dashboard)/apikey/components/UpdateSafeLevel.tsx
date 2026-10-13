'use client'

import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from "@/components/common/dialog";
import { Button } from "@/components/common/button";
import { Input } from "@/components/common/input";
import { Edit } from "lucide-react";
import { useState } from "react";
import { updateSafeLevel } from "@/lib/api/apiKeys";
import { toast } from "sonner";
import { config } from "@/config";

/**
 * 安全认证弹窗组件
 *
 * 职责：
 * - 提供安全认证码输入界面
 * - 显示安全合规申请链接
 * - 处理认证提交和反馈
 *
 * 设计说明：
 * - 使用受控输入框管理认证码状态
 * - 按钮 loading 状态防止重复提交
 * - 成功后自动关闭并触发父组件刷新
 */
interface UpdateSafeLevelProps {
  isOpen: boolean;
  onClose: () => void;
  akCode: string;
  onSuccess?: () => void;
}

export function UpdateSafeLevel({
  isOpen,
  onClose,
  akCode,
  onSuccess
}: UpdateSafeLevelProps) {
  const [certifyCode, setCertifyCode] = useState("");
  const [loading, setLoading] = useState(false);
  const privacyUrl = config.compliance.privacyUrl;

  // 处理确认提交
  const handleConfirm = async () => {
    if (!certifyCode.trim()) {
      toast.error("请输入安全认证码");
      return;
    }

    try {
      setLoading(true);
      const result = await updateSafeLevel({
        certifyCode: certifyCode.trim(),
        code: akCode
      });

      if (result === true) {
        toast.success("安全认证码修改成功");

        // 关闭弹窗并重置状态
        handleClose();

        // 触发父组件刷新
        onSuccess?.();
      } else {
        toast.error("apikey名称修改失败，请稍后重试。")
        handleClose();
      }
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : "安全认证码修改失败，请稍后重试";
      toast.error(errorMessage);
      handleClose();
    } finally {
      setLoading(false);
    }
  };

  // 处理弹窗关闭
  const handleClose = () => {
    setCertifyCode("");
    onClose();
  };

  // 处理安全合规申请链接点击
  const handleComplianceClick = () => {
    if (!privacyUrl) {
      toast.error("未配置安全合规申请地址，请联系部署方在环境变量中设置 NEXT_PUBLIC_PRIVACY_URL");
      return;
    }

    window.open(privacyUrl, "_blank");
  };

  return (
    <Dialog open={isOpen} onOpenChange={handleClose}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2 text-base">
            <Edit className="h-5 w-5" />
            安全认证
          </DialogTitle>
        </DialogHeader>

        <div className="space-y-4">
          <div className="text-sm text-muted-foreground">
            安全认证码申请请点击跳转：
            <button
              type="button"
              onClick={handleComplianceClick}
              className="text-blue-500 hover:text-blue-600 underline ml-1"
            >
              安全合规申请
            </button>
          </div>

          <div className="space-y-2">
            <label htmlFor="certifyCode" className="text-sm font-medium">
              安全认证码
            </label>
            <Input
              id="certifyCode"
              type="text"
              placeholder="输入新的安全认证码"
              value={certifyCode}
              onChange={(e) => setCertifyCode(e.target.value)}
              disabled={loading}
            />
          </div>
        </div>

        <DialogFooter>
          <Button
            onClick={handleConfirm}
            disabled={loading}
            className="cursor-pointer"
          >
            {loading ? '确认中...' : '确认'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
