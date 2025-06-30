'use client';

import React, { useState, useEffect } from 'react';
import { Sidebar } from '@/components/meta/sidebar';
import { EndpointDisplay } from '@/components/meta/endpoint-details';
import { getAllCategoryTrees } from '@/lib/api/meta';
import { CategoryTree } from '@/lib/types/openapi';
import {ClientHeader} from "@/components/user/client-header";
import { useSearchParams } from 'next/navigation';

export default function MetaPage() {
    const [categoryTrees, setCategoryTrees] = useState<CategoryTree[]>([]);
    const [selectedEndpoint, setSelectedEndpoint] = useState<string | null>(null);
    const searchParams = useSearchParams();
    const endpointParam = searchParams.get('endpoint');

    useEffect(() => {
        const initialEndpoint = endpointParam || '/v1/chat/completions';
        setSelectedEndpoint(initialEndpoint)

        async function fetchAllData() {
            const trees = await getAllCategoryTrees();
            setCategoryTrees(trees);
        }

        fetchAllData();
    }, []);

    return (
        <div className="min-h-screen bg-gray-50">
            <ClientHeader title='Bella Openapi'/>
            <div className="flex">
                <Sidebar categoryTrees={categoryTrees} onEndpointSelect={setSelectedEndpoint}
                         defaultEndpoint={endpointParam || '/v1/chat/completions'}/>
                <main className="flex-1">
                    <EndpointDisplay endpoint={selectedEndpoint}/>
                </main>
            </div>
        </div>
    );
}
