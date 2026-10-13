/**
 * SSE chunk parsing helpers for chat-completions playground streams.
 */

export interface SSEChunkData {
  type: 'text' | 'code' | 'reasoning_content' | 'image' | 'done' | 'error'
  delta?: string
  lang?: string
  url?: string
  image?: {
    source: string
    mimeType?: string
    append: boolean
  }
  error?: string
}

export type InlineImageTextPart =
  | { type: 'text'; content: string }
  | { type: 'image_start'; mimeType?: string }
  | { type: 'image_data'; content: string }
  | { type: 'image_end' }

const DATA_IMAGE_RE = /^data:(image\/[a-z0-9.+-]+);base64,([\s\S]*)$/i
const URL_RE = /^(https?:\/\/|blob:|\/)/i
const INLINE_IMAGE_START_RE = /<inline>\s*<data(?:\s+mime_type=["']?([^"'>\s]+)["']?)?\s*>/i
const INLINE_IMAGE_END_RE = /<\/data>\s*<\/inline>/i

function asString(value: unknown): string | undefined {
  return typeof value === 'string' && value.length > 0 ? value : undefined
}

function getNestedImageUrl(value: any): string | undefined {
  return asString(value?.image_url?.url) ?? asString(value?.image_url) ?? asString(value?.url)
}

function getMimeType(value: any): string | undefined {
  return (
    asString(value?.mime_type) ??
    asString(value?.mimeType) ??
    asString(value?.media_type) ??
    asString(value?.mediaType)
  )
}

function getImageSource(value: any): string | undefined {
  return (
    asString(value?.url) ??
    getNestedImageUrl(value) ??
    asString(value?.b64_json) ??
    asString(value?.base64) ??
    asString(value?.image_base64) ??
    asString(value?.data)
  )
}

function getImageFromContentParts(content: any[]): SSEChunkData | null {
  for (const part of content) {
    const type = asString(part?.type)
    if (!type?.includes('image')) continue

    const source = getImageSource(part)
    if (!source) continue

    return {
      type: 'image',
      url: getNestedImageUrl(part),
      image: {
        source,
        mimeType: getMimeType(part),
        append: false,
      },
    }
  }

  return null
}

/**
 * Converts a URL, data URL, or raw base64 image payload into a browser-ready src.
 */
export function normalizeImageSource(source: string, mimeType = 'image/png'): string {
  const trimmed = source.trim()
  if (!trimmed) return ''
  const dataImageMatch = trimmed.match(DATA_IMAGE_RE)
  if (dataImageMatch) {
    const base64 = sanitizeBase64Payload(dataImageMatch[2])
    return base64 ? `data:${dataImageMatch[1]};base64,${base64}` : ''
  }
  if (URL_RE.test(trimmed)) return trimmed
  if (/^data:/i.test(trimmed)) return trimmed

  const base64 = sanitizeBase64Payload(trimmed.replace(/^base64,/i, ''))
  return base64 ? `data:${mimeType};base64,${base64}` : ''
}

export function sanitizeBase64Payload(payload: string): string {
  let normalizedPayload = payload.trim()
  try {
    normalizedPayload = decodeURIComponent(normalizedPayload)
  } catch {
    // Keep the original payload when it is not URI encoded.
  }

  const base64MarkerIndex = normalizedPayload.toLowerCase().lastIndexOf('base64,')
  if (base64MarkerIndex >= 0) {
    normalizedPayload = normalizedPayload.slice(base64MarkerIndex + 'base64,'.length)
  }

  let base64 = normalizedPayload
    .replace(/<\/?inline>/gi, '')
    .replace(/<\/?data[^>]*>/gi, '')
    .replace(/-/g, '+')
    .replace(/_/g, '/')
    .replace(/\s/g, '')
    .replace(/[^A-Za-z0-9+/=]/g, '')
    .replace(/=/g, '')

  while (base64.length % 4 === 1) {
    base64 = base64.slice(0, -1)
  }
  const paddingLength = (4 - (base64.length % 4)) % 4
  return base64 ? `${base64}${'='.repeat(paddingLength)}` : ''
}

export function splitInlineImageText(input: string, insideImage = false): {
  parts: InlineImageTextPart[]
  insideImage: boolean
} {
  const parts: InlineImageTextPart[] = []
  let remaining = input
  let isInsideImage = insideImage

  while (remaining) {
    if (isInsideImage) {
      const endIndex = remaining.search(INLINE_IMAGE_END_RE)
      const imagePayload = endIndex >= 0 ? remaining.slice(0, endIndex) : remaining
      if (imagePayload) {
        parts.push({ type: 'image_data', content: imagePayload })
      }

      if (endIndex < 0) {
        remaining = ''
      } else {
        parts.push({ type: 'image_end' })
        remaining = remaining.slice(endIndex).replace(INLINE_IMAGE_END_RE, '')
        isInsideImage = false
      }
      continue
    }

    const startMatch = remaining.match(INLINE_IMAGE_START_RE)
    if (!startMatch || startMatch.index === undefined) {
      parts.push({ type: 'text', content: remaining })
      remaining = ''
      continue
    }

    const textBeforeImage = remaining.slice(0, startMatch.index)
    if (textBeforeImage) {
      parts.push({ type: 'text', content: textBeforeImage })
    }

    parts.push({ type: 'image_start', mimeType: startMatch[1] })
    remaining = remaining.slice(startMatch.index + startMatch[0].length)
    isInsideImage = true
  }

  return {
    parts,
    insideImage: isInsideImage,
  }
}

/**
 * Parses a single SSE data payload into the playground's internal chunk shape.
 */
export function parseSSEChunk(chunk: string): SSEChunkData | null {
  try {
    const cleanChunk = chunk.replace(/^data:\s*/i, '').trim()

    if (!cleanChunk || cleanChunk === '[DONE]') {
      return { type: 'done' }
    }

    const parsed = JSON.parse(cleanChunk)

    if (parsed.choices && parsed.choices[0]?.delta) {
      const delta = parsed.choices[0].delta

      if (delta.reasoning_content !== undefined) {
        return {
          type: 'reasoning_content',
          delta: delta.reasoning_content,
        }
      }

      if (Array.isArray(delta.content)) {
        const imageChunk = getImageFromContentParts(delta.content)
        if (imageChunk) return imageChunk
      }

      if (delta.content !== undefined) {
        return {
          type: 'text',
          delta: delta.content,
        }
      }
    }

    if (parsed.type === 'image') {
      const delta = asString(parsed.delta)
      const source = getImageSource(parsed) ?? delta

      return {
        type: 'image',
        delta,
        url: asString(parsed.url) ?? getNestedImageUrl(parsed),
        image: source
          ? {
              source,
              mimeType: getMimeType(parsed),
              append: Boolean(delta && !parsed.url && !parsed.b64_json && !parsed.base64 && !parsed.image_base64),
            }
          : undefined,
      }
    }

    if (parsed.type) {
      return {
        type: parsed.type,
        delta: parsed.delta ?? parsed.content ?? parsed.text,
        lang: parsed.lang,
        url: parsed.url,
        error: parsed.error,
      }
    }

    return null
  } catch (err) {
    console.warn('[parseSSEChunk] 解析失败:', chunk, err)
    return null
  }
}
