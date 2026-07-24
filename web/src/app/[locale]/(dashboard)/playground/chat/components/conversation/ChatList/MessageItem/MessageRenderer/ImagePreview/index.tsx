import { useEffect, useState } from 'react'
import { ImageOff, Loader2 } from 'lucide-react'
import { sanitizeBase64Payload } from '@/lib/chat/transport/utils/parseSSEChunk'

const DATA_IMAGE_RE = /^data:(image\/[a-z0-9.+-]+);base64,([\s\S]*)$/i

interface ImagePreviewProps {
  imageUrl: string
  status?: 'loading' | 'done'
  alt?: string
}

function dataUrlToObjectUrl(dataUrl: string): string {
  const match = dataUrl.match(DATA_IMAGE_RE)
  if (!match) return dataUrl

  const mimeType = match[1]
  const base64 = sanitizeBase64Payload(match[2])
  if (!base64) {
    throw new Error('Empty image payload')
  }
  const byteString = window.atob(base64)
  const chunkSize = 8192
  const chunks: ArrayBuffer[] = []

  for (let offset = 0; offset < byteString.length; offset += chunkSize) {
    const slice = byteString.slice(offset, offset + chunkSize)
    const buffer = new ArrayBuffer(slice.length)
    const bytes = new Uint8Array(buffer)
    for (let index = 0; index < slice.length; index += 1) {
      bytes[index] = slice.charCodeAt(index)
    }
    chunks.push(buffer)
  }

  return URL.createObjectURL(new Blob(chunks, { type: mimeType }))
}

export function ImagePreview({ imageUrl, status, alt }: ImagePreviewProps) {
  const [loadState, setLoadState] = useState<'loading' | 'done' | 'error'>('loading')
  const [resolvedImageUrl, setResolvedImageUrl] = useState('')

  useEffect(() => {
    setLoadState('loading')
    setResolvedImageUrl('')

    if (!imageUrl) return

    let objectUrl = ''
    try {
      const resolvedUrl = dataUrlToObjectUrl(imageUrl)
      if (resolvedUrl.startsWith('blob:')) {
        objectUrl = resolvedUrl
      }
      setResolvedImageUrl(resolvedUrl)
    } catch (error) {
      console.warn('[ImagePreview] 图片数据解析失败:', error)
      setLoadState('error')
    }

    return () => {
      if (objectUrl) {
        URL.revokeObjectURL(objectUrl)
      }
    }
  }, [imageUrl])

  const isPending = (!resolvedImageUrl && loadState !== 'error') || status === 'loading'
  const hasError = loadState === 'error'

  return (
    <div className="my-2 max-w-full overflow-hidden rounded-lg border bg-muted/30">
      {isPending ? (
        <div className="flex h-40 w-full min-w-[16rem] max-w-full items-center justify-center gap-2 px-4 text-sm text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" />
          <span>图片生成中...</span>
        </div>
      ) : hasError ? (
        <div className="flex h-40 w-full min-w-[16rem] max-w-full items-center justify-center gap-2 px-4 text-sm text-destructive">
          <ImageOff className="h-4 w-4" />
          <span>图片加载失败</span>
        </div>
      ) : (
        <img
          src={resolvedImageUrl}
          alt={alt || 'AI generated image'}
          className="block h-auto max-h-[min(70vh,720px)] max-w-full object-contain"
          onLoad={() => setLoadState('done')}
          onError={() => setLoadState('error')}
        />
      )}
    </div>
  )
}
