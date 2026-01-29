import { EndpointDetails } from '@/lib/types/openapi';
import { apiClient } from '@/lib/api/client';
import { CategoryTree } from '@/lib/types/models';
export async function getEndpointDetails(endpoint: string, modelName: string, features: string[]): Promise<EndpointDetails> {
    const response = await apiClient.get<EndpointDetails, EndpointDetails>('/v1/meta/endpoint/details', {
        params: { endpoint, modelName, features: features.join(',') },
    });
    return response || null;
}

export async function getAllCategoryTrees(): Promise<CategoryTree[]> {
    const response = await apiClient.get<CategoryTree[], CategoryTree[]>('/v1/meta/category/tree/all');
    return response || [];
}