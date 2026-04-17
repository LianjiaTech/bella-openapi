import { ApiKeyBalance } from "@/lib/types/apikeys";

interface QuotaUsageDisplayProps {
    balance?: ApiKeyBalance;
}

/**
 * 额度使用情况展示组件
 * 显示已用额度/总额度和使用百分比进度条
 */
export function QuotaUsageDisplay({ balance }: QuotaUsageDisplayProps) {
    if (!balance) {
        return <span className="text-muted-foreground text-xs">加载中...</span>;
    }

    const usagePercent = balance.quota > 0 ? (balance.cost / balance.quota * 100).toFixed(1) : 0;

    return (
        <div className="space-y-1 w-[160px]">
            <div className="text-xs text-muted-foreground whitespace-nowrap">
                {balance.cost.toFixed(2)} / {balance.quota.toFixed(2)}
            </div>
            <div className="flex items-center gap-2">
                <div className="flex-1 bg-gray-200 rounded-full h-2 overflow-hidden">
                    <div
                        className="bg-blue-500 h-full transition-all duration-300"
                        style={{ width: `${Math.min(Number(usagePercent), 100)}%` }}
                    />
                </div>
                <span className="text-xs text-muted-foreground whitespace-nowrap">
                    {usagePercent}%
                </span>
            </div>
        </div>
    );
}
