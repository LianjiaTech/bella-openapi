import Sidebar from '@/components/sidebar';

export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div className="flex min-h-screen bg-gray-50">
      {/* 左侧边栏 */}
      <Sidebar />

      {/* 主内容区域 */}
      <main className="flex-1 ml-64">
        <div className="h-full">
          {children}
        </div>
      </main>
    </div>
  );
}
