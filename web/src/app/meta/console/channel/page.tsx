'use client';

import {ClientHeader} from "@/components/user/client-header";
import {CreateChannelForm} from "@/components/meta/create-channel-form";
import React from "react";

export default function CreateModelPage({ searchParams }: { searchParams: { entityType: string, entityCode: string } }) {
    const {entityType, entityCode} = searchParams;

    return (
        <div className="min-h-screen bg-gray-50">
            <ClientHeader title='添加渠道'/>
            <CreateChannelForm entityType={entityType} entityCode={entityCode}/>
        </div>
    );
}
