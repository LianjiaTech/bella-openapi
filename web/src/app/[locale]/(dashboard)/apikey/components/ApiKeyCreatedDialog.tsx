'use client'

import { useState,useEffect } from "react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "@/components/common/dialog";
import { Button } from "@/components/common/button";
import { Copy, Check, AlertCircle } from "lucide-react";
import copy from 'copy-to-clipboard';

interface ApiKeyCreatedDialogProps {
  apiKey: string;
  isOpen: boolean;
  onClose: () => void;
  onCopy?: (apiKey: string) => void;
}

export function ApiKeyCreatedDialog({ apiKey, isOpen, onClose, onCopy }: ApiKeyCreatedDialogProps) {
  const [copied, setCopied] = useState(false);
  const [copyError, setCopyError] = useState(false);

  // 处理复制操作
  const handleCopy = (e: React.MouseEvent<HTMLButtonElement>) => {
    e.preventDefault();
    e.stopPropagation();

    console.log('handleCopy called, apiKey:', apiKey);
    setCopyError(false);
    const success = copy(apiKey);

    if (success) {
      setCopied(true);
      onCopy?.(apiKey); // 通知父组件复制成功
    } else {
      setCopyError(true);
    }
  };
useEffect(() => {
    return () => {
      setCopied(false);
      setCopyError(false);
    };
  }, []);


  return (
    <Dialog open={isOpen} onOpenChange={onClose}>
      <DialogContent
        className=" max-h-[80vh] flex flex-col"
        onInteractOutside={(e) => {
          // 阻止点击遮罩层关闭弹窗
          e.preventDefault()
        }}
        onEscapeKeyDown={(e) => {
          // 阻止按 ESC 键关闭弹窗
          e.preventDefault()
        }}
      >
        <DialogHeader className="flex-shrink-0">
          <DialogTitle>API Key 创建成功</DialogTitle>
        </DialogHeader>

        <div className="flex-1 overflow-y-auto space-y-4 pr-2">
          <div className="space-y-2">
            <label className="text-sm font-medium">API Key:</label>
            <div className="relative">
              <div className="rounded-md border bg-muted px-4 py-3 font-mono text-sm break-all">
                {apiKey}
              </div>
            </div>
          </div>

          <DialogDescription className="text-amber-600 dark:text-amber-500 border rounded-md p-2">
            此API Key仅用于openapi的接口请求鉴权。后续申请额度等操作，需要填写的并非此apikey，而是<span className="font-bold underline">ak code</span>（即apikey的id，不是用于身份验证的密钥），获取方式为：点击每一行操作栏中的复制按钮。
          </DialogDescription>

          <DialogDescription className="text-red-500 text-xs font-bold">
            请妥善保管您的API Key,不要与他人分享。关闭此窗口后将无法再次查看完整的API Key。
          </DialogDescription>
        </div>

        <DialogFooter className="flex-shrink-0 gap-2">
          <div className="flex flex-col gap-2 w-full">
            {copyError && (
              <div className="flex items-center gap-2 text-sm text-red-500">
                <AlertCircle className="h-4 w-4" />
                <span>复制失败，请手动选择复制</span>
              </div>
            )}
            <div className="flex gap-2 justify-end">
              <Button
                variant="outline"
                className="gap-2 cursor-pointer"
                onClick={handleCopy}
                disabled={copied}
              >
                {copied ? (
                  <>
                    <Check className="h-4 w-4" />
                    已复制
                  </>
                ) : (
                  <>
                    <Copy className="h-4 w-4" />
                    复制 API Key
                  </>
                )}
              </Button>
              <Button className="cursor-pointer" onClick={onClose}>
                确认并关闭
              </Button>
            </div>
          </div>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
