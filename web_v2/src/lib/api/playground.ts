import { apiClient } from '@/lib/api/client';

export async function generateImages(body: any): Promise<any> {
    console.log(body,'body<<<')
    const response = await apiClient.post('/v1/images/generations', body);
    return response;
}

export async function editImages(formData: FormData): Promise<any> {
    const response = await apiClient.post('/v1/images/edits', formData);
    return response;
}