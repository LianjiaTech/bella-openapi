import { useState } from 'react';
import { CategoryTree } from '@/lib/types/openapi';
import { useRouter, usePathname } from 'next/navigation';

interface SidebarProps {
    categoryTrees: CategoryTree[];
    onEndpointSelect: (endpoint: string) => void;
    defaultEndpoint: string;
}

export function Sidebar({categoryTrees, onEndpointSelect, defaultEndpoint }: SidebarProps) {
    const [activeEndpoint, setActiveEndpoint] = useState<string | null>(defaultEndpoint);
    const router = useRouter();
    const pathname = usePathname();
    
    const handleEndpointClick = (endpoint: string) => {
        setActiveEndpoint(endpoint);
        onEndpointSelect(endpoint);
        
        // Update URL with endpoint parameter
        const url = new URL(window.location.href);
        url.searchParams.set('endpoint', endpoint);
        router.push(url.pathname + url.search);
    };

    const renderCategoryTree = (tree: CategoryTree) => (
        <div key={tree.categoryCode} className="mb-6">
            <h2 className="font-semibold text-lg mb-3 text-gray-700 dark:text-gray-300">{tree.categoryName}</h2>
            {tree.endpoints && tree.endpoints.map(endpoint => (
                <div
                    key={endpoint.endpointCode}
                    className={`cursor-pointer p-2 rounded-lg transition-colors duration-200 ${
                        activeEndpoint === endpoint.endpoint
                            ? 'bg-gray-600 text-white dark:bg-gray-700'
                            : 'text-gray-700 dark:text-gray-200 hover:text-gray-900 dark:hover:text-gray-100 hover:bg-gray-100 dark:hover:bg-gray-700'
                    }`}
                    onClick={() => handleEndpointClick(endpoint.endpoint)}
                >
                    {endpoint.endpointName}
                </div>
            ))}
            {tree.children && tree.children.map(child => (
                <div key={child.categoryCode} className="mt-4">
                    <h3 className="font-medium text-base mb-2 text-gray-700 dark:text-gray-300">{child.categoryName}</h3>
                    {child.endpoints && child.endpoints.map(endpoint => (
                        <div
                            key={endpoint.endpointCode}
                            className={`cursor-pointer p-2 rounded-lg transition-colors duration-200 ${
                                activeEndpoint === endpoint.endpoint
                                    ? 'bg-gray-600 text-white dark:bg-gray-700'
                                    : 'text-gray-700 dark:text-gray-200 hover:text-gray-900 dark:hover:text-gray-100 hover:bg-gray-100 dark:hover:bg-gray-700'
                            }`}
                            onClick={() => handleEndpointClick(endpoint.endpoint)}
                        >
                            {endpoint.endpointName}
                        </div>
                    ))}
                </div>
            ))}
        </div>
    );

    return (
        <aside className="w-64 bg-white dark:bg-gray-800 shadow-md p-6 overflow-y-auto h-screen border-r border-gray-200 dark:border-gray-700">
            {categoryTrees.map(renderCategoryTree)}
        </aside>
    );
}
