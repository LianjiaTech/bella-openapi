import React from 'react';
import { Checkbox } from "@/components/ui/checkbox";
import { Label } from "@/components/ui/label";

interface MultiSelectProps {
    options: string[];
    value: string[];
    onChange: (selected: string[]) => void;
    label: string;
}

const MultiSelect: React.FC<MultiSelectProps> = ({ options, value, onChange, label }) => {
    const handleChange = (option: string, checked: boolean) => {
        if (checked) {
            onChange([...value, option]);
        } else {
            const newValue = value.filter(item => item !== option);
            onChange(newValue.length > 0 ? newValue : []); // 确保当所有选项都被取消时，返回空数组
        }
    };

    return (
        <div className="space-y-2">
            <Label>{label}</Label>
            <div className="space-y-2">
                {options.map((option) => (
                    <div key={option} className="flex items-center space-x-2">
                        <Checkbox
                            id={option}
                            checked={value.includes(option)}
                            onCheckedChange={(checked) => handleChange(option, checked as boolean)}
                        />
                        <Label htmlFor={option}>{option}</Label>
                    </div>
                ))}
            </div>
        </div>
    );
};

export default MultiSelect;
