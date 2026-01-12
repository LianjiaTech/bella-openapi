import {Badge} from "@/components/ui/badge";
import {PriceDetails, PricingMode, CompletionPriceInfo, PriceTier} from "@/lib/types/openapi";

interface PriceDisplayProps {
    priceDetails: PriceDetails;
}

export function formatTokenRange(min?: number | null, max?: number | null): string {
    const minVal = (min !== null && min !== undefined) ? min.toLocaleString() : '0';

    if (max === null || max === undefined) {
        return `(${minVal}, ∞)`;
    }

    return `(${minVal}, ${max.toLocaleString()}]`;
}

export function PriceDisplay({priceDetails}: PriceDisplayProps) {
    const priceInfo = priceDetails.priceInfo as CompletionPriceInfo;

    if (priceInfo?.mode === PricingMode.TIERED && priceInfo.tiers) {
        return <TieredPriceDisplay tiers={priceInfo.tiers} unit={priceDetails.unit}/>;
    }

    return <FixedPriceDisplay displayPrice={priceDetails.displayPrice} unit={priceDetails.unit}/>;
}

function FixedPriceDisplay({displayPrice, unit}: { displayPrice: Record<string, string>; unit: string }) {
    return (
        <div className="bg-gray-50 rounded-lg p-3">
            {Object.entries(displayPrice).map(([key, value]) => (
                <div key={key} className="flex justify-between items-start mb-1">
                    <span className="text-xs text-gray-600">{key}</span>
                    <span className="text-sm font-semibold text-gray-800 text-right whitespace-pre-line">
                        {value}
                    </span>
                </div>
            ))}
            <div className="text-xs text-gray-500 mt-2 text-right">
                单位: {unit}
            </div>
        </div>
    );
}

function TieredPriceDisplay({tiers, unit}: { tiers: PriceTier[]; unit: string }) {
    const formatPrice = (price: number): string => {
        return price.toFixed(4);
    };

    const hasOutputRange = (tier: PriceTier): boolean => {
        return tier.minOutputTokens !== undefined && tier.minOutputTokens !== null ||
            tier.maxOutputTokens !== undefined && tier.maxOutputTokens !== null;
    };

    const renderTier = (tier: PriceTier, index: number) => {
        const showOutputRange = hasOutputRange(tier);

        return (
            <div key={index} className="bg-blue-50 p-2 rounded mb-1 text-xs">
                <div className="mb-1">
                    <span className="font-medium text-gray-700">区间 {index + 1}</span>
                </div>
                <div className="space-y-1">
                    {/* Token 范围 */}
                    <div className="text-gray-600">
                        <span
                            className="font-medium">输入范围:</span> {formatTokenRange(tier.minInputTokens, tier.maxInputTokens)} tokens
                    </div>
                    {showOutputRange && (
                        <div className="text-gray-600">
                            <span
                                className="font-medium">输出范围:</span> {formatTokenRange(tier.minOutputTokens, tier.maxOutputTokens)} tokens
                        </div>
                    )}

                    {/* 价格信息 */}
                    <div className="grid grid-cols-2 gap-x-4 gap-y-1 text-gray-600 mt-2 pt-2 border-t border-blue-200">
                        <div>输入: {formatPrice(tier.inputPrice)}</div>
                        <div>输出: {formatPrice(tier.outputPrice)}</div>
                        {tier.imageInputPrice !== undefined && tier.imageInputPrice !== null && (
                            <div>图片输入: {formatPrice(tier.imageInputPrice)}</div>
                        )}
                        {tier.imageOutputPrice !== undefined && tier.imageOutputPrice !== null && (
                            <div>图片输出: {formatPrice(tier.imageOutputPrice)}</div>
                        )}
                        {tier.cachedReadPrice !== undefined && tier.cachedReadPrice !== null && (
                            <div>缓存命中: {formatPrice(tier.cachedReadPrice)}</div>
                        )}
                        {tier.cachedCreationPrice !== undefined && tier.cachedCreationPrice !== null && (
                            <div>缓存创建: {formatPrice(tier.cachedCreationPrice)}</div>
                        )}
                    </div>
                </div>
            </div>
        );
    };

    return (
        <div className="space-y-3">
            <div className="flex items-center gap-2">
                <Badge variant="outline" className="bg-purple-50 text-purple-700 border-purple-200">
                    区间定价
                </Badge>
                <span className="text-xs text-gray-500">单位: {unit}</span>
            </div>

            <div>
                {tiers && tiers.length > 0 ? (
                    tiers.map((tier, idx) => renderTier(tier, idx))
                ) : (
                    <div className="text-xs text-gray-500">未配置价格区间</div>
                )}
            </div>
        </div>
    );
}
