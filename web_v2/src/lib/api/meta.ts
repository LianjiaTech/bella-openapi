import { EndpointDetails } from '@/lib/types/openapi';
import { apiClient } from '@/lib/api/client';
export async function getEndpointDetails(endpoint: string, modelName: string, features: string[]): Promise<EndpointDetails> {
    const response = await apiClient.get<EndpointDetails, EndpointDetails>('/v1/meta/endpoint/details', {
        params: { endpoint, modelName, features: features.join(',') },
    });
    return response || null;
}

export async function getAllCategoryTrees() {
    return null
}