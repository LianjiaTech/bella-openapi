import React, {useEffect, useState} from 'react';
import {Card, CardContent, CardHeader, CardTitle} from "@/components/ui/card";
import {Button} from "@/components/ui/button";
import {Label} from "@/components/ui/label";
import {Input} from "@/components/ui/input";
import MultiSelect from "@/components/ui/multi-select";
import {useToast} from "@/hooks/use-toast";
import {JsonSchema, Model} from "@/lib/types/openapi";
import {createModel, getModelFeatureSchema, getModelPropertySchema, listSuppliers} from "@/lib/api/meta";
import {NestedObject} from "@/lib/types/common";
import {renderField} from "@/components/ui/render-field";
import {listEndpoints} from "@/lib/api/meta";


type ModelWithoutPriceDetails = Omit<Model, 'priceDetails' | 'terminalModel'>;


export function CreateModelForm() {
    const { toast } = useToast();
    const [model, setModel] = useState<ModelWithoutPriceDetails>({
        modelName: '',
        linkedTo: '',
        documentUrl: '',
        properties: '{}',
        features: '{}',
        ownerType: '',
        ownerCode: '',
        ownerName: '',
        visibility: 'private',
        status: 'active',
        endpoints: []
    });
    const [propertySchema, setPropertySchema] = useState<JsonSchema | null>(null);
    const [featureSchema, setFeatureSchema] = useState<JsonSchema | null>(null);
    const [propertyValue, setPropertyValue] = useState<NestedObject>({});
    const [featureValue, setFeatureValue] = useState<NestedObject>({});
    const [endpointOptions, setEndpointOptions] = useState<string[]>([]);
    const [selectedEndpoints, setSelectedEndpoints] = useState<string[]>([]);

    useEffect(() => {
        async function fetchEndpoints() {
            const endpoints = await listEndpoints('active');
            setEndpointOptions(endpoints.map(endpoint => endpoint.endpoint))
        }
        fetchEndpoints();
    }, []);

    useEffect(() => {
        setModel(prev => ({
            ...prev,
            endpoints: selectedEndpoints.length > 0 ? selectedEndpoints : []
        }));
    }, [selectedEndpoints]);

    useEffect(() => {
        async function fetchModelPropertyAndFeatureSchema() {
            try {
                const newPropertySchema = await getModelPropertySchema(selectedEndpoints);
                const newFeatureSchema = await getModelFeatureSchema(selectedEndpoints);
                setPropertySchema(newPropertySchema);
                setFeatureSchema(newFeatureSchema);
                setPropertyValue({});
                setFeatureValue({});
            } catch (error) {
                console.error("Error fetching schemas:", error);
                toast({
                    title: "获取模型属性和特性失败",
                    // @ts-ignore
                    description: error.error,
                    variant: "destructive",
                });
            }
        }
        if (selectedEndpoints.length > 0) {
            fetchModelPropertyAndFeatureSchema();
        } else {
            setPropertySchema(null);
            setFeatureSchema(null);
            setPropertyValue({});
            setFeatureValue({});
        }
    }, [selectedEndpoints, toast]);

    const handleChange = (field: keyof ModelWithoutPriceDetails, value: any) => {
        setModel(prev => ({ ...prev, [field]: value }));
    };

    const handlePropertyChange = (path: string, value: any) => {
        setPropertyValue(prev => {
            const newValue = { ...prev };
            let current = newValue;
            const keys = path.split('.');
            for (let i = 0; i <keys.length - 1; i++) {
                if (!current[keys[i]]) current[keys[i]] = {};
                current = current[keys[i]];
            }
            current[keys[keys.length - 1]] = value;
            return newValue;
        });
    };

    const handleFeatureChange = (path: string, value: any) => {
        setFeatureValue(prev => {
            const newValue = { ...prev };
            let current = newValue;
            const keys = path.split('.');
            for (let i = 0; i < keys.length - 1; i++) {
                if (!current[keys[i]]) current[keys[i]] = {};
                current = current[keys[i]];
            }
            current[keys[keys.length - 1]] = value;
            return newValue;
        });
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!model.modelName.trim()) {
            toast({
                title: "错误",
                description: "模型名称不能为空",
                variant: "destructive",
            });
            return;
        }

        const finalModel = {
            ...model,
            properties: JSON.stringify(propertyValue),
            features: JSON.stringify(featureValue)
        };

        try {
            await createModel(finalModel as Model);
            toast({
                title: "成功",
                description: "模型创建成功",
            });
            window.location.href = `/meta/console/model?modelName=${model.modelName}`;
        } catch (error) {
            console.error('Failed to create model:', error);
            toast({
                title: "错误",
                description: (error as Error).message || "创建模型失败",
                variant: "destructive",
            });
        }
    };


    return (
        <Card className="bg-gradient-to-r from-blue-50 to-purple-50 shadow-sm max-w-2xl mx-auto mt-10">
            <CardHeader>
                <CardTitle className="text-gray-800">添加模型</CardTitle>
            </CardHeader>
            <CardContent>
                <form onSubmit={handleSubmit} className="space-y-4 text-gray-800">
                    <MultiSelect
                        options={endpointOptions}
                        value={selectedEndpoints}
                        onChange={setSelectedEndpoints}
                        label="选择能力点"
                    />
                    <div className="space-y-2">
                        <Label htmlFor="modelName">模型名称</Label>
                        <Input
                            id="modelName"
                            value={model.modelName}
                            onChange={(e) => handleChange('modelName', e.target.value)}
                            placeholder="输入模型名称"
                        />
                    </div>
                    <div className="space-y-2">
                        <Label htmlFor="documentUrl">文档 URL</Label>
                        <Input
                            id="documentUrl"
                            value={model.documentUrl}
                            onChange={(e) => handleChange('documentUrl', e.target.value)}
                            placeholder="输入文档 URL"
                        />
                    </div>
                    {propertySchema && (
                        <div className="space-y-2">
                            <Label htmlFor="properties">属性</Label>
                            {propertySchema.params.map((schema) => (
                                <div key={schema.code}>
                                    <Label>{schema.name}</Label>
                                    {renderField(schema, propertyValue[schema.code], (_, value) => handlePropertyChange(schema.code, value))}
                                </div>
                            ))}
                        </div>
                    )}

                    {featureSchema && (
                        <div className="space-y-2">
                            <Label htmlFor="features">特性</Label>
                            {featureSchema.params.map((schema) => (
                                <div key={schema.code}>
                                    <Label>{schema.name}</Label>
                                    {renderField(schema, featureValue[schema.code], (_, value) => handleFeatureChange(schema.code, value))}
                                </div>
                            ))}
                        </div>
                    )}
                    <div className="space-y-2">
                        <Label htmlFor="ownerType">所有者类型</Label>
                        <Input
                            id="ownerType"
                            value={model.ownerType}
                            onChange={(e) => handleChange('ownerType', e.target.value)}
                            placeholder="输入所有者类型"
                        />
                    </div>
                    <div className="space-y-2">
                        <Label htmlFor="ownerType">所有者编码</Label>
                        <Input
                            id="ownerType"
                            value={model.ownerCode}
                            onChange={(e) => handleChange('ownerCode', e.target.value)}
                            placeholder="输入所有者编码"
                        />
                    </div>
                    <div className="space-y-2">
                        <Label htmlFor="ownerName">所有者名称</Label>
                        <Input
                            id="ownerName"
                            value={model.ownerName}
                            onChange={(e) => handleChange('ownerName', e.target.value)}
                            placeholder="输入所有者名称"
                        />
                    </div>
                    <Button type="submit" className="w-full">
                        创建模型
                    </Button>
                </form>
            </CardContent>
        </Card>
    );
}
