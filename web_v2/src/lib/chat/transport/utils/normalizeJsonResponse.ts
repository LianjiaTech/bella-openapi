// chat/transport/normalize-json.ts

import type { TokenUsage } from '../../store/types'
import type { NormalizedBlock } from '../types'
export interface NormalizedMessageMeta {
  finishReason?: string | null
  usage?: TokenUsage
  model?: string
}

export interface NormalizedResult {
  blocks: NormalizedBlock[]
  meta: NormalizedMessageMeta
}

/**
 * normalize JSON 响应为统一 block 结构
 *
 * 支持结构：
 * 1. OpenAI 兼容格式
 * 2. reasoning_content
 * 3. images 数组
 * 4. 未来扩展
 */
export function normalizeJsonResponse(body: any): NormalizedResult {
  const blocks: NormalizedBlock[] = []

  const choice = body?.choices?.[0]
  const message = choice?.message ?? {}

  // =========================
  // 1️⃣ reasoning_content
  // =========================
  if (typeof message.reasoning_content === 'string' && message.reasoning_content) {
    blocks.push({
      type: 'reasoning_content',
      content: message.reasoning_content,
    })
  }

  // =========================
  // 2️⃣ 普通 text content
  // =========================
  if (typeof message.content === 'string' && message.content) {
    blocks.push({
      type: 'text',
      content: message.content,
    })
  }

  // =========================
  // 3️⃣ 代码结构支持（未来可扩展）
  // 如果后端直接返回结构化 code
  // =========================
  if (Array.isArray(message.code_blocks)) {
    for (const code of message.code_blocks) {
      if (!code?.content) continue
      blocks.push({
        type: 'code',
        content: code.content,
        lang: code.lang,
      })
    }
  }

  // =========================
  // 4️⃣ 图片支持（多模态）
  // =========================
  if (Array.isArray(message.images)) {
    for (const img of message.images) {
      if (!img?.url) continue
      blocks.push({
        type: 'image',
        url: img.url,
        alt: img.alt,
      })
    }
  }

  // =========================
  // 5️⃣ fallback（极端情况）
  // =========================
  if (blocks.length === 0 && typeof body === 'string') {
    blocks.push({
      type: 'text',
      content: body,
    })
  }

  return {
    blocks,
    meta: {
      finishReason: choice?.finish_reason ?? null,
      usage: body?.usage,
      model: body?.model,
    },
  }
}