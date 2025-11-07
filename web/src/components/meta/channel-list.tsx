import React, { useState, useEffect } from 'react';
import { Channel } from '@/lib/types/openapi';
import { ChannelForm } from './channel-form';
import { Button } from '@/components/ui/button';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';

interface ChannelListProps {
    channels: Channel[];
    onUpdate: (channelCode: string, field: keyof Channel, value: string | number) => void;
    onBatchUpdate?: (channelCode: string, updates: Partial<Channel>) => void;
    onToggleStatus: (channelCode: string) => void;
    entityType: string;
    entityCode: string;
    isPrivate: boolean;
}

export function ChannelList({ channels, onUpdate, onBatchUpdate, onToggleStatus, entityType, entityCode, isPrivate }: ChannelListProps) {
    const [showActiveOnly, setShowActiveOnly] = useState(true);

    const displayChannels = showActiveOnly
        ? channels.filter(channel => channel.status === 'active')
        : channels;

    return (
        <>
            <div className="flex justify-between items-center mt-12 mb-6">
                <div className="flex items-center gap-4">
                    <h2 className="text-xl font-bold">渠道</h2>
                    <div className="flex items-center gap-2">
                        <Button
                            variant="outline"
                            onClick={() => setShowActiveOnly(false)}
                            className={!showActiveOnly ? "bg-black text-white hover:bg-black/90" : ""}
                        >
                            显示所有渠道
                        </Button>
                        <Button
                            variant="outline"
                            onClick={() => setShowActiveOnly(true)}
                            className={showActiveOnly ? "bg-black text-white hover:bg-black/90" : ""}
                        >
                            显示可用渠道
                        </Button>
                    </div>
                </div>
                {entityType && entityCode && (
                    <Link href={`/meta/console/channel?entityType=${entityType}&entityCode=${entityCode}${isPrivate ? '&private=true' : ''}`}>
                        <Button
                            className="bg-purple-100 hover:bg-purple-200 text-gray-800 font-bold py-2 px-4 rounded-lg shadow-md transition duration-300 ease-in-out transform hover:scale-105">
                            <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5 mr-2 inline-block"
                                 viewBox="0 0 20 20" fill="currentColor">
                                <path fillRule="evenodd"
                                      d="M10 3a1 1 0 011 1v5h5a1 1 0 110 2h-5v5a1 1 0 11-2 0v-5H4a1 1 0 110-2h5V4a1 1 0 011-1z"
                                      clipRule="evenodd"/>
                            </svg>
                            添加渠道
                        </Button>
                    </Link>
                )}
            </div>

            {displayChannels.length === 0 ? (
                <div className="text-center py-12 bg-gray-100 rounded-lg">
                    <p className="text-gray-500">暂无渠道</p>
                </div>
            ) : (
                <div className="grid grid-cols-1 gap-6">
                    {displayChannels.map(channel => (
                        <div key={channel.channelCode} className="mb-6">
                            <ChannelForm
                                channel={channel}
                                onUpdate={onUpdate}
                                onBatchUpdate={onBatchUpdate}
                                onToggleStatus={onToggleStatus}
                            />
                        </div>
                    ))}
                </div>
            )}
        </>
    );
}
