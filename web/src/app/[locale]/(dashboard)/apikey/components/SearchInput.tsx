'use client'

import { Search, X, Loader } from "lucide-react";

interface SearchInputProps {
  value: string;                    // 搜索值
  onChange: (value: string) => void; // 变更回调
  placeholder?: string;              // 占位文字(可选，默认: "搜索...")
  isSearching?: boolean;             // 是否正在搜索(可选，默认: false)
  className?: string;                // 容器自定义样式(可选)
}

export function SearchInput({
  value,
  onChange,
  placeholder = "搜索...",
  isSearching = false,
  className = ""
}: SearchInputProps) {
  return (
    <div className={`relative w-64 ${className}`}>
      <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
      <input
        type="text"
        placeholder={placeholder}
        className="pl-10 pr-10 py-2 border rounded-md w-full text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        value={value}
        onChange={(e) => onChange(e.target.value)}
      />
      {isSearching ? (
        <Loader className="absolute right-3 top-1/2 -translate-y-1/2 h-4 w-4 text-blue-500 animate-spin" />
      ) : value ? (
        <button
          onClick={() => onChange("")}
          className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
        >
          <X className="h-4 w-4" />
        </button>
      ) : null}
    </div>
  );
}
