import React, {useEffect, useState} from 'react';
import {Card, CardContent, CardHeader, CardTitle} from "@/components/ui/card";
import {Button} from "@/components/ui/button";
import {Input} from "@/components/ui/input";
import {Label} from "@/components/ui/label";
import {useToast} from "@/hooks/use-toast";
import {
    createChannel,
    createPrivateChannel,
    getChannelInfoSchema,
    getPriceInfoSchema,
    listProtocols
} from '@/lib/api/meta';
import {Channel, JsonSchema} from '@/lib/types/openapi';
import {Textarea} from "@/components/ui/textarea";
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from "@/components/ui/select";
import {NestedObject} from "@/lib/types/common";
import {renderField} from "@/components/ui/render-field";
import {Switch} from "@/components/ui/switch";
import {useRouter, useSearchParams} from 'next/navigation';

interface Props {
    entityType: string,
    entityCode: string,
    isPrivate?: boolean
}

export function CreateChannelForm({
                                      entityType,
                                      entityCode,
                                      isPrivate = false
                                  }: Props) {
    const {toast} = useToast();
    const searchParams = useSearchParams();
    const [channel, setChannel] = useState<Channel>({
        entityType: entityType,
        entityCode: entityCode,
        channelCode: '',
        status: 'active',
        dataDestination: '',
        priority: '',
        protocol: '',
        supplier: '',
        url: '',
        channelInfo: '{}',
        priceInfo: '{}',
        trialEnabled: 1,
        queueMode: 0,
        queueName: '',
    });
    const [protocols, setProtocols] = useState<Record<string, string>>({})
    const [selectedProtocol, setSelectedProtocol] = useState<string>('')
    const [priceInfoSchema, setPriceInfoSchema] = useState<JsonSchema | null>(null)
    const [channelInfoSchema, setChannelInfoSchema] = useState<JsonSchema | null>(null);
    const [priceInfoValue, setPriceInfoValue] = useState<NestedObject>({});
    const [channelInfoValue, setChannelInfoValue] = useState<NestedObject>({});
    const [loading, setLoading] = useState(false);

    const handleChange = (field: keyof Channel, value: string | number) => {
        if (field == 'protocol') {
            setSelectedProtocol(value as string)
        }
        setChannel(prev => ({...prev, [field]: value}));
    };

    const handlePriceInfoChange = (path: string, value: any) => {
        setPriceInfoValue(prev => {
            const newValue = {...prev};
            let current = newValue;
            const keys = path.split('.');
            for (let i = 0; i < keys.length - 1; i++) {
                if (!current[keys[i]]) current[keys[i]] = {};
                current = current[keys[i]];
            }
            current[keys[keys.length - 1]] = value;
            return newValue;
        });
    };

    const handleChannelInfoChange = (path: string, value: any) => {
        setChannelInfoValue(prev => {
            const newValue = {...prev};
            let current = newValue;
            const keys = path.split('.');
            for (let i = 0; i < keys.length - 1; i++) {
                if (!current[keys[i]]) current[keys[i]] = {};
                current = current[keys[i]];
            }
            current[keys[keys.length - 1]] = value;
            return newValue;
        });
    };

    useEffect(() => {
        async function fetchData() {
            const protocols = await listProtocols(entityType, entityCode);
            setProtocols(protocols);
            setSelectedProtocol('')
            const priceSchema = await getPriceInfoSchema(entityType, entityCode);
            setPriceInfoSchema(priceSchema);
            setPriceInfoValue({});
        }

        fetchData();
    }, []);

    useEffect(() => {
        async function fetchChannelInfoSchema() {
            try {
                const newChannelInfoSchema = await getChannelInfoSchema(entityType, entityCode, selectedProtocol);
                setChannelInfoSchema(newChannelInfoSchema);
                setChannelInfoValue({safetyCheckMode: 'async'});
            } catch (error) {
                console.error("Error fetching schemas:", error);
                toast({
                    title: "获取渠道信息模版失败",
                    // @ts-ignore
                    description: error.error,
                    variant: "destructive",
                });
            }
        }

        if (selectedProtocol && selectedProtocol != '') {
            fetchChannelInfoSchema();
        } else {
            setChannelInfoSchema(null);
            setChannelInfoValue({});
        }
    }, [selectedProtocol, toast]);

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setLoading(true);
        try {
            const isPrivate = searchParams.get('private') === 'true';
            const createFunction = isPrivate ? createPrivateChannel : createChannel;
            const finalChannel = {
                ...channel,
                priceInfo: JSON.stringify(priceInfoValue),
                channelInfo: JSON.stringify(channelInfoValue)
            };
            await createFunction(finalChannel);
            toast({
                title: "成功",
                description: `渠道创建成功`,
            });
            if (isPrivate) {
                window.location.href = `/meta/private-channel?entityType=${entityType}&entityCode=${entityCode}`;
            } else {
                if (entityType == 'model') {
                    window.location.href = `/meta/console/model?modelName=${entityCode}`;
                } else {
                    window.location.href = `/meta/console/endpoint?endpoint=${entityCode}`;
                }
            }
        } catch (error) {
            console.error('Failed to create model:', error);
            toast({
                title: "错误",
                // @ts-ignore
                description: error.error,
                variant: "destructive",
            });
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="container mx-auto p-4">
            <Card className="bg-gradient-to-r from-blue-50 to-purple-50 shadow-sm max-w-2xl mx-auto mt-10">
                <CardHeader>
                    <CardTitle className="text-gray-800">添加渠道</CardTitle>
                </CardHeader>
                <CardContent>
                    <form onSubmit={handleSubmit} className="space-y-4 text-gray-800">
                        <div className="space-y-2">
                            <div className="flex items-center space-x-4">
                                <Label>是否支持试用</Label>
                                <div className="flex items-center space-x-2">
                                    <Switch
                                        checked={channel.trialEnabled === 1}
                                        onCheckedChange={(checked) => handleChange('trialEnabled', checked ? 1 : 0)}
                                    />
                                    <span className="text-sm text-gray-600">
                                    {channel.trialEnabled === 1 ? '是' : '否'}
                                </span>
                                </div>
                            </div>
                        </div>
                        <div className="space-y-2">
                            <Label htmlFor="dataDestination">数据流向</Label>
                            <Select value={channel.dataDestination}
                                    onValueChange={(value) => handleChange('dataDestination', value)}>
                                <SelectTrigger>
                                    <SelectValue placeholder="选择数据流向"/>
                                </SelectTrigger>
                                <SelectContent>
                                    <SelectItem value="protected">内部已备案</SelectItem>
                                    <SelectItem value="inner">内部</SelectItem>
                                    <SelectItem value="mainland">国内</SelectItem>
                                    <SelectItem value="overseas">海外</SelectItem>
                                </SelectContent>
                            </Select>
                        </div>
                        <div className="space-y-2">
                            <Label htmlFor="priority">优先级</Label>
                            <Select value={channel.priority}
                                    onValueChange={(value) => handleChange('priority', value)}>
                                <SelectTrigger>
                                    <SelectValue placeholder="选择优先级"/>
                                </SelectTrigger>
                                <SelectContent>
                                    <SelectItem value="high">高</SelectItem>
                                    <SelectItem value="normal">中</SelectItem>
                                    <SelectItem value="low">低</SelectItem>
                                </SelectContent>
                            </Select>
                        </div>
                        <div className="space-y-2">
                            <Label htmlFor="protocol">协议</Label>
                            <Select value={channel.protocol}
                                    onValueChange={(value) => handleChange('protocol', value)}>
                                <SelectTrigger>
                                    <SelectValue placeholder="选择协议"/>
                                </SelectTrigger>
                                <SelectContent>
                                    {Object.entries(protocols).map(([key, value]) => (
                                        <SelectItem key={key} value={key}>
                                            {value}
                                        </SelectItem>
                                    ))}
                                </SelectContent>
                            </Select>
                        </div>
                        <div className="space-y-2">
                            <Label htmlFor="supplier">供应商</Label>
                            <Input
                                id="supplier"
                                value={channel.supplier}
                                onChange={(e) => handleChange('supplier', e.target.value)}
                                placeholder="输入渠道供应商"
                            />
                        </div>
                        <div className="space-y-2">
                            <Label htmlFor="url">转发url</Label>
                            <Textarea
                                id="url"
                                value={channel.url}
                                onChange={(e) => handleChange('url', e.target.value)}
                                placeholder="输入转发url"
                            />
                        </div>

                        <div className="space-y-2">
                            <Label htmlFor="queueMode">队列模式</Label>
                            <Select value={channel.queueMode?.toString()}
                                    onValueChange={(value) => handleChange('queueMode', parseInt(value))}>
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
                        {channel.queueMode !== undefined && channel.queueMode !== null && channel.queueMode !== 0 ? (
                            <div className="space-y-2">
                                <Label htmlFor="queueName">队列名称</Label>
                                <Input
                                    id="queueName"
                                    value={channel.queueName || ''}
                                    onChange={(e) => handleChange('queueName', e.target.value)}
                                    placeholder="输入队列名称"
                                />
                            </div>
                        ) : null}

                        <div className="space-y-2">
                            <Label htmlFor="safetyCheckMode">安全检测模式</Label>
                            <Select
                                value={channelInfoValue.safetyCheckMode as string || 'async'}
                                onValueChange={(value) => handleChannelInfoChange('safetyCheckMode', value)}
                            >
                                <SelectTrigger>
                                    <SelectValue placeholder="选择安全检测模式"/>
                                </SelectTrigger>
                                <SelectContent>
                                    <SelectItem value="skip">
                                        <div className="flex flex-col items-start py-1">
                                            <span className="font-medium">跳过检测</span>
                                            <span className="text-xs text-gray-500">不执行安全检测</span>
                                        </div>
                                    </SelectItem>
                                    <SelectItem value="async">
                                        <div className="flex flex-col items-start py-1">
                                            <span className="font-medium">异步检测（推荐）</span>
                                            <span className="text-xs text-gray-500">不阻塞主流程，用于审计</span>
                                        </div>
                                    </SelectItem>
                                    <SelectItem value="sync">
                                        <div className="flex flex-col items-start py-1">
                                            <span className="font-medium">同步检测</span>
                                            <span className="text-xs text-gray-500">检测到敏感数据会拦截</span>
                                        </div>
                                    </SelectItem>
                                </SelectContent>
                            </Select>
                            <p className="text-xs text-gray-500 mt-1">
                                {channelInfoValue.safetyCheckMode === 'skip' && '⚠️ 不执行任何安全检测'}
                                {(channelInfoValue.safetyCheckMode === 'async' || !channelInfoValue.safetyCheckMode) && '✓ 异步检测，不阻塞主流程（默认）'}
                                {channelInfoValue.safetyCheckMode === 'sync' && '⚡ 同步检测，会拦截敏感请求'}
                            </p>
                        </div>

                        {channelInfoSchema && (
                            <div className="space-y-2">
                                <Label htmlFor="channelInfo">渠道信息</Label>
                                {channelInfoSchema.params.map((schema) => (
                                    <div key={schema.code}>
                                        {schema.valueType != 'map' && <Label>{schema.name}</Label>}
                                        {renderField(schema, channelInfoValue[schema.code], (_, value) => handleChannelInfoChange(schema.code, value))}
                                    </div>
                                ))}
                            </div>
                        )}
                        {priceInfoSchema && (
                            <div className="space-y-2">
                                <Label htmlFor="channelInfo">单价信息</Label>
                                {priceInfoSchema.params.map((schema) => (
                                    <div key={schema.code}>
                                        <Label>{schema.name}</Label>
                                        {renderField(schema, priceInfoValue[schema.code], (_, value) => handlePriceInfoChange(schema.code, value))}
                                    </div>
                                ))}
                            </div>
                        )}
                        <Button type="submit" className="w-full">
                            创建渠道
                        </Button>
                    </form>
                </CardContent>
            </Card>

        </div>
    );
}
