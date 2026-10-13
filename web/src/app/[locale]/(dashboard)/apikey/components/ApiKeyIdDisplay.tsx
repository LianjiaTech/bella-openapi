'use client'

import { Copy } from 'lucide-react'
import { Button } from '@/components/common/button'

interface ApiKeyIdDisplayProps {
    code: string
    onCopy: (text: string) => void
}

export function ApiKeyIdDisplay({ code, onCopy }: ApiKeyIdDisplayProps) {
    if (!code) {
        return <span className="text-muted-foreground">-</span>
    }

    return (
        <div className="inline-flex max-w-[160px] items-center gap-1.5 rounded-md border bg-muted/40 px-2 py-1 align-middle">
            <span
                className="min-w-0 flex-1 overflow-x-auto whitespace-nowrap font-mono text-xs text-foreground [scrollbar-width:none] [-ms-overflow-style:none] [&::-webkit-scrollbar]:hidden"
                title={code}
            >
                {code}
            </span>
            <Button
                type="button"
                variant="ghost"
                size="sm"
                className="h-5 w-5 shrink-0 p-0 text-muted-foreground hover:text-foreground"
                onClick={() => onCopy(code)}
                aria-label={`复制密钥ID ${code}`}
            >
                <Copy className="h-3.5 w-3.5" />
            </Button>
        </div>
    )
}
