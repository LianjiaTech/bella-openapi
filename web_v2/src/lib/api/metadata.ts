import { Model, Channel,ModelDetails,Endpoint } from '@/lib/types/openapi';
import { JsonSchema, ListEndpointsResponse } from '@/lib/types/metadata';
import { apiClient } from '@/lib/api/client';

// ==================== Endpoint 相关 ====================

export async function listEndpoints(status: string): Promise<Endpoint[]> {
    const response = await apiClient.get<ListEndpointsResponse[], ListEndpointsResponse[]>('/v1/meta/endpoint/list', {
        params: { status },
    });
    return response || [];
}

// ==================== Model 相关 ====================

export async function listConsoleModels(endpoint: string, modelName: string, supplier: string, status: string, visibility: string): Promise<Model[]> {
    const response = await apiClient.get<Model[], Model[]>('/console/model/list', {
        params: { endpoint, modelName, supplier, status, visibility, includeLinkedTo : true},
    });
    console.log(response, 'response??')
    return response;
}

export async function listModels(endpoint?: string): Promise<Model[]> {
    const response = await apiClient.get<Model[]>('/v1/meta/model/list/for-selection', {
        params: { endpoint, status: 'active'},
    });
    return response.data;
}

export async function getModelDetails(modelName: string): Promise<ModelDetails> {
    const response = await apiClient.get<ModelDetails, ModelDetails>('/console/model/details', {
        params: { modelName },
    });
    return response;
}

export async function createModel(model: Model) : Promise<Boolean> {
    const response = await apiClient.post<Boolean>('/console/model', model);
    return response.data;
}

export async function updateModel(modelName: string, update: Partial<Model>): Promise<Boolean> {
    const requestBody: Model = {
        ...update,
        modelName
    } as Model;
    const response = await apiClient.put<Boolean>('/console/model', requestBody);
    return response.data;
}

export async function linkModel(modelName: string, linkedTo: string) {
    const response = await apiClient.post<Boolean>('/console/model/link', {modelName, linkedTo});
    return response.data;
}

export async function updateModelStatus(modelName: string, activate: boolean) {
    const url = activate ? '/console/model/activate' : '/console/model/inactivate';
    const response = await apiClient.post<Boolean>(url, { modelName });
    return response.data;
}

export async function updateModelVisibility(modelName: string, publish: boolean) {
    const url = publish ? '/console/model/publish' : '/console/model/publish/cancel';
    const response = await apiClient.post<Boolean>(url, { modelName });
    return response.data;
}

// ==================== Supplier 相关 ====================

export async function listSuppliers(): Promise<string[]> {
    const response = await apiClient.get<string[], string[]>('/v1/meta/supplier/list');
    return response;
}

// ==================== Channel 相关 ====================

export async function listPrivateChannels(entityType: string, entityCode: string): Promise<Channel[]> {
    const response = await apiClient.get<Channel[]>('/v1/meta/channel/list', {
        params: {
            entityType,
            entityCode,
            visibility: 'private',
        },
    });
    return response.data;
}

export async function createChannel(channel: Channel) : Promise<Boolean> {
    const response = await apiClient.post<Boolean>('/console/channel', channel);
    return response.data;
}

export async function createPrivateChannel(channel: Channel): Promise<Boolean> {
    const response = await apiClient.post<Boolean>('/v1/meta/channel/private', channel);
    return response.data;
}

export async function updateChannel(channelCode: string, update: Partial<Channel>): Promise<Boolean> {
    const requestBody: Channel = {
        ...update,
        channelCode,
    } as Channel;
    const response = await apiClient.put<Boolean>('/console/channel', requestBody);
    return response.data;
}

export async function updatePrivateChannel(channelCode: string, update: Partial<Channel>): Promise<Boolean> {
    const requestBody = {
        channelCode,
        ...update
    };
    const response = await apiClient.put<Boolean>('/v1/meta/channel/private', requestBody);
    return response.data;
}

export async function updateChannelStatus(channelCode: string, active: boolean) {
    const url = active ? '/console/channel/activate' : '/console/channel/inactivate';
    const response = await apiClient.post<Boolean>(url, { channelCode });
    return response.data;
}

export async function updatePrivateChannelStatus(channelCode: string, active: boolean) {
    const url = active ? '/v1/meta/channel/private/activate' : '/v1/meta/channel/private/inactivate';
    const response = await apiClient.post<Boolean>(url, { channelCode });
    return response.data;
}

// ==================== Schema 相关 ====================

export async function getModelPropertySchema(endpoints: string[], signal?: AbortSignal) : Promise<JsonSchema> {
    const response = await apiClient.get<JsonSchema, JsonSchema>('/v1/meta/schema/modelProperty', {
        params: { endpoints : endpoints.join(',') },
        signal,
    });
    return response;
}

export async function getModelFeatureSchema(endpoints: string[], signal?: AbortSignal) : Promise<JsonSchema> {
    const response = await apiClient.get<JsonSchema, JsonSchema>('/v1/meta/schema/modelFeature', {
        params: { endpoints : endpoints.join(',') },
        signal,
    });
    return response;
}

export async function getPriceInfoSchema(entityType: string, entityCode: string) : Promise<JsonSchema> {
    const response = await apiClient.get<JsonSchema, JsonSchema>('/v1/meta/schema/priceInfo', {
        params: { entityType, entityCode},
    });
    return response || null;
}

export async function getChannelInfoSchema(entityType: string, entityCode: string, protocol: string) : Promise<JsonSchema> {
    const response = await apiClient.get<JsonSchema, JsonSchema>('/v1/meta/schema/channelInfo', {
        params: { entityType, entityCode, protocol},
    });
    return response || null;
}

// ==================== Protocol 相关 ====================

export async function listProtocols(entityType: string, entityCode: string) : Promise<Record<string, string>> {
    const response = await apiClient.get<Record<string, string>, Record<string, string>>('/v1/meta/protocol/list', {
        params: { entityType, entityCode},
    });
    return response || {};
}