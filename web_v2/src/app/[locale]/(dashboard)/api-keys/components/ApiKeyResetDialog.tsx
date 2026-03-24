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

interface ApiKeyResetDialogProps {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: () => void;
  loading?: boolean;
}

export function ApiKeyResetDialog({
  isOpen,
  onClose,
  onConfirm,
  loading = false
}: ApiKeyResetDialogProps) {
  return (
    <Dialog open={isOpen} onOpenChange={onClose}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2 text-base ">
            <AlertCircle className="h-5 w-5 text-amber-500" />
            重置 API Key
          </DialogTitle>
        </DialogHeader>

        <DialogDescription className="text-sm">
          确定要重置此API Key吗？重置后，当前的Key将失效。
        </DialogDescription>

        <DialogFooter>
          <Button
            onClick={onConfirm}
            disabled={loading}
            className="cursor-pointer"
          >
            {loading ? '重置中...' : '确认'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
