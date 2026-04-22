'use client'

import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
  DialogDescription,
} from "@/components/common/dialog";
import { Button } from "@/components/common/button";
import { useState } from "react";
import { ArrowLeft } from "lucide-react";
import { UserSearchView } from "./components/UserSearchView";
import { TransferHistoryView } from "./components/TransferHistoryView";
import { TransferResultView } from "./components/TransferResultView";

interface ApiKeyTransferDialogProps {
  isOpen: boolean;
  onClose: () => void;
  apiKeyName?: string;
  apiKeyCode?: string;
  akDisplay?: string;
}

type ViewMode = 'search' | 'history' | 'result';

interface TransferResultData {
  status: 'success' | 'error';
  targetUserName?: string;
  targetUserEmail?: string;
  errorMessage?: string;
}

export function ApiKeyTransferDialog({
  isOpen,
  onClose,
  apiKeyCode = "",
  akDisplay = ""
}: ApiKeyTransferDialogProps) {
  const [viewMode, setViewMode] = useState<ViewMode>('search');
  const [resultData, setResultData] = useState<TransferResultData>({
    status: 'success',
    targetUserName: '',
    targetUserEmail: '',
    errorMessage: ''
  });

  const handleViewHistory = () => {
    setViewMode('history');
  };

  const handleBackToSearch = () => {
    setViewMode('search');
  };

  const handleShowResult = (data: TransferResultData) => {
    console.log('---data ====', data)
    setResultData(data);
    setViewMode('result');
  };

  const handleResultComplete = () => {
    handleClose();
  };

  const handleResultRetry = () => {
    setViewMode('search');
  };

  // 当弹窗关闭时重置视图模式
  const handleClose = () => {
    console.log('---handleClose ====')
    setViewMode('search');
    onClose();
  };

  return (
    <Dialog open={isOpen} onOpenChange={handleClose}>
      <DialogContent className="max-w-md">
        {viewMode === 'search' ? (
          // 搜索用户视图
          <>
            <DialogHeader>
              <DialogTitle className="text-base">
                转交API Key - 搜索用户
              </DialogTitle>
              <DialogDescription className="text-xs">
                搜索并选择要转交<span className="text-blue-500">"{akDisplay}"</span>的目标用户
              </DialogDescription>
            </DialogHeader>

            <UserSearchView
              apiKeyCode={apiKeyCode}
              akDisplay={akDisplay}
              onViewHistory={handleViewHistory}
              onShowResult={handleShowResult}
            />

            <DialogFooter>
              <Button
                variant="outline"
                onClick={handleClose}
                className="cursor-pointer"
              >
                取消
              </Button>
            </DialogFooter>
          </>
        ) : viewMode === 'history' ? (
          // 转交历史视图
          <>
            <DialogHeader>
              <div className="flex items-center gap-2">
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={handleBackToSearch}
                  className="h-8 w-8 p-0 cursor-pointer"
                >
                  <ArrowLeft className="h-4 w-4" />
                </Button>
                <DialogTitle className="text-base">
                  转交历史
                </DialogTitle>
              </div>
              <DialogDescription className="text-xs ml-10">
                查看<span className="text-blue-500">"{akDisplay}"</span>的转交历史记录
              </DialogDescription>
            </DialogHeader>

            <TransferHistoryView akDisplay={akDisplay} />

            <DialogFooter>
              <Button
                variant="outline"
                onClick={handleClose}
                className="cursor-pointer"
              >
                关闭
              </Button>
            </DialogFooter>
          </>
        ) : (
          // 转交结果视图
          <>
            <DialogHeader>
              <div className="">
                <div className="flex items-center gap-2">
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={handleBackToSearch}
                    className="h-8 w-8 p-0 cursor-pointer"
                  >
                    <ArrowLeft className="h-4 w-4" />
                  </Button>
                  <DialogTitle className="text-base">
                    转交API Key
                  </DialogTitle>
                </div>
                <div className="pt-2 text-xs font-normal text-gray-500">确认转交"{akDisplay}"给所选用户:</div>

              </div>
            </DialogHeader>

            <TransferResultView
              userInfo={resultData}
              apiKeyCode={apiKeyCode}
              onComplete={handleResultComplete}
              onRetry={handleResultRetry}
            />
          </>
        )}
      </DialogContent>
    </Dialog>
  );
}
