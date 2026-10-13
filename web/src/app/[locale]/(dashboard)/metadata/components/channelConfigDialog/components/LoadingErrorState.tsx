import { Button } from '@/components/common/button'

interface LoadingErrorStateProps {
  loading?: boolean
  error?: string | null
  onRetry?: () => void
  loadingText?: string
  children?: React.ReactNode
}

/**
 * 加载/错误状态复用组件
 * 用于统一处理加载中、错误、重试等状态
 */
export function LoadingErrorState({
  loading,
  error,
  onRetry,
  loadingText = '加载中...',
  children,
}: LoadingErrorStateProps) {
  // 加载状态
  if (loading) {
    return (
      <div className="text-sm text-muted-foreground">
        {loadingText}
      </div>
    )
  }

  // 错误状态
  if (error) {
    return (
      <div className="rounded-lg border border-red-500/20 bg-red-500/10 p-4">
        <div className="flex items-center justify-between">
          <p className="text-sm text-red-500">{error}</p>
          {onRetry && (
            <Button
              variant="outline"
              size="sm"
              onClick={onRetry}
              className="ml-4 flex-shrink-0"
            >
              重试
            </Button>
          )}
        </div>
      </div>
    )
  }

  // 正常内容
  return <>{children}</>
}
