"use client"

/**
 * Toast Hook (临时简化版)
 * TODO: 后续集成完整的shadcn/ui toast组件
 *
 * 当前使用浏览器原生alert作为临时方案
 */

interface ToastOptions {
  title: string
  description?: string
  variant?: 'default' | 'destructive'
}

export function useToast() {
  const toast = ({ title, description, variant }: ToastOptions) => {
    // 临时使用alert展示消息
    const message = description ? `${title}\n${description}` : title

    if (variant === 'destructive') {
      alert(`❌ ${message}`)
    } else {
      alert(`✓ ${message}`)
    }
  }

  return { toast }
}
