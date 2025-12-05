import { MetadataFeature } from '@/lib/types/openapi';

interface FeatureFilterProps {
    features: MetadataFeature[];
    onSelect: ((selectedFeatures: string[]) => void) | ((selectedFeatures: string) => void);
    selectedFeatures: string[] | string;
}

export function FeatureFilter({ features, onSelect, selectedFeatures }: FeatureFilterProps) {
    const isMultiSelect = Array.isArray(selectedFeatures);

    const handleFeatureToggle = (featureCode: string) => {
        if (isMultiSelect) {
            const updatedFeatures = (selectedFeatures as string[]).includes(featureCode)
                ? (selectedFeatures as string[]).filter(f => f !== featureCode)
                : [...(selectedFeatures as string[]), featureCode];
            (onSelect as (selectedFeatures: string[]) => void)(updatedFeatures);
        } else {
            (onSelect as (selectedFeatures: string) => void)(selectedFeatures === featureCode ? '' : featureCode);
        }
    };

    const isSelected = (featureCode: string) => {
        if (isMultiSelect) {
            return (selectedFeatures as string[]).includes(featureCode);
        } else {
            return selectedFeatures === featureCode;
        }
    };

    return (
        <div className="mb-6">
            <div className="flex flex-wrap gap-2">
                {features && features.map(feature => (
                    <button
                        key={feature.code}
                        onClick={() => handleFeatureToggle(feature.code)}
                        className={`
                            px-3 py-1 rounded-full text-sm font-medium
                            transition-all duration-200 ease-in-out
                            ${isSelected(feature.code)
                            ? 'bg-blue-400 text-white border-2 border-blue-200 dark:bg-blue-500 dark:border-blue-300'
                            : 'bg-gray-100 text-gray-700 border-2 border-transparent hover:bg-gray-200 dark:bg-gray-700 dark:text-gray-200 dark:hover:bg-gray-600'}
                        `}
                    >
                        {feature.name}
                    </button>
                ))}
            </div>
        </div>
    );
}
