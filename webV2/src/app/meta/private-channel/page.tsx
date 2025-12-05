'use client';

import React, {useEffect, useState} from 'react';
import {listPrivateChannels, updatePrivateChannel, updatePrivateChannelStatus} from '@/lib/api/meta';
import {Channel} from '@/lib/types/openapi';
import {ClientHeader} from "@/components/user/client-header";
import {useToast} from "@/hooks/use-toast";
import Link from "next/link";
import {Button} from "@/components/ui/button";
import {ArrowLeft} from "lucide-react";
import {ChannelList} from "@/components/meta/channel-list";

export default function PrivateChannelPage({ searchParams }: { searchParams: { entityType : string, entityCode : string } }) {
    const { entityType, entityCode } = searchParams;
    const [privateChannels, setPrivateChannels] = useState<Channel[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const { toast } = useToast();

    useEffect(() => {
        if (entityType && entityCode) {
            setIsLoading(true);
            listPrivateChannels(entityType, entityCode)
                .then((channelsData) => {
                    setPrivateChannels(channelsData);
                })
                .catch(error => {
                    console.error('Failed to fetch data:', error);
                    toast({
                        title: "加载失败",
                        description: "无法获取渠道数据",
                        variant: "destructive"
                    });
                })
                .finally(() => setIsLoading(false));
        }
    }, [entityType, entityCode, toast]);

    const handleChannelUpdate = async (channelCode: string, field: keyof Channel, value: string | number) => {
        try {
            const channelToUpdate = privateChannels.find(ch => ch.channelCode === channelCode);
            if (!channelToUpdate) {
                console.error('Channel not found');
                return;
            }

            const updatedChannel = {...channelToUpdate, [field]: value};
            await updatePrivateChannel(channelCode, updatedChannel);

            setPrivateChannels(prev => prev.map(ch =>
                ch.channelCode === channelCode ? {...ch, [field]: value} : ch
            ));

            toast({
                title: "更新成功",
                description: "渠道已更新",
                variant: "default"
            });
        } catch (error) {
            console.error('Failed to update channel:', error);
            // @ts-ignore
            toast({ title: "修改失败", description: error.error, variant: "destructive" });
        }
    };

    const handleChannelStatusUpdate = async (channelCode: string) => {
        try {
            const channelToUpdate = privateChannels.find(ch => ch.channelCode === channelCode);
            if (!channelToUpdate) {
                console.error('Channel not found');
                return;
            }

            const activate = channelToUpdate.status === 'inactive';
            await updatePrivateChannelStatus(channelCode, activate);

            setPrivateChannels(prev => prev.map(ch =>
                ch.channelCode === channelCode ? {...ch, status: activate ? 'active' : 'inactive'} : ch
            ));

            toast({
                title: "状态更新成功",
                description: `渠道已${activate ? '启用' : '停用'}`,
                variant: "default"
            });
        } catch (error) {
            console.error('Failed to update channel status:', error);
            // @ts-ignore
            toast({ title: "状态修改失败", description: error.error, variant: "destructive" });
        }
    };

    if (isLoading) return <div className="flex justify-center items-center h-screen">加载中...</div>;

    return (
        <div className="min-h-screen bg-gray-50">
            <ClientHeader title='私有渠道管理'/>
            <div className="container mx-auto p-4">
                <div className="flex items-center mb-6">
                    <Link href={`/`}>
                        <Button variant="ghost" className="mr-4">
                            <ArrowLeft className="mr-2 h-4 w-4" />
                            返回主页
                        </Button>
                    </Link>
                    <h1 className="text-2xl font-bold">{entityCode}</h1>
                </div>

                <ChannelList
                    channels={privateChannels}
                    onUpdate={handleChannelUpdate}
                    onToggleStatus={handleChannelStatusUpdate}
                    entityType={entityType}
                    entityCode={entityCode}
                    isPrivate={true}
                />
            </div>
        </div>
    );
}
