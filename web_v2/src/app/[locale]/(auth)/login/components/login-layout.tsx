/**
 * 登录页面布局组件
 * 提供居中的卡片式布局和背景渐变
 */
export function LoginLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-background to-muted p-4">
      <div className="w-full max-w-md">
        <div className="bg-card text-card-foreground rounded-lg shadow-lg p-8">
          {children}
        </div>
      </div>
    </div>
  )
}
