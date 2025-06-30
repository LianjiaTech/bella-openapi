'use client';

import {useEffect, useState, Suspense} from 'react';
import {ClientHeader} from "@/components/user/client-header";
import {ModelSelect} from "@/components/ui/model-select";
import {Sidebar} from '@/components/meta/sidebar';
import {getAllCategoryTrees, getEndpointDetails} from '@/lib/api/meta';
import {CategoryTree, Model} from "@/lib/types/openapi";
import { useSearchParams } from 'next/navigation';


function PlaygroundContent() {
    const searchParams = useSearchParams();
    const endpointParam = searchParams.get('endpoint');
    const [selectedEndpoint, setSelectedEndpoint] = useState<string>(endpointParam || '/v1/chat/completions');
    const [models, setModels] = useState<Model[]>([]);
    const [loading, setLoading] = useState(true);
    const [filteredModels, setFilteredModels] = useState<Model[]>([]);
    const [searchQuery, setSearchQuery] = useState('');
    const [selectedModel, setSelectedModel] = useState<string>('');
    const [selectedDisplayModel, setSelectedDisplayModel] = useState<string>('');
    const [categoryTrees, setCategoryTrees] = useState<CategoryTree[]>([]);

    useEffect(() => {
        async function fetchCategoryTrees() {
            try {
                const trees = await getAllCategoryTrees();
                setCategoryTrees(trees);
            } catch (error) {
                console.error('Error fetching category trees:', error);
            }
        }
        fetchCategoryTrees();
    }, []);

    useEffect(() => {
        async function fetchModels() {
            try {
                setLoading(true);
                const data = await getEndpointDetails(selectedEndpoint, '', []);
                setModels(data.models || []);
                setSelectedModel(data.models[0]?.terminalModel || data.models[0]?.modelName);
                setSelectedDisplayModel(data.models[0]?.modelName);
            } catch (error) {
                console.error('Error fetching endpoint details:', error);
            } finally {
                setLoading(false);
            }
        }
        fetchModels();
    }, [selectedEndpoint]);

    useEffect(() => {
        const filtered = models.filter((model) =>
            model.modelName.toLowerCase().includes(searchQuery.toLowerCase())
        );
        setFilteredModels(filtered);
    }, [searchQuery, models]);

    return (
        <div className="min-h-screen bg-white dark:bg-white">
            <ClientHeader title="Playground" />
            <div className="flex h-screen">
                <Sidebar
                    categoryTrees={categoryTrees}
                    onEndpointSelect={setSelectedEndpoint}
                    defaultEndpoint={endpointParam || '/v1/chat/completions'}
                />
                <main className="flex-1 flex flex-col overflow-hidden">
                    {models.length > 0 &&  (
                    <div className="bg-white p-3">
                        <div className="flex items-center space-x-2">
                            <label className="text-sm font-medium text-gray-700 whitespace-nowrap">模型:</label>
                            <ModelSelect
                                value={selectedDisplayModel}
                                onChange={(value) => {
                                    const model = models.find(m => m.modelName === value);
                                    setSelectedModel(model?.terminalModel || model?.modelName || value);
                                    setSelectedDisplayModel(model?.modelName || value);
                                }}
                                models={models.map(m => m.modelName || '')}
                                className="w-full"
                            />
                        </div>
                    </div>)}
                    <div className="flex-1 overflow-hidden">
                        <iframe
                            src={`/playground${selectedEndpoint}?model=${selectedModel}`}
                            className="w-full h-full border-0"
                            sandbox="allow-scripts allow-same-origin allow-popups allow-forms allow-popups-to-escape-sandbox"
                            referrerPolicy="no-referrer"
                        />
                    </div>
                </main>
            </div>
        </div>
    );
}

function Playground() {
    return (
        <Suspense fallback={<div>Loading...</div>}>
            <PlaygroundContent />
        </Suspense>
    );
}

export default function MonitorPage() {
    return <Playground/>;
}
