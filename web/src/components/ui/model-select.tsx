'use client';

import { useState, useEffect, useRef } from 'react';
import { Input } from './input';
import { cn } from '@/lib/utils';

interface ModelSelectProps {
  value: string;
  onChange: (value: string) => void;
  models: string[];
  className?: string;
  allowClear?: boolean;
}

export function ModelSelect({ value, onChange, models, className, allowClear }: ModelSelectProps) {
  const [isOpen, setIsOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const dropdownRef = useRef<HTMLDivElement>(null);

  const filteredModels = models.filter(model =>
    model.toLowerCase().includes(searchQuery.toLowerCase())
  );

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    }

    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const selectedModel = value || '选择模型';

  return (
    <div className="relative" ref={dropdownRef}>
      <div
        className={cn(
          "w-full p-2 border border-gray-300 rounded bg-white cursor-pointer flex justify-between items-center",
          className
        )}
      >
        <span 
          className={value ? "text-gray-900 flex-1" : "text-gray-500 flex-1"}
          onClick={() => setIsOpen(!isOpen)}
        >
          {selectedModel}
        </span>
        <div className="flex items-center">
          {allowClear && value && (
            <button
              onClick={(e) => {
                e.stopPropagation();
                onChange('');
                setSearchQuery('');
              }}
              className="mr-2 text-gray-400 hover:text-gray-600"
            >
              <svg className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor">
                <path
                  fillRule="evenodd"
                  d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z"
                  clipRule="evenodd"
                />
              </svg>
            </button>
          )}
          <svg
            className={`h-5 w-5 text-gray-400 ${isOpen ? 'transform rotate-180' : ''}`}
            viewBox="0 0 20 20"
            fill="currentColor"
            onClick={() => setIsOpen(!isOpen)}
          >
            <path
              fillRule="evenodd"
              d="M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z"
              clipRule="evenodd"
            />
          </svg>
        </div>
      </div>

      {isOpen && (
        <div className="absolute w-full mt-1 bg-white border border-gray-300 rounded-md shadow-lg z-50">
          <div className="p-2">
            <Input
              className="w-full p-2 border border-gray-300 rounded bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
              placeholder="搜索模型..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
            />
          </div>
          <div className="max-h-60 overflow-auto">
            {filteredModels.map((model) => (
              <div
                key={model}
                className="px-4 py-2 hover:bg-gray-100 cursor-pointer text-sm"
                onClick={() => {
                  onChange(model);
                  setIsOpen(false);
                  setSearchQuery('');
                }}
              >
                {model}
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
