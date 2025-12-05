'use client';

import React, { Suspense } from 'react';
import { EndpointDisplay } from '@/components/meta/endpoint-details';
import { SidebarProvider, useSidebar } from '@/lib/context/sidebar-context';

function MetaPageContent() {
    const { selectedEndpoint } = useSidebar();

    return (
        <div className="h-screen bg-gray-50 flex flex-col">
            {/* <ClientHeader title='Bella Openapi'/> */}
            <div className="flex flex-1 overflow-hidden">
                {/* <Sidebar /> */}
                <main className="flex-1 overflow-y-auto">
                    <EndpointDisplay endpoint={selectedEndpoint}/>
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
