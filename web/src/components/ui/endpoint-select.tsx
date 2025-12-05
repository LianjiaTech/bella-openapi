'use client';

import { useState, useEffect, useRef } from 'react';
import { Input } from './input';
import { cn } from '@/lib/utils';

interface EndpointSelectProps {
  value: string;
  onChange: (value: string | null) => void;
  endpoints: string[];
  className?: string;
  onSearch?: (query: string) => void;
  searchValue?: string;
  filteredEndpoints?: string[];
  allowClear?: boolean;
}

export function EndpointSelect({ 
  value, 
  onChange, 
  endpoints, 
  className,
  onSearch,
  searchValue,
  filteredEndpoints,
  allowClear
}: EndpointSelectProps) {
  const [isOpen, setIsOpen] = useState(false);
  const [localSearchQuery, setLocalSearchQuery] = useState('');
  const dropdownRef = useRef<HTMLDivElement>(null);

  const displayEndpoints = filteredEndpoints || endpoints.filter(endpoint =>
    endpoint.toLowerCase().includes(localSearchQuery.toLowerCase())
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

  const handleSearchChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const query = e.target.value;
    setLocalSearchQuery(query);
    onSearch?.(query);
  };

  const selectedEndpoint = value || '选择能力点';

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
          {selectedEndpoint}
        </span>
        <div className="flex items-center">
          {allowClear && value && (
            <button
              onClick={(e) => {
                e.stopPropagation();
                onChange(null);
                setLocalSearchQuery('');
                onSearch?.('');
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
              placeholder="搜索能力点..."
              value={searchValue ?? localSearchQuery}
              onChange={handleSearchChange}
              onClick={(e) => e.stopPropagation()}
            />
          </div>
          <div className="max-h-60 overflow-auto">
            {displayEndpoints.map((endpoint) => (
              <div
                key={endpoint}
                className="px-4 py-2 hover:bg-gray-100 cursor-pointer text-sm"
                onClick={() => {
                  onChange(endpoint);
                  setIsOpen(false);
                  setLocalSearchQuery('');
                  onSearch?.('');
                }}
              >
                {endpoint}
              </div>
            ))}
            {displayEndpoints.length === 0 && (
              <div className="px-4 py-2 text-gray-500 text-center">
                未找到能力点
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
