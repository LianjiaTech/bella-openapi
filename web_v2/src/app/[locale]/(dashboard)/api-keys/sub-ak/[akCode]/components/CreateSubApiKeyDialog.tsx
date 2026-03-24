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
import { ApikeyInfo } from "@/lib/types/apikeys";
import { createSubApiKey, updateSubApiKey } from "@/lib/api/apiKeys";
import { CreateSubApiKeyRequest, UpdateSubApiKeyRequest } from "@/lib/types/apikeys";
import { ApiKeyCreatedDialog } from "@/app/[locale]/(dashboard)/api-keys/components/ApiKeyCreatedDialog";

interface CreateSubApiKeyDialogProps {
  isOpen: boolean;
  onClose: () => void;
  parentCode: string;
  parentApiKey: ApikeyInfo;
  editingApiKey?: ApikeyInfo | null;
  onSuccess: () => void;
}

export function CreateSubApiKeyDialog({
  isOpen,
  onClose,
  parentCode,
  parentApiKey,
  editingApiKey,
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

  // 判断是否为编辑模式
  const isEditMode = !!editingApiKey;

  // 当对话框打开且存在编辑数据时,回填表单
  useEffect(() => {
    if (isOpen && editingApiKey) {
      setFormData({
        name: editingApiKey.name || "",
        outEntityCode: editingApiKey.outEntityCode || "",
        safetyLevel: editingApiKey.safetyLevel.toString(),
        monthQuota: editingApiKey.monthQuota?.toString() || "",
        remark: editingApiKey.remark || "",
      });
    } else if (isOpen && !editingApiKey) {
      // 创建模式时重置表单
      setFormData({
        name: "",
        outEntityCode: "",
        safetyLevel: "",
        monthQuota: parentApiKey.monthQuota?.toString() || "",
        remark: "",
      });
    }
    // 清空错误信息
    setErrors({});
  }, [isOpen, editingApiKey, parentApiKey.monthQuota]);

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

      if (isEditMode && editingApiKey) {
        // 编辑模式：调用更新接口
        const updateParams: UpdateSubApiKeyRequest = {
          code: editingApiKey.code,
          name: formData.name.trim(),
          outEntityCode: formData.outEntityCode.trim(),
          safetyLevel: Number(formData.safetyLevel),
          monthQuota: Number(formData.monthQuota),
          roleCode: parentApiKey.roleCode, // 从父密钥继承
          remark: formData.remark.trim(),
        };

        await updateSubApiKey(updateParams);

        // 成功后调用 onSuccess 回调刷新列表并关闭对话框
        onSuccess();
      } else {
        // 创建模式：调用创建接口
        const createParams: CreateSubApiKeyRequest = {
          name: formData.name.trim(),
          outEntityCode: formData.outEntityCode.trim(),
          safetyLevel: Number(formData.safetyLevel),
          monthQuota: Number(formData.monthQuota),
          parentCode: parentCode,
          roleCode: parentApiKey.roleCode, // 从父密钥继承
          remark: formData.remark.trim(),
        };

        const res = await createSubApiKey(createParams);

        // 判断创建是否成功
        if (res !== null && res !== undefined) {
          // TODO: 根据实际接口返回结构调整字段名（可能是 res.apiKey 或 res.ak 等）
          setNewSubApiKey(res);

          // 先关闭创建表单，再展示密钥弹窗
          onClose();

          // 展示新创建的子 API Key 弹窗
          setShowCreatedDialog(true);

          // 成功后调用 onSuccess 回调刷新列表
          onSuccess();
        }
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
      console.error(`${isEditMode ? '更新' : '创建'}子密钥失败:`, err);
      setSubmitError(err instanceof Error ? err.message : `${isEditMode ? '更新' : '创建'}子密钥失败，请重试`);
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
          <DialogTitle>{isEditMode ? '编辑子密钥' : '创建子密钥'}</DialogTitle>
          <DialogDescription className="text-xs">
            {isEditMode ? '修改子密钥信息' : '为 生产环境主密钥 创建一个子密钥'}
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
            {isLoading ? (isEditMode ? '更新中...' : '创建中...') : (isEditMode ? '确认更新' : '确认创建')}
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
