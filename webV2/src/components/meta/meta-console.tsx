import {useEffect, useState} from 'react';
import {listConsoleModels} from '@/lib/api/meta';
import {MetadataFeature, Model} from '@/lib/types/openapi';
import {ModelCard} from './model-card';
import {SearchBar} from './search-bar';
import {FeatureFilter} from './feature-filter';
import {Loader} from "lucide-react";
import {Button} from "@/components/ui/button";
import Link from "next/link";

interface MetaConsoleProps {
    endpoint: string | null;
    suppliers: string[]
}

export function MetaConsoleDisplay({ endpoint, suppliers }: MetaConsoleProps) {
    const [endpointModels, setEndpointModels] = useState<Model[]>([]);
    const [searchTerm, setSearchTerm] = useState('');
    const [selectedSupplier, setSelectedSupplier] = useState<string>('');
    const [selectedStatus, setSelectedStatus] = useState<string>('');
    const [selectedVisibility, setSelectedVisibility] = useState<string>('');

    function convertToMetadataFeatures(features: string[]): MetadataFeature[] {
        return features.map(feature => ({ code: feature, name: feature }));
    }

    async function fetchEndpointModels() {
        if (endpoint) {
            const data = await listConsoleModels(endpoint, searchTerm, selectedSupplier, selectedStatus, selectedVisibility);
            setEndpointModels(data);
        } else {
            setEndpointModels([]);
        }
    }

    useEffect(() => {
        setSelectedStatus('');
        setSelectedSupplier('');
        setSearchTerm('');
        fetchEndpointModels();
    }, [endpoint, suppliers]);

    useEffect(() => {
        fetchEndpointModels();
    }, [searchTerm, selectedSupplier, selectedStatus, selectedVisibility]);

    if (!endpoint || !endpointModels) {
        return (
            <div className="flex justify-center items-center p-4">
                <Loader className="h-8 w-8 animate-spin" />
            </div>
        )
    }

    return (
        <div className="p-4">
            <div className="mb-4">
                <SearchBar onSearch={setSearchTerm}/>
            </div>
            <div className="mb-6">
                <FeatureFilter features={convertToMetadataFeatures(suppliers)} onSelect={setSelectedSupplier}
                               selectedFeatures={selectedSupplier}/>
                <div className="flex space-x-4">
                    <FeatureFilter
                        features={[{code: 'active', name: '已启用'}, {code: 'inactive', name: '已停用'}]}
                        onSelect={setSelectedStatus}
                        selectedFeatures={selectedStatus}
                    />
                    <FeatureFilter
                        features={[{code: 'public', name: '公开'}, {code: 'private', name: '私有'}]}
                        onSelect={setSelectedVisibility}
                        selectedFeatures={selectedVisibility}
                    />
                </div>
            </div>
            <div className="flex justify-between items-center mb-8">
                <h2 className="text-xl font-bold text-gray-900">模型列表</h2>
                <Link href="/meta/console/model/create">
                    <Button
                        className="bg-purple-100 hover:bg-purple-200 text-gray-800 font-bold py-2 px-4 rounded-lg shadow-md transition duration-300 ease-in-out transform hover:scale-105">
                        <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5 mr-2 inline-block"
                             viewBox="0 0 20 20" fill="currentColor">
                            <path fillRule="evenodd"
                                  d="M10 3a1 1 0 011 1v5h5a1 1 0 110 2h-5v5a1 1 0 11-2 0v-5H4a1 1 0 110-2h5V4a1 1 0 011-1z"
                                  clipRule="evenodd"/>
                        </svg>
                        添加模型
                    </Button>
                </Link>
            </div>
            {endpointModels && <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                {endpointModels.map(model => (
                    <ModelCard key={model.modelName} model={model} update={true}/>
                ))}
            </div>}
        </div>
    );
}
