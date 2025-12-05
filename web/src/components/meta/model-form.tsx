import React, {useState} from 'react';
import {Model} from '@/lib/types/openapi';
import {Card, CardContent, CardHeader, CardTitle} from "@/components/ui/card";
import {EditableField} from './editable-field';
import {Button} from "@/components/ui/button";
import {Badge} from "@/components/ui/badge";
import {ConfirmDialog} from '@/components/ui/confirm-dialog';

interface ModelFormProps {
    model: Model;
    onUpdate: (field: keyof Model, value: string) => void;
    onToggleStatus: () => void;
    onToggleVisibility: () => void;
}

export function ModelForm({ model, onUpdate, onToggleStatus, onToggleVisibility }: ModelFormProps) {
    const [isStatusDialogOpen, setIsStatusDialogOpen] = useState(false);
    const [isVisibilityDialogOpen, setIsVisibilityDialogOpen] = useState(false);

    const handleToggleStatus = () => {
        setIsStatusDialogOpen(true);
    };

    const handleToggleVisibility = () => {
        setIsVisibilityDialogOpen(true);
    };

    return (
        <Card className="bg-gradient-to-r from-blue-50 to-purple-50 shadow-sm">
            <CardHeader className="flex flex-col space-y-2">
                <div className="flex items-center justify-between">
                    <CardTitle className="text-gray-800">{model.modelName}</CardTitle>
                    <div className="flex space-x-2">
                        <Badge variant={model.status === 'active' ? "default" : "destructive"}>
                            {model.status === 'active' ? '已启用' : '已停用'}
                        </Badge>
                        <Badge variant={model.visibility === 'public' ? "default" : "secondary"}>
                            {model.visibility === 'public' ? '公开' : '私有'}
                        </Badge>
                    </div>
                </div>
                <div className="flex justify-end space-x-2">
                    <Button
                        onClick={handleToggleStatus}
                        variant={model.status === 'active' ? "destructive" : "default"}
                    >
                        {model.status === 'active' ? '停用' : '启用'}
                    </Button>
                    <Button
                        onClick={handleToggleVisibility}
                        variant={model.visibility === 'public' ? "secondary" : "default"}
                    >
                        {model.visibility === 'public' ? '取消公开' : '公开'}
                    </Button>
                </div>
            </CardHeader>
            <CardContent className="space-y-4">
                <EditableField
                    label="模型软链"
                    value={model.linkedTo}
                    onUpdate={(value) => onUpdate('linkedTo', value)}
                />
                <EditableField
                    label="文档 URL"
                    value={model.documentUrl}
                    onUpdate={(value) => onUpdate('documentUrl', value)}
                />
                <EditableField
                    label="属性"
                    value={model.properties}
                    onUpdate={(value) => onUpdate('properties', value)}
                    multiline
                />
                <EditableField
                    label="特性"
                    value={model.features}
                    onUpdate={(value) => onUpdate('features', value)}
                    multiline
                />
                <EditableField
                    label="所有者类型"
                    value={model.ownerType}
                    onUpdate={(value) => onUpdate('ownerType', value)}
                />
                <EditableField
                    label="所有者编码"
                    value={model.ownerCode}
                    onUpdate={(value) => onUpdate('ownerCode', value)}
                />
                <EditableField
                    label="所有者名称"
                    value={model.ownerName}
                    onUpdate={(value) => onUpdate('ownerName', value)}
                />
            </CardContent>

            <ConfirmDialog
                isOpen={isStatusDialogOpen}
                onClose={() => setIsStatusDialogOpen(false)}
                onConfirm={() => {
                    onToggleStatus();
                    setIsStatusDialogOpen(false);
                }}
                title={`确认${model.status === 'active' ? '停用' : '启用'}模型`}
                description={`您确定要${model.status === 'active' ? '停用' : '启用'}该模型吗？`}
            />

            <ConfirmDialog
                isOpen={isVisibilityDialogOpen}
                onClose={() => setIsVisibilityDialogOpen(false)}
                onConfirm={() => {
                    onToggleVisibility();
                    setIsVisibilityDialogOpen(false);
                }}
                title={`确认${model.visibility === 'public' ? '取消公开' : '公开'}模型`}
                description={`您确定要${model.visibility === 'public' ? '取消公开' : '公开'}该模型吗？`}
            />
        </Card>
    );
}
