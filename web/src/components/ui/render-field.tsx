import React, { useState } from 'react';
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Switch } from "@/components/ui/switch";
import { Label } from "@/components/ui/label";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { TypeSchema } from "@/lib/types/openapi";
import { ChevronDown, ChevronRight, Plus, Trash2 } from "lucide-react";

export const renderField = (schema: TypeSchema, value: any, onChange: (path: string, value: any) => void, path: string = '') => {
    const commonProps = {
        className: "w-full p-2 border rounded focus:ring-2 focus:ring-blue-500",
        id: path,
    };

    switch (schema.valueType) {
        case 'enum':
            return (
                <div>
                    <Select
                        value={value || ''}
                        onValueChange={(selectedValue) => onChange(path, selectedValue)}
                    >
                        <SelectTrigger className="w-full">
                            <SelectValue placeholder={`选择 ${schema.name}`} />
                        </SelectTrigger>
                        <SelectContent>
                            {schema.selections?.map((option) => (
                                <SelectItem key={option} value={option}>
                                    {option}
                                </SelectItem>
                            ))}
                        </SelectContent>
                    </Select>
                </div>
            );
        case 'string':
            return (
                <Input
                    {...commonProps}
                    value={value || ''}
                    onChange={(e) => onChange(path, e.target.value)}
                    placeholder={`输入 ${schema.name}`}
                />
            );
        case 'number':
            return (
                <Input
                    {...commonProps}
                    type="number"
                    value={value || ''}
                    onChange={(e) => onChange(path, parseFloat(e.target.value))}
                    placeholder={`输入 ${schema.name}`}
                />
            );
        case 'bool':
            return (
                <div className="flex items-center">
                    <Switch
                        id={path}
                        checked={value || false}
                        onCheckedChange={(checked) => onChange(path, checked)}
                        className="data-[state=checked]:bg-blue-500 data-[state=unchecked]:bg-gray-200"
                    />
                </div>
            );
        case 'array':
            return <ArrayField schema={schema} value={value} onChange={onChange} path={path} />;
        case 'object':
            return (
                <Card className="bg-gradient-to-r from-blue-50 to-purple-50 text-gray-800 shadow-sm rounded-md">
                    <CardContent className="p-4">
                        {schema.child?.params.map((param, index) => (
                            <div key={param.code} className={index !== 0 ? "mt-4" : ""}>
                                {renderField(
                                    param,
                                    value?.[param.code],
                                    (_, nestedValue) => {
                                        const newValue = { ...value, [param.code]: nestedValue };
                                        onChange(path, newValue);
                                    },
                                    `${path}${path ? '.' : ''}${param.code}`
                                )}
                            </div>
                        ))}
                    </CardContent>
                </Card>
            );
        case 'map':
            return (
                <div>
                    <Textarea
                        {...commonProps}
                        value={value ? JSON.stringify(value, null, 2) : ''}
                        onChange={(e) => {
                            try {
                                onChange(path, JSON.parse(e.target.value));
                            } catch (error) {
                                console.error('无效的 JSON 输入', error);
                            }
                        }}
                        placeholder={`输入 ${schema.name} 的 JSON`}
                        rows={5}
                    />
                </div>
            );
        default:
            return (
                <Input {...commonProps} value={value || ''} onChange={(e) => onChange(path, e.target.value)} />
            );
    }
};

const ArrayField = ({ schema, value, onChange, path }: {
    schema: TypeSchema;
    value: any;
    onChange: (path: string, value: any) => void;
    path: string;
}) => {
    const [expandedItems, setExpandedItems] = useState<Set<number>>(new Set());
    const arrayValue = Array.isArray(value) ? value : [];

    const addItem = () => {
        const newItem = schema.child ? {} : '';
        const newArray = [...arrayValue, newItem];
        onChange(path, newArray);
    };

    const removeItem = (index: number) => {
        const newArray = arrayValue.filter((_, i) => i !== index);
        onChange(path, newArray);
    };

    const updateItem = (index: number, newValue: any) => {
        const newArray = [...arrayValue];
        newArray[index] = newValue;
        onChange(path, newArray);
    };

    const toggleExpanded = (index: number) => {
        const newExpanded = new Set(expandedItems);
        if (newExpanded.has(index)) {
            newExpanded.delete(index);
        } else {
            newExpanded.add(index);
        }
        setExpandedItems(newExpanded);
    };

    // If this is an array of objects with schema
    if (schema.child?.params) {
        return (
            <div className="space-y-2">
                <div className="flex items-center justify-between">
                    <Label className="text-sm font-medium">{schema.name} ({arrayValue.length} 项)</Label>
                    <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={addItem}
                        className="flex items-center gap-1"
                    >
                        <Plus className="w-4 h-4" />
                        添加项目
                    </Button>
                </div>
                
                {arrayValue.map((item, index) => {
                    const isExpanded = expandedItems.has(index);
                    return (
                        <Card key={index} className="border border-gray-200">
                            <CardContent className="p-3">
                                <div className="flex items-center justify-between mb-2">
                                    <Button
                                        type="button"
                                        variant="ghost"
                                        size="sm"
                                        onClick={() => toggleExpanded(index)}
                                        className="flex items-center gap-1 p-1 h-auto"
                                    >
                                        {isExpanded ? (
                                            <ChevronDown className="w-4 h-4" />
                                        ) : (
                                            <ChevronRight className="w-4 h-4" />
                                        )}
                                        第 {index + 1} 项
                                    </Button>
                                    <Button
                                        type="button"
                                        variant="ghost"
                                        size="sm"
                                        onClick={() => removeItem(index)}
                                        className="text-red-500 hover:text-red-700 p-1 h-auto"
                                    >
                                        <Trash2 className="w-4 h-4" />
                                    </Button>
                                </div>
                                
                                {isExpanded && (
                                    <div className="space-y-3 pl-4">
                                        {schema.child!.params.map((param) => (
                                            <div key={param.code} className="space-y-1">
                                                <Label className="text-sm text-gray-600">{param.name}</Label>
                                                {renderField(
                                                    param,
                                                    item?.[param.code],
                                                    (_, nestedValue) => {
                                                        const newItem = { ...item, [param.code]: nestedValue };
                                                        updateItem(index, newItem);
                                                    },
                                                    `${path}[${index}].${param.code}`
                                                )}
                                            </div>
                                        ))}
                                    </div>
                                )}
                            </CardContent>
                        </Card>
                    );
                })}
                
                {arrayValue.length === 0 && (
                    <div className="text-center py-8 text-gray-500 border-2 border-dashed border-gray-300 rounded-lg">
                        <p className="mb-2">暂无项目</p>
                        <Button
                            type="button"
                            variant="outline"
                            onClick={addItem}
                            className="flex items-center gap-1"
                        >
                            <Plus className="w-4 h-4" />
                            添加第一个项目
                        </Button>
                    </div>
                )}
            </div>
        );
    }

    // Fallback to simple array handling for primitive types
    return (
        <div>
            <Textarea
                className="w-full p-2 border rounded focus:ring-2 focus:ring-blue-500"
                value={arrayValue.join(', ')}
                onChange={(e) => onChange(path, e.target.value.split(',').map(item => item.trim()))}
                placeholder={`输入 ${schema.name} (用逗号分隔)`}
                rows={3}
            />
        </div>
    );
};
