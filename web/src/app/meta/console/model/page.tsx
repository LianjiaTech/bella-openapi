'use client';

import React, { useState, useEffect } from 'react';
import {
    getModelDetails,
    updateModel,
    updateChannel,
    updateModelStatus,
    updateModelVisibility,
    updateChannelStatus, linkModel
} from '@/lib/api/meta';
import { Model, Channel, ModelDetails } from '@/lib/types/openapi';
import {ModelForm} from "@/components/meta/model-form";
import {ChannelList} from "@/components/meta/channel-list";
import {ClientHeader} from "@/components/user/client-header";
import {useToast} from "@/hooks/use-toast";
import Link from "next/link";
import {Button} from "@/components/ui/button";

export default function ModelConsolePage({ searchParams }: { searchParams: { modelName: string } }) {
    const { modelName } = searchParams;
    const [modelDetails, setModelDetails] = useState<ModelDetails | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const { toast } = useToast()
    useEffect(() => {
        if (modelName) {
            setIsLoading(true);
            getModelDetails(modelName)
                .then(setModelDetails)
                .finally(() => setIsLoading(false));
        }
    }, [modelName]);

    if (isLoading) return <div>Loading...</div>;
    if (!modelDetails) return <div>Model not found</div>;

    const handleModelUpdate = async (field: keyof Model, value: string) => {
        try {
            if (field == 'linkedTo') {
                await linkModel(modelName, value);
            } else {
                await updateModel(modelName, {...modelDetails.model, [field]: value});
            }
            setModelDetails(prev => prev ? {
                ...prev,
                model: {...prev.model, [field]: value}
            } : null);
        } catch (error) {
            console.error('Failed to update model:', error);
            // @ts-ignore
            toast({ title: "修改失败", description: error.error as string, variant: "destructive" })
        }
    };

    const handleStatusUpdate = async () => {
        try {
            const activate = modelDetails.model.status === 'inactive'
            await updateModelStatus(modelName, activate);
            setModelDetails(prev => prev ? {
                ...prev,
                model: {...prev.model, status : activate ? 'active' : 'inactive'}
            } : null);
        } catch (error) {
            console.error('Failed to update model:', error);
            // @ts-ignore
            toast({ title: "修改失败", description: error.error, variant: "destructive" })
        }
    };

    const handleVisibilityUpdate = async () => {
        try {
            const publish = modelDetails.model.visibility === 'private'
            await updateModelVisibility(modelName, publish);
            setModelDetails(prev => prev ? {
                ...prev,
                model: {...prev.model, visibility : publish ? 'public' : 'private'}
            } : null);
        } catch (error) {
            console.error('Failed to update model:', error);
            // @ts-ignore
            toast({ title: "修改失败", description: error.error, variant: "destructive" })
        }
    };

    const handleChannelUpdate = async (channelCode: string, field: keyof Channel, value: string | number) => {
        try {
            const channelToUpdate = modelDetails.channels.find(ch => ch.channelCode === channelCode);
            if (!channelToUpdate) {
                console.error('Channel not found');
                return;
            }
            const updatedChannel = {...channelToUpdate, [field]: value};
            await updateChannel(channelCode, updatedChannel);
            setModelDetails(prev => prev ? {
                ...prev,
                channels: prev.channels.map(ch =>
                    ch.channelCode === channelCode ? {...ch, [field]: value} : ch
                )
            } : null);
        } catch (error) {
            console.error('Failed to update channel:', error);
            // @ts-ignore
            toast({ title: "修改失败", description: error.error, variant: "destructive" })
        }
    };

    const handleChannelBatchUpdate = async (channelCode: string, updates: Partial<Channel>) => {
        try {
            const channelToUpdate = modelDetails.channels.find(ch => ch.channelCode === channelCode);
            if (!channelToUpdate) {
                console.error('Channel not found');
                return;
            }
            
            const updatedChannel = {...channelToUpdate, ...updates};
            await updateChannel(channelCode, updatedChannel);
            setModelDetails(prev => prev ? {
                ...prev,
                channels: prev.channels.map(ch =>
                    ch.channelCode === channelCode ? {...ch, ...updates} : ch
                )
            } : null);
        } catch (error) {
            console.error('Failed to batch update channel:', error);
            // @ts-ignore
            toast({ title: "修改失败", description: error.error, variant: "destructive" })
        }
    };

    const handleChannelStatusUpdate = async (channelCode: string) => {
        try {
            const channelToUpdate = modelDetails.channels.find(ch => ch.channelCode === channelCode);
            if (!channelToUpdate) {
                console.error('Channel not found');
                return;
            }
            const activate = channelToUpdate.status === 'inactive'
            await updateChannelStatus(channelCode, activate);
            setModelDetails(prev => prev ? {
                ...prev,
                channels: prev.channels.map(ch =>
                    ch.channelCode === channelCode ? {...ch, status : activate ? 'active' : 'inactive' } : ch
                )
            } : null);
        } catch (error) {
            console.error('Failed to update channel:', error);
            // @ts-ignore
            toast({ title: "修改失败", description: error.error, variant: "destructive" })
        }
    };

    return (
        <div className="min-h-screen bg-gray-50">
            <ClientHeader title='模型管理'/>
            <div className="container mx-auto p-4">
                <h1 className="text-2xl font-bold mb-4">模型: {modelDetails.model.modelName}</h1>
                <ModelForm model={modelDetails.model} onUpdate={handleModelUpdate} onToggleStatus={handleStatusUpdate}
                           onToggleVisibility={handleVisibilityUpdate}/>

                <ChannelList
                    channels={modelDetails.channels}
                    onUpdate={handleChannelUpdate}
                    onBatchUpdate={handleChannelBatchUpdate}
                    onToggleStatus={handleChannelStatusUpdate}
                    entityType="model"
                    entityCode={modelName}
                    isPrivate={false}
                />
            </div>
        </div>
    );
}
