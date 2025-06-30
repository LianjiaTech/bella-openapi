'use client';

import React, { useState, useEffect, Suspense } from 'react';
import { Sidebar } from '@/components/meta/sidebar';
import { getAllCategoryTrees } from '@/lib/api/meta';
import { CategoryTree } from '@/lib/types/openapi';
import {ClientHeader} from "@/components/user/client-header";
import {MetaConsoleDisplay} from "@/components/meta/meta-console";
import {listSuppliers} from "@/lib/api/meta";
import { useSearchParams } from 'next/navigation';

function MetaPageContent() {
    const [categoryTrees, setCategoryTrees] = useState<CategoryTree[]>([]);
    const [selectedEndpoint, setSelectedEndpoint] = useState<string | null>(null);
    const [suppliers, setSuppliers] = useState<string[]>([]);
    const searchParams = useSearchParams();
    const endpointParam = searchParams.get('endpoint');

    useEffect(() => {
        const initialEndpoint = endpointParam || '/v1/chat/completions';
        setSelectedEndpoint(initialEndpoint)

        async function fetchAllData() {
            const trees = await getAllCategoryTrees();
            setCategoryTrees(trees);
            const suppliers = await listSuppliers();
            setSuppliers(suppliers)
        }
        fetchAllData();
    }, []);

    return (
        <div className="min-h-screen bg-gray-50">
            <ClientHeader title='元数据管理'/>
            <div className="flex">
                <Sidebar categoryTrees={categoryTrees} onEndpointSelect={setSelectedEndpoint}
                         defaultEndpoint={endpointParam || '/v1/chat/completions'}/>
                <main className="flex-1">
                    <MetaConsoleDisplay endpoint={selectedEndpoint} suppliers={suppliers}/>
                </main>
            </div>
        </div>
    );
}

export default function MetaPage() {
    return (
        <Suspense fallback={<div>Loading...</div>}>
            <MetaPageContent />
        </Suspense>
    );
}
