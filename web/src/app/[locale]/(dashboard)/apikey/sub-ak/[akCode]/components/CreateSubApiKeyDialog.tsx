'use client'

import { useState, useEffect } from "react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from "@/components/common/dialog";
import { Button } from "@/components/common/button";
import { Input } from "@/components/common/input";
import { Label } from "@/components/common/label";
import { Textarea } from "@/components/common/textarea";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/common/select";
import { DialogDescription } from "@/components/common/dialog";
import { ApikeyInfo, CreateSubApiKeyRequest } from "@/lib/types/apikeys";
import { createSubApiKey } from "@/lib/api/apiKeys";
import { ApiKeyCreatedDialog } from "@/app/[locale]/(dashboard)/apikey/components/ApiKeyCreatedDialog";

interface CreateSubApiKeyDialogProps {
  isOpen: boolean;
  onClose: () => void;
  parentCode: string;
  parentApiKey: ApikeyInfo;
  onSuccess: () => void;
}

export function CreateSubApiKeyDialog({
  isOpen,
  onClose,
  parentCode,
  parentApiKey,
  onSuccess,
}: CreateSubApiKeyDialogProps) {
  const [formData, setFormData] = useState({
    name: "",
    outEntityCode: "",
    safetyLevel: "",
    monthQuota: parentApiKey.monthQuota?.toString() || "",
    remark: "",
  });

  const [errors, setErrors] = useState<Record<string, string>>({});
  const [isLoading, setIsLoading] = useState(false);
  const [submitError, setSubmitError] = useState<string>("");

  // 新增：控制 ApiKeyCreatedDialog 显示状态
  const [showCreatedDialog, setShowCreatedDialog] = useState(false);
  // 新增：存储新创建的子 API Key
  const [newSubApiKey, setNewSubApiKey] = useState("");

  useEffect(() => {
    if (isOpen) {
      setFormData({
        name: "",
        outEntityCode: "",
        safetyLevel: "",
        monthQuota: parentApiKey.monthQuota?.toString() || "",
        remark: "",
      });
    }
    setErrors({});
  }, [isOpen, parentApiKey.monthQuota]);

  // 格式化安全等级，极低，低，中高，高
  const formatSafetyLevel = (level: number) => {
    const levels: Record<number, string> = {
      10: '极低',
      20: '低',
      30: '中',
      40: '高',
    };
    return levels[level] || level.toString();
  };

  // 根据父AK的安全等级动态生成可选项
  const availableSafetyLevels = [10, 20, 30, 40].filter(
    level => level <= parentApiKey.safetyLevel
  );

  // 表单验证
  const validateForm = () => {
    const newErrors: Record<string, string> = {};

    // 验证名称
    if (!formData.name.trim()) {
      newErrors.name = "请输入名称";
    }

    // 验证用途标识
    if (!formData.outEntityCode.trim()) {
      newErrors.outEntityCode = "请输入用途标识";
    }

    // 验证安全等级
    if (!formData.safetyLevel) {
      newErrors.safetyLevel = "请选择安全等级";
    }

    // 验证月额度
    if (!formData.monthQuota.trim()) {
      newErrors.monthQuota = "请输入月额度";
    } else {
      const quota = Number(formData.monthQuota);
      if (isNaN(quota) || quota <= 0) {
        newErrors.monthQuota = "月额度必须为正数";
      } else if (quota > parentApiKey.monthQuota) {
        newErrors.monthQuota = `月额度不能超过 ￥${parentApiKey.monthQuota}`;
      }
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async () => {
    // 表单验证
    if (!validateForm()) {
      return;
    }

    try {
      setIsLoading(true);
      setSubmitError("");

      const createParams: CreateSubApiKeyRequest = {
        name: formData.name.trim(),
        outEntityCode: formData.outEntityCode.trim(),
        safetyLevel: Number(formData.safetyLevel),
        monthQuota: Number(formData.monthQuota),
        parentCode,
        roleCode: parentApiKey.roleCode,
        remark: formData.remark.trim(),
      };

      const res = await createSubApiKey(createParams);
      if (res !== null && res !== undefined) {
        setNewSubApiKey(res);
        onClose();
        setShowCreatedDialog(true);
        onSuccess();
      }

      // 重置表单
      setFormData({
        name: "",
        outEntityCode: "",
        safetyLevel: "",
        monthQuota: parentApiKey.monthQuota?.toString() || "",
        remark: "",
      });
      setErrors({});
    } catch (err) {
      console.error(`创建子密钥失败:`, err);
      setSubmitError(err instanceof Error ? err.message : `创建子密钥失败，请重试`);
    } finally {
      setIsLoading(false);
    }
  };

  // 清除指定字段的错误
  const clearError = (field: string) => {
    if (errors[field]) {
      setErrors((prev) => {
        const newErrors = { ...prev };
        delete newErrors[field];
        return newErrors;
      });
    }
  };

  const handleCancel = () => {
    // 重置表单
    setFormData({
      name: "",
      outEntityCode: "",
      safetyLevel: "",
      monthQuota: parentApiKey.monthQuota?.toString() || "",
      remark: "",
    });
    setErrors({});
    onClose();
  };

  // 新增：处理 ApiKeyCreatedDialog 关闭
  const handleCreatedDialogClose = () => {
    setShowCreatedDialog(false);
    setNewSubApiKey("");
  };

  return (
    <>
      <Dialog open={isOpen} onOpenChange={onClose}>
      <DialogContent className="max-h-[80vh]">
        <DialogHeader>
          <DialogTitle>创建子密钥</DialogTitle>
          <DialogDescription className="text-xs">
            为 生产环境主密钥 创建一个子密钥
          </DialogDescription>
        </DialogHeader>

        <div className="flex-1 overflow-y-auto px-1">
          {/* 错误提示 */}
          {submitError && (
            <div className="mb-4 rounded-lg border border-red-500/20 bg-red-500/10 p-3">
              <p className="text-sm text-red-500">{submitError}</p>
            </div>
          )}

          <div className="space-y-4">
          {/* 名称 */}
          <div className="space-y-2">
            <Label htmlFor="name" className={errors.name ? "text-red-500" : ""}>
              名称 <span className="text-red-500">*</span>
            </Label>
            <Input
              id="name"
              value={formData.name}
              onChange={(e) => {
                setFormData({ ...formData, name: e.target.value });
                clearError("name");
              }}
              placeholder="请输入名称"
              maxLength={64}
              className={errors.name ? "border-red-500" : ""}
            />
            {errors.name && (
              <p className="text-red-500 text-xs mt-1">{errors.name}</p>
            )}
          </div>

          {/* 用途 */}
          <div className="space-y-2">
            <Label htmlFor="outEntityCode" className={errors.outEntityCode ? "text-red-500" : ""}>
              用途标识（通常为用户id或业务id） <span className="text-red-500">*</span>
            </Label>
            <Input
              id="outEntityCode"
              value={formData.outEntityCode}
              onChange={(e) => {
                setFormData({ ...formData, outEntityCode: e.target.value });
                clearError("outEntityCode");
              }}
              placeholder="请输入用途"
              maxLength={64}
              className={errors.outEntityCode ? "border-red-500" : ""}
            />
            {errors.outEntityCode && (
              <p className="text-red-500 text-xs mt-1">{errors.outEntityCode}</p>
            )}
          </div>

          {/* 安全等级 */}
          <div className="space-y-2">
            <Label htmlFor="safetyLevel" className={errors.safetyLevel ? "text-red-500" : ""}>
              安全等级(最高:{formatSafetyLevel(parentApiKey.safetyLevel)}) <span className="text-red-500">*</span>
            </Label>
            <Select
              value={formData.safetyLevel}
              onValueChange={(value) => {
                setFormData({ ...formData, safetyLevel: value });
                clearError("safetyLevel");
              }}
            >
              <SelectTrigger className={errors.safetyLevel ? "border-red-500" : ""}>
                <SelectValue placeholder="请选择安全等级" />
              </SelectTrigger>
              <SelectContent>
                {availableSafetyLevels.map((level) => (
                  <SelectItem key={level} value={level.toString()}>
                    {formatSafetyLevel(level)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {errors.safetyLevel && (
              <p className="text-red-500 text-xs mt-1">{errors.safetyLevel}</p>
            )}
          </div>

          {/* 月额度 */}
          <div className="space-y-2">
            <Label htmlFor="monthQuota" className={errors.monthQuota ? "text-red-500" : ""}>
              月额度(最大:￥{parentApiKey.monthQuota}) <span className="text-red-500">*</span>
            </Label>
            <Input
              id="monthQuota"
              type="number"
              max={parentApiKey.monthQuota}
              value={formData.monthQuota}
              onChange={(e) => {
                setFormData({ ...formData, monthQuota: e.target.value });
                clearError("monthQuota");
              }}
              placeholder="请输入月额度"
              className={errors.monthQuota ? "border-red-500" : ""}
            />
            {errors.monthQuota && (
              <p className="text-red-500 text-xs mt-1">{errors.monthQuota}</p>
            )}
          </div>

          {/* 备注 */}
          <div className="space-y-2">
            <Label htmlFor="remark">备注</Label>
            <Textarea
              id="remark"
              value={formData.remark}
              onChange={(e) =>
                setFormData({ ...formData, remark: e.target.value })
              }
              placeholder="请输入备注信息"
              rows={3}
              maxLength={1024}
            />
          </div>
          </div>
        </div>

        <DialogFooter className="gap-2">
          <Button
            variant="outline"
            onClick={handleCancel}
            className="cursor-pointer"
            disabled={isLoading}
          >
            取消
          </Button>
          <Button
            onClick={handleSubmit}
            className="cursor-pointer"
            disabled={isLoading}
          >
            {isLoading ? '创建中...' : '确认创建'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    {/* 新增：展示新创建的子 API Key 弹窗 */}
    <ApiKeyCreatedDialog
      apiKey={newSubApiKey}
      isOpen={showCreatedDialog}
      onClose={handleCreatedDialogClose}
    />
  </>
  );
}
