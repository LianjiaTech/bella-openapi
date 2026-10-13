'use client'

interface TransferHistoryViewProps {
  akDisplay: string;
}

export function TransferHistoryView({
  akDisplay,
}: TransferHistoryViewProps) {
  // TODO: 实现转交历史加载逻辑

  return (
    <div className="space-y-4">
      <div className="max-h-[300px] overflow-y-auto border border-gray-200 rounded-md">
        {/* 历史记录占位 */}
        <div className="p-8 flex items-center justify-center text-sm text-gray-400">
          暂无转交历史记录
        </div>
      </div>
    </div>
  );
}
