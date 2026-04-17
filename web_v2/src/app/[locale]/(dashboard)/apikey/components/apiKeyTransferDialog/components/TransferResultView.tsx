'use client'

import { Button } from "@/components/common/button";
import { DialogFooter } from "@/components/common/dialog";
import { useState } from "react";
import { transferApikey } from "@/lib/api/apiKeys";
import { on } from "events";
import { useToast } from "@/hooks/use-toast";
interface TransferResultViewProps {
  userInfo: any;
  apiKeyCode: string;
  onComplete: () => void;
  onRetry: () => void;
}

export function TransferResultView({
  apiKeyCode,
  userInfo,
  onComplete,
}: TransferResultViewProps) {
  const { toast } = useToast();
  const [transferReason, setTransferReason] = useState("");
  const [showError, setShowError] = useState(false);

  const handleTransfer = async () => {
    // 验证转交原因是否已填写
    if (!transferReason || transferReason.trim().length === 0) {
      setShowError(true);
      return;
    }
    try {
      const data = await transferApikey({
        akCode: apiKeyCode,
        targetUserId: userInfo.id,
        transferReason: transferReason.trim()
      });
      console.log('---data ====', data)

      if (data) {
        onComplete();
      } 
    } catch (error: any) {
      console.error('Error transferring apikey:', error);
      toast({
        title: `${(error as Error).message}`,
        description: "",
        variant: "destructive",
      })
    }
  }
  return (
    <>
      {/* 可滚动的内容区域 */}
      <div className="max-h-[60vh] overflow-y-auto pr-2 space-y-6">
        <div className="border border-gray-200 rounded-md p-4 space-y-1">
          <div className="text-sm font-medium">{userInfo.userName}</div>
          <div className="text-xs text-gray-500">{userInfo.email}</div>
          <div className="text-xs text-gray-400">ID: {userInfo.sourceId}</div>
        </div>

        <div>
          <label htmlFor="transfer-reason" className="block text-sm font-medium text-gray-700 mb-1">
            转交原因<span className="text-red-500">*</span>
          </label>
          <textarea
            id="transfer-reason"
            className="w-full border border-gray-300 rounded-md p-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
            rows={3}
            placeholder="请输入转交原因 (必填)"
            required
            value={transferReason}
            onChange={(e) => {
              setTransferReason(e.target.value);
              if (showError && e.target.value.trim().length > 0) {
                setShowError(false);
              }
            }}
          />
          {showError && (
            <div className="text-xs text-red-500 mt-1">请输入转交原因</div>
          )}
        </div>

        <div className="text-xs text-gray-500 text-center bg-yellow-50 border border-yellow-200 rounded-md p-3">
          <p className="flex items-start gap-2">
            <span className="text-yellow-600">⚠️</span>
            <span className="flex-1 text-left">
              <strong>注意：</strong>转交后，此 API Key 的所有权将完全移交给目标用户，您将失去对此 API Key 的控制权。此操作不可撤销，请慎重操作。
            </span>
          </p>
        </div>
      </div>

      {/* 固定在底部的按钮 */}
      <DialogFooter>
        <Button size="sm" onClick={handleTransfer}>
          确认转交
        </Button>
      </DialogFooter>
    </>
  );
}
