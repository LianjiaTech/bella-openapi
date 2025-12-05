import { useState } from 'react';
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Label } from "@/components/ui/label";

interface EditableFieldProps {
    label: string;
    value: string;
    onUpdate: (value: string) => void;
    multiline?: boolean;
    options?: Array<{ value: string; label: string }>;
}

export function EditableField({ label, value, onUpdate, multiline, options }: EditableFieldProps) {
    const [isEditing, setIsEditing] = useState(false);
    const [currentValue, setCurrentValue] = useState(value);

    const handleSubmit = () => {
        onUpdate(currentValue);
        setIsEditing(false);
    };

    if (isEditing) {
        return (
            <div className="space-y-2 bg-white bg-opacity-50 p-3 rounded-lg">
                <Label className="text-sm font-medium text-gray-700">{label}</Label>
                {options ? (
                    <Select value={currentValue} onValueChange={setCurrentValue}>
                        <SelectTrigger className="bg-white text-gray-800">
                            <SelectValue placeholder="Select option" />
                        </SelectTrigger>
                        <SelectContent>
                            {options.map(option => (
                                <SelectItem key={option.value} value={option.value}>{option.label}</SelectItem>
                            ))}
                        </SelectContent>
                    </Select>
                ) : multiline ? (
                    <Textarea
                        value={currentValue}
                        onChange={(e) => setCurrentValue(e.target.value)}
                        rows={3}
                        className="bg-white text-gray-800 placeholder-gray-400"
                    />
                ) : (
                    <Input
                        value={currentValue}
                        onChange={(e) => setCurrentValue(e.target.value)}
                        className="bg-white text-gray-800 placeholder-gray-400"
                    />
                )}
                <div className="space-x-2 mt-2">
                    <Button onClick={handleSubmit} variant="default">保存</Button>
                    <Button
                        onClick={() => setIsEditing(false)}
                        variant="default"
                    >
                        取消
                    </Button>
                </div>
            </div>
        );
    }

    return (
        <div className="space-y-1 bg-white bg-opacity-50 p-3 rounded-lg">
            <div className="flex justify-between items-center">
                <Label className="text-sm font-medium text-gray-700">{label}</Label>
                <Button onClick={() => setIsEditing(true)} variant="ghost"
                        className="bg-white text-gragy-600 hover:text-blue-800">
                    编辑
                </Button>
            </div>
            <p className="text-sm text-gray-600">
                {options ? options.find(o => o.value === value)?.label : value}
            </p>
        </div>
    );
}
