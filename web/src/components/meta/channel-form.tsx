import React, { useState, useEffect, useRef } from 'react';
import { Channel, CompletionPriceInfo } from '@/lib/types/openapi';
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { EditableField } from './editable-field';
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { ConfirmDialog } from '@/components/ui/confirm-dialog';
import { Label } from "@/components/ui/label";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import {
    Dialog,
    DialogContent,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from "@/components/ui/dialog";
import { CompletionPriceEditor, CompletionPriceEditorRef } from './completion-price-editor';
import { useToast } from '@/hooks/use-toast';
import { isChatCompletionsEndpoint } from '@/lib/utils';

interface ChannelFormProps {
    channel: Channel;
    onUpdate: (channelCode: string, field: keyof Channel, value: string | number) => void;
    onBatchUpdate?: (channelCode: string, updates: Partial<Channel>) => void;
    onToggleStatus: (channelCode: string) => void;
}

export function ChannelForm({ channel, onUpdate, onBatchUpdate, onToggleStatus }: ChannelFormProps) {
    const { toast } = useToast();
    const priceEditorRef = useRef<CompletionPriceEditorRef>(null);

    const [isStatusDialogOpen, setIsStatusDialogOpen] = useState(false);
    const [isTrialDialogOpen, setIsTrialDialogOpen] = useState(false);
    const [isQueueEditing, setIsQueueEditing] = useState(false);
    const [isPriceDialogOpen, setIsPriceDialogOpen] = useState(false);
    const [usePriceDialog, setUsePriceDialog] = useState(false);

    const [queueMode, setQueueMode] = useState(channel.queueMode ?? 0);
    const [queueName, setQueueName] = useState(channel.queueName ?? '');
    const [priceInfo, setPriceInfo] = useState<CompletionPriceInfo | null>(null);

    // 判断是否需要使用区间价格编辑器
    useEffect(() => {
        isChatCompletionsEndpoint(channel.entityType, channel.entityCode)
            .then(setUsePriceDialog);
    }, [channel.entityType, channel.entityCode]);

    // 同步props变化到本地状态
    useEffect(() => {
        setQueueMode(channel.queueMode ?? 0);
        setQueueName(channel.queueName ?? '');
    }, [channel.queueMode, channel.queueName]);

    const handleToggleStatus = () => {
        setIsStatusDialogOpen(true);
    };

    const handleToggleTrial = () => {
        setIsTrialDialogOpen(true);
    };

    const handleQueueEdit = () => {
        // 确保编辑开始时本地状态与props同步
        setQueueMode(channel.queueMode ?? 0);
        setQueueName(channel.queueName ?? '');
        setIsQueueEditing(true);
    };

    const handleQueueSave = () => {
        const finalQueueName = queueMode !== 0 ? queueName.trim() : '';
        
        if (onBatchUpdate) {
            // 使用批量更新，一次性更新两个字段
            onBatchUpdate(channel.channelCode, {
                queueMode,
                queueName: finalQueueName
            });
        } else {
            // 回退到分别更新
            onUpdate(channel.channelCode, 'queueMode', queueMode);
            onUpdate(channel.channelCode, 'queueName', finalQueueName);
        }
        
        setIsQueueEditing(false);
    };

    const handleQueueCancel = () => {
        setQueueMode(channel.queueMode ?? 0);
        setQueueName(channel.queueName ?? '');
        setIsQueueEditing(false);
    };

    const handlePriceEdit = () => {
        // 解析当前的价格信息JSON
        try {
            const parsedPrice = JSON.parse(channel.priceInfo);
            setPriceInfo(parsedPrice);
            setIsPriceDialogOpen(true);
        } catch (error) {
            toast({
                title: '解析失败',
                description: '无法解析当前价格信息，请检查JSON格式',
                variant: 'destructive',
            });
        }
    };

    const handlePriceSave = () => {
        // 调用编辑器的验证方法
        if (priceEditorRef.current?.validate()) {
            // 验证通过，保存价格信息
            const priceInfoString = JSON.stringify(priceInfo);
            onUpdate(channel.channelCode, 'priceInfo', priceInfoString);
            setIsPriceDialogOpen(false);
            toast({
                title: '保存成功',
                description: '价格信息已更新',
            });
        }
    };

    const handlePriceCancel = () => {
        setIsPriceDialogOpen(false);
        setPriceInfo(null);
    };

    const getQueueModeText = (mode: number) => {
        switch (mode) {
            case 0: return 'NONE';
            case 1: return 'PULL';
            case 2: return 'ROUTE';
            case 3: return 'BOTH';
            default: return 'NONE';
        }
    };

    const formatPricePreview = (priceInfoStr: string) => {
        try {
            const price = JSON.parse(priceInfoStr);
            if (!price.tiers || price.tiers.length === 0) {
                return '未配置';
            }

            const totalRanges = price.tiers.reduce(
                (sum: number, tier: any) => {
                    if (Array.isArray(tier.outputRangePrices) && tier.outputRangePrices.length > 0) {
                        return sum + tier.outputRangePrices.length;
                    }
                    return sum + 1;
                },
                0
            );

            return `${totalRanges} 个价格区间`;
        } catch {
            return '格式错误';
        }
    };

    return (
        <Card className="bg-gradient-to-r from-blue-50 to-purple-50 shadow-sm">
            <CardHeader className="flex flex-col space-y-2">
                <div className="flex items-center justify-between">
                    <CardTitle className="text-gray-800">{channel.channelCode}</CardTitle>
                    <div className="flex items-center space-x-2">
                        <Badge variant={channel.trialEnabled === 1 ? "default" : "destructive"}>
                            {channel.trialEnabled === 1 ? '已支持试用' : '不支持试用'}
                        </Badge>
                        <Badge variant={channel.status === 'active' ? "default" : "destructive"}>
                            {channel.status === 'active' ? '已启用' : '已停用'}
                        </Badge>
                    </div>
                </div>
                <div className="flex justify-end space-x-2">
                    <Button
                        onClick={handleToggleTrial}
                        variant={channel.trialEnabled === 1 ? "destructive" : "default"}
                        size="sm"
                    >
                        {channel.trialEnabled === 1 ? '禁止试用' : '支持试用'}
                    </Button>
                    <Button
                        onClick={handleToggleStatus}
                        variant={channel.status === 'active' ? "destructive" : "default"}
                    >
                        {channel.status === 'active' ? '停用' : '启用'}
                    </Button>
                </div>
            </CardHeader>
            <CardContent className="space-y-6">
                <div className="space-y-2 bg-white bg-opacity-50 p-3 rounded-lg">
                    <Label className="text-sm font-medium text-gray-700">转发url</Label>
                    <div className="text-sm font-medium text-gray-700">{channel.url}</div>
                </div>
                <div className="space-y-2 bg-white bg-opacity-50 p-3 rounded-lg">
                    <Label className="text-sm font-medium text-gray-700">协议</Label>
                    <div className="text-sm font-medium text-gray-700">{channel.protocol}</div>
                </div>
                <div className="space-y-2 bg-white bg-opacity-50 p-3 rounded-lg">
                    <Label className="text-sm font-medium text-gray-700">供应商</Label>
                    <div className="text-sm font-medium text-gray-700">{channel.supplier}</div>
                </div>
                <EditableField
                    label="渠道信息"
                    value={channel.channelInfo}
                    onUpdate={(value) => onUpdate(channel.channelCode, 'channelInfo', value)}
                    multiline
                />

                {/* 价格信息 - 根据 endpoint 类型选择编辑方式 */}
                {usePriceDialog ? (
                // chat completions endpoint 使用弹窗编辑
                <div className="space-y-4 bg-white bg-opacity-50 p-4 rounded-lg">
                    <div className="flex items-center justify-between">
                        <Label className="text-sm font-medium text-gray-700">价格信息</Label>
                        <Button
                            onClick={handlePriceEdit}
                            size="sm"
                            variant="outline"
                        >
                            编辑
                        </Button>
                    </div>
                    <div className="space-y-1">
                        <div className="text-sm font-medium text-gray-700">
                            {formatPricePreview(channel.priceInfo)}
                        </div>
                    </div>
                </div>
                ) : (
                    // 其他 endpoint 使用 EditableField
                    <EditableField
                        label="价格信息"
                        value={channel.priceInfo}
                        onUpdate={(value) => onUpdate(channel.channelCode, 'priceInfo', value)}
                        multiline
                    />
                )}

                <EditableField
                    label='优先级'
                    value={channel.priority}
                    onUpdate={(value) => onUpdate(channel.channelCode, 'priority', value)}
                />
                
                {/* 队列信息 */}
                <div className="space-y-4 bg-white bg-opacity-50 p-4 rounded-lg">
                    <div className="flex items-center justify-between">
                        <Label className="text-sm font-medium text-gray-700">队列配置</Label>
                        {!isQueueEditing && (
                            <Button 
                                onClick={handleQueueEdit}
                                size="sm"
                                variant="outline"
                            >
                                编辑
                            </Button>
                        )}
                        {isQueueEditing && (
                            <div className="flex space-x-2">
                                <Button 
                                    onClick={handleQueueSave}
                                    size="sm"
                                    variant="outline"
                                >
                                    保存
                                </Button>
                                <Button 
                                    onClick={handleQueueCancel}
                                    size="sm"
                                    variant="ghost"
                                >
                                    取消
                                </Button>
                            </div>
                        )}
                    </div>
                    
                    {!isQueueEditing ? (
                        <div className="space-y-2">
                            <div className="space-y-1">
                                <Label className="text-sm text-gray-600">队列模式</Label>
                                <div className="text-sm font-medium text-gray-700">
                                    {getQueueModeText(channel.queueMode ?? 0)}
                                </div>
                            </div>
                            <div className="space-y-1">
                                <Label className="text-sm text-gray-600">队列名称</Label>
                                <div className="text-sm font-medium text-gray-700">
                                    {channel.queueName || '未设置'}
                                </div>
                            </div>
                        </div>
                    ) : (
                        <div className="space-y-4">
                            <div className="space-y-2">
                                <Label htmlFor="queueMode">队列模式</Label>
                                <Select 
                                    value={queueMode.toString()}
                                    onValueChange={(value) => setQueueMode(parseInt(value))}
                                >
                                    <SelectTrigger>
                                        <SelectValue placeholder="选择队列模式"/>
                                    </SelectTrigger>
                                    <SelectContent>
                                        <SelectItem value="0">NONE</SelectItem>
                                        <SelectItem value="1">PULL</SelectItem>
                                        <SelectItem value="2">ROUTE</SelectItem>
                                        <SelectItem value="3">BOTH</SelectItem>
                                    </SelectContent>
                                </Select>
                            </div>
                            {queueMode !== 0 && (
                                <div className="space-y-2">
                                    <Label htmlFor="queueName">队列名称</Label>
                                    <Input
                                        id="queueName"
                                        value={queueName}
                                        onChange={(e) => setQueueName(e.target.value)}
                                        placeholder="输入队列名称"
                                    />
                                </div>
                            )}
                        </div>
                    )}
                </div>
            {/* 价格编辑弹窗 - 仅在 chat completions endpoint 时使用 */}
            {usePriceDialog && (
                <Dialog open={isPriceDialogOpen} onOpenChange={setIsPriceDialogOpen}>
                    <DialogContent className="max-w-4xl max-h-[90vh] overflow-y-auto bg-white dark:bg-slate-900">
                        <DialogHeader>
                            <DialogTitle>编辑价格信息</DialogTitle>
                        </DialogHeader>

                        {priceInfo && (
                            <CompletionPriceEditor
                                ref={priceEditorRef}
                                value={priceInfo}
                                onChange={setPriceInfo}
                            />
                        )}

                        <DialogFooter>
                            <Button onClick={handlePriceSave}>
                                保存
                            </Button>
                            <Button variant="outline" onClick={handlePriceCancel}>
                                取消
                            </Button>
                        </DialogFooter>
                    </DialogContent>
                </Dialog>
                )}

                <ConfirmDialog
                    isOpen={isStatusDialogOpen}
                    onClose={() => setIsStatusDialogOpen(false)}
                    onConfirm={() => {
                        onToggleStatus(channel.channelCode);
                        setIsStatusDialogOpen(false);
                    }}
                    title={`确认${channel.status === 'active' ? '停用' : '启用'}渠道`}
                    description={`您确定要${channel.status === 'active' ? '停用' : '启用'}该渠道吗？`}
                />
                <ConfirmDialog
                    isOpen={isTrialDialogOpen}
                    onClose={() => setIsTrialDialogOpen(false)}
                    onConfirm={() => {
                        onUpdate(channel.channelCode, 'trialEnabled', channel.trialEnabled === 1 ? 0 : 1);
                        setIsTrialDialogOpen(false);
                    }}
                    title={`确认${channel.trialEnabled === 1 ? '禁止' : '支持'}试用`}
                    description={`您确定要${channel.trialEnabled === 1 ? '禁止' : '支持'}该渠道的试用吗？`}
                />
            </CardContent>
        </Card>
    );
}
