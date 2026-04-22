'use client'

import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "@/components/common/dialog";
import { Button } from "@/components/common/button";
import { AlertCircle } from "lucide-react";

interface ApiKeyDeleteDialogProps {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: () => void;
  loading?: boolean;
}

export function ApiKeyDeleteDialog({
  isOpen,
  onClose,
  onConfirm,
  loading = false
}: ApiKeyDeleteDialogProps) {
  return (
    <Dialog open={isOpen} onOpenChange={onClose}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2 text-base ">
            <AlertCircle className="h-5 w-5 text-amber-500" />
            删除 API Key
          </DialogTitle>
        </DialogHeader>

        <DialogDescription className="text-sm">
          确定要删除此API Key吗?此操作无法撤销。
        </DialogDescription>

        <DialogFooter>
          <Button
            onClick={onConfirm}
            disabled={loading}
            className="cursor-pointer"
          >
            {loading ? '删除中...' : '确认'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
