import { Sidebar } from '@/components/layout';
import { SidebarProvider } from '@/components/providers/sidebar-provider';

export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <SidebarProvider>
      <div className="flex min-h-screen bg-sidebar">
        {/* 左侧边栏 */}
        <Sidebar />

        {/* 主内容区域 */}
        <main className="flex-1 ml-64 max-w-[calc(100vw-16rem)]  bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/60">
          <div className="h-full">
            {children}
          </div>
        </main>
      </div>
    </SidebarProvider>
  );
}
