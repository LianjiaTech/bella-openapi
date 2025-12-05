'use client';

import React, { useState, useEffect, Suspense } from 'react';
import { Sidebar } from '@/components/meta/sidebar';
import { ClientHeader } from "@/components/user/client-header";
import { MetaConsoleDisplay } from "@/components/meta/meta-console";
import { listSuppliers } from "@/lib/api/meta";
import { SidebarProvider, useSidebar } from '@/lib/context/sidebar-context';

function MetaPageContent() {
    const { selectedEndpoint } = useSidebar();
    const [suppliers, setSuppliers] = useState<string[]>([]);

    useEffect(() => {
        async function fetchSuppliers() {
            const suppliers = await listSuppliers();
            setSuppliers(suppliers);
        }
        fetchSuppliers();
    }, []);

    return (
        <div className="h-screen bg-gray-50 flex flex-col">
            <ClientHeader title='元数据管理'/>
            <div className="flex flex-1 overflow-hidden">
                <Sidebar />
                <main className="flex-1 overflow-y-auto">
                    <MetaConsoleDisplay endpoint={selectedEndpoint} suppliers={suppliers}/>
                </main>
            </div>
        </div>
    );
}

export default function MetaPage() {
    return (
        <Suspense fallback={<div>Loading...</div>}>
            <SidebarProvider>
                <MetaPageContent />
            </SidebarProvider>
        </Suspense>
    );
}
