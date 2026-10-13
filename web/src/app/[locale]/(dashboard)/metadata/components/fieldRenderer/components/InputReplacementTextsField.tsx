import React, { useState } from 'react'
import { Button } from '@/components/common/button'
import { Input } from '@/components/common/input'
import { Label } from '@/components/common/label'
import type { TypeSchema } from '@/lib/types/metadata'
import { Plus, Trash2 } from 'lucide-react'

interface InputReplacementTextsFieldProps {
  schema: TypeSchema
  value: any
  onChange: (value: any) => void
  error?: string
}

type Entry = {
  id: string
  key: string
  value: string
}

function createEntry(key = '', value = ''): Entry {
  return {
    id: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
    key,
    value,
  }
}

function entriesFromValue(value: any): Entry[] {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return [createEntry()]
  }
  const entries = Object.entries(value).map(([key, rawValue]) =>
    createEntry(key, rawValue == null ? '' : String(rawValue))
  )
  return entries.length > 0 ? entries : [createEntry()]
}

export const InputReplacementTextsField = ({ schema, value, onChange, error }: InputReplacementTextsFieldProps) => {
  const [entries, setEntries] = useState<Entry[]>(() => entriesFromValue(value))

  const commit = (nextEntries: Entry[]) => {
    setEntries(nextEntries)
    const result: Record<string, string> = {}
    nextEntries.forEach((entry) => {
      result[entry.key] = entry.value
    })
    onChange(result)
  }

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between gap-3">
        <Label className="text-sm font-medium text-gray-700">{schema.name}</Label>
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={() => commit([...entries, createEntry()])}
          className="gap-1"
        >
          <Plus className="h-4 w-4" />
          添加
        </Button>
      </div>

      <div className="space-y-3">
        {entries.map((entry, index) => (
          <div key={entry.id} className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_auto]">
            <div className="space-y-1.5">
              <Label className="text-xs text-muted-foreground">Key</Label>
              <Input
                value={entry.key}
                onChange={(e) => commit(entries.map((item) =>
                  item.id === entry.id ? { ...item, key: e.target.value } : item
                ))}
                placeholder={`key ${index + 1}`}
              />
            </div>
            <div className="space-y-1.5">
              <Label className="text-xs text-muted-foreground">Value</Label>
              <Input
                value={entry.value}
                onChange={(e) => commit(entries.map((item) =>
                  item.id === entry.id ? { ...item, value: e.target.value } : item
                ))}
                placeholder={`value ${index + 1}`}
              />
            </div>
            <div className="flex items-end">
              <Button
                type="button"
                variant="ghost"
                size="icon"
                disabled={entries.length === 1}
                onClick={() => commit(entries.filter((item) => item.id !== entry.id).length > 0
                  ? entries.filter((item) => item.id !== entry.id)
                  : [createEntry()])}
              >
                <Trash2 className="h-4 w-4" />
              </Button>
            </div>
          </div>
        ))}
      </div>

      {error && <p className="text-xs text-red-600">{error}</p>}
    </div>
  )
}
