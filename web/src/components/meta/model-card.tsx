import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { ExternalLink, Settings } from "lucide-react";
import Link from "next/link";
import { Model, PriceDetails } from "@/lib/types/openapi";
import { Badge } from "@/components/ui/badge";
import {useUser} from "@/lib/context/user-context";
import {hasPermission} from "@/lib/api/userInfo";
import { RenderAllTiersTable } from '@/components/meta/price-display';

interface ModelCardProps {
    model: Model;
    update: boolean;
}

// 渲染价格详情
const renderPriceDetails = (priceDetails: PriceDetails) => {
    if (priceDetails.priceInfo?.tiers && priceDetails.priceInfo.tiers.length > 0) {
        const validTiers = priceDetails.priceInfo.tiers.filter(tier => tier.inputRangePrice);
        if (validTiers.length === 0) {
            return null;
        }

        return (
            <div className="bg-gray-50 rounded-lg p-3">
                <RenderAllTiersTable tiers={validTiers} />

                <div className="text-xs text-gray-500 mt-2 text-right">
                    单位: {priceDetails.unit}
                </div>
            </div>
        );
    }

    return (
        <div className="bg-gray-50 rounded-lg p-3">
            {Object.entries(priceDetails.displayPrice).map(([key, value]) => (
                <div key={key} className="flex justify-between items-start mb-1">
                    <span className="text-xs text-gray-600">{key}</span>
                    <span className="text-sm font-semibold text-gray-800 text-right whitespace-pre-line">
                        {value}
                    </span>
                </div>
            ))}
            <div className="text-xs text-gray-500 mt-2 text-right">
                单位: {priceDetails.unit}
            </div>
        </div>
    );
};

export function ModelCard({ model, update }: ModelCardProps) {
    const properties = JSON.parse(model.properties) as Record<string, any>;
    const features = JSON.parse(model.features) as Record<string, boolean>;
    const { userInfo } = useUser()

    const renderProperty = (label: string, value: any) => {
        if (value !== undefined && value !== null) {
            return (
                <div className="bg-gray-50 rounded-lg p-3">
                    <span className="text-sm font-medium text-gray-500">{label}</span>
                    <p className="text-lg font-semibold text-gray-800">{value}</p>
                </div>
            );
        }
        return null;
    };

    return (
        <Card className="overflow-hidden bg-white shadow-lg hover:shadow-xl transition-shadow duration-300 border-none rounded-xl">
            <CardHeader className="bg-gradient-to-r from-blue-100 to-purple-100 pb-8 relative">
                <CardTitle className="flex items-center justify-between z-10 relative">
                    <span className="text-lg font-bold text-gray-800">{model.modelName}</span>
                    <div className="flex items-center space-x-2">
                        {update && (
                            <Link href={`/meta/console/model?modelName=${model.modelName}`} passHref>
                                <Button
                                    variant="ghost"
                                    size="sm"
                                    className="text-gray-600 hover:text-gray-800 hover:bg-white/50"
                                >
                                    <Settings size={16} />
                                </Button>
                            </Link>
                        )}
                        {!update && hasPermission(userInfo, '/v1/meta/channel/private/**') && (
                            <Link href={`/meta/private-channel?entityType=model&entityCode=${model.modelName}`} passHref>
                                <Button
                                    variant="ghost"
                                    size="sm"
                                    className="text-gray-600 hover:text-gray-800 hover:bg-white/50"
                                >
                                    <Settings size={16} className="mr-1" />
                                    私有渠道
                                </Button>
                            </Link>
                        )}
                        {model.documentUrl && (
                            <Link
                                href={model.documentUrl}
                                target="_blank"
                                rel="noopener noreferrer"
                            >
                                <Button variant="ghost" size="sm" className="text-gray-600 hover:text-gray-800 hover:bg-white/50">
                                    <ExternalLink size={16} />
                                </Button>
                            </Link>
                        )}
                    </div>
                </CardTitle>
            </CardHeader>
            <CardContent className="-mt-6 relative z-20">
                <div className="grid grid-cols-2 gap-4 mb-4">
                    {renderProperty("最大输入长度", properties.max_input_context)}
                    {renderProperty("最大输出长度", properties.max_output_context)}
                </div>
                {Object.values(features).some(v => v === true) && (
                    <div className="mt-4">
                        <h3 className="text-sm font-medium text-gray-600 mb-2">特性</h3>
                        <div className="flex flex-wrap gap-2">
                            {Object.entries(features).map(([key, value]) => (
                                value === true ? <Badge key={key} variant="secondary" className="bg-blue-50 text-blue-600 hover:bg-blue-100">{key}</Badge> : null
                            ))}
                        </div>
                    </div>
                )}
                {model.priceDetails && renderPriceDetails(model.priceDetails) && (
                    <div className="mt-4">
                        <h3 className="text-sm font-medium text-gray-600 mb-2">费用</h3>
                        {renderPriceDetails(model.priceDetails)}
                    </div>
                )}
            </CardContent>
        </Card>
    );
}
