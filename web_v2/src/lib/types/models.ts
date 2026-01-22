export interface CategoryTree {
    categoryCode: string;
    categoryName: string;
    endpoints: Endpoint[] | null;
    children: CategoryTree[] | null;
}

export interface Endpoint {
    endpoint: string;
    endpointCode: string;
    endpointName: string;
    maintainerCode: string;
    maintainerName: string;
    status: string;
    cuid: number;
    cuName: string;
    muid: number;
    muName: string;
    ctime: string;
    mtime: string;
    documentUrl?: string;
}