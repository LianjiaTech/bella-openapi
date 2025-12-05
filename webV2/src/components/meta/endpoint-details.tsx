import { useState, useEffect } from 'react';
import { getEndpointDetails, listEndpoints } from '@/lib/api/meta';
import { EndpointDetails, Model, Endpoint } from '@/lib/types/openapi';
import { ModelCard } from './model-card';
import { SearchBar } from './search-bar';
import { FeatureFilter } from './feature-filter';
import { Loader } from "lucide-react";
import { DocumentIframeCard } from './document-iframe-card';

interface EndpointDetailsProps {
    endpoint: string | null;
}

export function EndpointDisplay({ endpoint }: EndpointDetailsProps) {
    const [endpointData, setEndpointData] = useState<EndpointDetails | null>(null);
    const [endpointModels, setEndpointModels] = useState<Model[]>([]);
    const [endpointInfo, setEndpointInfo] = useState<Endpoint | null>(null);
    const [searchTerm, setSearchTerm] = useState('');
    const [selectedFeatures, setSelectedFeatures] = useState<string[]>([]);
    const [loading, setLoading] = useState(true);

    async function fetchEndpointModels() {
        if (endpoint) {
            try {
                setLoading(true);
                const data = await getEndpointDetails(endpoint, searchTerm, selectedFeatures);
                setEndpointData(data);
                setEndpointModels(data.models);
            } catch (error) {
                console.error('Error fetching endpoint details:', error);
            } finally {
                setLoading(false);
            }
        } else {
            setEndpointData(null);
            setEndpointModels([]);
            setLoading(false);
        }
    }

    async function fetchEndpointInfo() {
        if (endpoint) {
            try {
                const endpoints = await listEndpoints('active');
                const currentEndpoint = endpoints.find(e => e.endpoint === endpoint);
                setEndpointInfo(currentEndpoint || null);
            } catch (error) {
                console.error('Error fetching endpoint info:', error);
            }
        } else {
            setEndpointInfo(null);
        }
    }

    useEffect(() => {
        setSelectedFeatures([]);
        setSearchTerm('');
        fetchEndpointModels();
        fetchEndpointInfo();
    }, [endpoint]);

    useEffect(() => {
        fetchEndpointModels();
    }, [searchTerm, selectedFeatures]);

    if (!endpoint || loading) {
        return (
            <div className="flex justify-center items-center p-4 h-64">
                <Loader className="h-8 w-8 animate-spin" />
            </div>
        );
    }

    // 如果能力点有文档URL，并且没有关联模型，则显示文档页面
    const hasDocumentUrl = endpointInfo?.documentUrl;
    const hasNoModels = !endpointModels || endpointModels.length === 0;
    const shouldShowDocument = hasDocumentUrl && hasNoModels;

    if (shouldShowDocument && endpointInfo) {
        return (
            <div className="p-4">
                <DocumentIframeCard endpoint={endpointInfo} />
            </div>
        );
    }

    return (
        <div className="p-4">
            <div className="mb-4">
                <SearchBar onSearch={setSearchTerm} />
            </div>
            <div className="mb-4">
                <FeatureFilter 
                    features={endpointData?.features || []} 
                    onSelect={setSelectedFeatures} 
                    selectedFeatures={selectedFeatures} 
                />
            </div>
            {endpointModels && <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                {endpointModels.map(model => (
                    <ModelCard key={model.modelName} model={model} update={false} />
                ))}
            </div>}
            {hasNoModels && !hasDocumentUrl && (
                <div className="flex flex-col items-center justify-center p-8 bg-gray-50 rounded-lg border border-gray-200">
                    <p className="text-gray-500 mb-2">该能力点暂无关联模型或文档</p>
                    <p className="text-sm text-gray-400">请联系维护人员添加模型或文档</p>
                </div>
            )}
        </div>
    );
}
