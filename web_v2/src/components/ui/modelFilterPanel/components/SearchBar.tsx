import { memo } from "react"
import { Input } from "@/components/common/input"
import { Search, X } from "lucide-react"

interface SearchBarProps {
  value: string
  onChange: (value: string) => void
  onClear: () => void
  placeholder: string
  className?: string
}

/**
 * 搜索栏组件
 * 提供带清空功能的搜索输入框
 */
export const SearchBar = memo(({
  value,
  onChange,
  onClear,
  placeholder,
  className = "",
}: SearchBarProps) => {
  return (
    <div className={`mb-6 ${className}`}>
      <div className="relative">
        <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          placeholder={placeholder}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          className="pl-10 pr-10 text-sm"
        />
        {value && (
          <button
            onClick={onClear}
            className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground transition-colors"
          >
            <X className="h-4 w-4" />
          </button>
        )}
      </div>
    </div>
  )
})

SearchBar.displayName = "SearchBar"
