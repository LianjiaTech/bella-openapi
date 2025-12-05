import {
    EndpointDetails,
    Model,
    Channel,
    ModelDetails,
    JsonSchema,
    Category,
    CategoryTree,
    Endpoint, VoiceProperties
} from '@/lib/types/openapi';
import { openapi } from '@/lib/api/openapi';

export async function getTopCategories(): Promise<Category[]> {
    const response = await openapi.get<Category[]>('/v1/meta/category/list', {
        params: { topCategory: true },
    });
    return response.data;
}

export async function getCategoryTree(categoryCode: string): Promise<CategoryTree> {
    const response = await openapi.get<CategoryTree>('/v1/meta/category/tree', {
        params: { includeEndpoint: true, status: 'active', categoryCode },
    });
    return response.data;
}

export async function getAllCategoryTrees(): Promise<CategoryTree[]> {
    const response = await openapi.get<CategoryTree[]>('/v1/meta/category/tree/all');
    return response.data;
}


export async function getEndpointDetails(endpoint: string, modelName: string, features: string[]): Promise<EndpointDetails> {
    const response = await openapi.get<EndpointDetails>('/v1/meta/endpoint/details', {
        params: { endpoint, modelName, features: features.join(',') },
    });
    return response.data;
}

export async function getVoiceProperties(entityType: string, entityCode: string): Promise<VoiceProperties> {
    const response = await openapi.get<VoiceProperties>('/v1/meta/property/voice', {
        params: {entityType, entityCode},
    });
    return response.data;
}

export async function listEndpoints(status: string): Promise<Endpoint[]> {
    const response = await openapi.get<Endpoint[]>('/v1/meta/endpoint/list', {
        params: { status },
    });
    return response.data;
}

export async function listConsoleModels(endpoint: string, modelName: string, supplier: string, status: string, visibility: string): Promise<Model[]> {
    const response = await openapi.get<Model[]>('/console/model/list', {
        params: { endpoint, modelName, supplier, status, visibility, includeLinkedTo : true},
    });
    return response.data;
}

export async function listSuppliers(): Promise<string[]> {
    const response = await openapi.get<string[]>('/v1/meta/supplier/list');
    return response.data;
}

export async function getModelDetails(modelName: string): Promise<ModelDetails> {
    const response = await openapi.get<ModelDetails>('/console/model/details', {
        params: { modelName },
    });
    return response.data;
}


export async function updateModel(modelName: string, update: Partial<Model>): Promise<Boolean> {
    const requestBody: Model = {
        ...update,
        modelName
    } as Model;
    const response = await openapi.put<Boolean>('/console/model', requestBody);
    return response.data;
}

export async function linkModel(modelName: string, linkedTo: string) {
    const response = await openapi.post<Boolean>('/console/model/link', {modelName, linkedTo});
    return response.data;
}

export async function updateModelStatus(modelName: string, activate: boolean) {
    const url = activate ? '/console/model/activate' : '/console/model/inactivate';
    const response = await openapi.post<Boolean>(url, { modelName });
    return response.data;
}

export async function updateModelVisibility(modelName: string, publish: boolean) {
    const url = publish ? '/console/model/publish' : '/console/model/publish/cancel';
    const response = await openapi.post<Boolean>(url, { modelName });
    return response.data;
}

export async function updateChannel(channelCode: string, update: Partial<Channel>): Promise<Boolean> {
    const requestBody: Channel = {
        ...update,
        channelCode,
    } as Channel;
    const response = await openapi.put<Boolean>('/console/channel', requestBody);
    return response.data;
}


export async function updateChannelStatus(channelCode: string, active: boolean) {
    const url = active ? '/console/channel/activate' : '/console/channel/inactivate';
    const response = await openapi.post<Boolean>(url, { channelCode });
    return response.data;
}

export async function updatePrivateChannelStatus(channelCode: string, active: boolean) {
    const url = active ? '/v1/meta/channel/private/activate' : '/v1/meta/channel/private/inactivate';
    const response = await openapi.post<Boolean>(url, { channelCode });
    return response.data;
}

export async function createModel(model: Model) : Promise<Boolean> {
    const response = await openapi.post<Boolean>('/console/model', model);
    return response.data;
}

export async function createChannel(channel: Channel) : Promise<Boolean> {
    const response = await openapi.post<Boolean>('/console/channel', channel);
    return response.data;
}

export async function createPrivateChannel(channel: Channel): Promise<Boolean> {
    const response = await openapi.post<Boolean>('/v1/meta/channel/private', channel);
    return response.data;
}

export async function updatePrivateChannel(channelCode: string, update: Partial<Channel>): Promise<Boolean> {
    const requestBody = {
        channelCode,
        ...update
    };
    const response = await openapi.put<Boolean>('/v1/meta/channel/private', requestBody);
    return response.data;
}

export async function getModelPropertySchema(endpoints: string[]) : Promise<JsonSchema> {
    const response = await openapi.get<JsonSchema>('/v1/meta/schema/modelProperty', {
        params: { endpoints : endpoints.join(',') },
    });
    return response.data;
}


export async function getModelFeatureSchema(endpoints: string[]) : Promise<JsonSchema> {
    const response = await openapi.get<JsonSchema>('/v1/meta/schema/modelFeature', {
        params: { endpoints : endpoints.join(',') },
    });
    return response.data;
}


export async function listProtocols(entityType: string, entityCode: string) : Promise<Record<string, string>> {
    const response = await openapi.get<Record<string, string>>('/v1/meta/protocol/list', {
        params: { entityType, entityCode},
    });
    return response.data;
}


export async function getPriceInfoSchema(entityType: string, entityCode: string) : Promise<JsonSchema> {
    const response = await openapi.get<JsonSchema>('/v1/meta/schema/priceInfo', {
        params: { entityType, entityCode},
    });
    return response.data;
}

export async function getChannelInfoSchema(entityType: string, entityCode: string, protocol: string) : Promise<JsonSchema> {
    const response = await openapi.get<JsonSchema>('/v1/meta/schema/channelInfo', {
        params: { entityType, entityCode, protocol},
    });
    return response.data;
}

export async function listModels(endpoint?: string): Promise<Model[]> {
    const response = await openapi.get<Model[]>('/v1/meta/model/list/for-selection', {
        params: { endpoint, status: 'active'},
    });
    return response.data;
}

export async function listPrivateChannels(entityType: string, entityCode: string): Promise<Channel[]> {
    const response = await openapi.get<Channel[]>('/v1/meta/channel/list', {
        params: {
            entityType,
            entityCode,
            visibility: 'private',
        },
    });
    return response.data;
}
