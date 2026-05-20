"use client"

import { Sidebar } from '@/components/layout'
import { SidebarProvider, useSidebar } from '@/components/providers/sidebar-provider'
import { cn } from '@/lib/utils'

function DashboardShell({ children }: { children: React.ReactNode }) {
  const { isDesktop, isSidebarExpanded } = useSidebar()

  return (
    <div className="flex h-screen bg-sidebar">
      <Sidebar />
      <main
        className={cn(
          "flex-1 h-screen bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/60 transition-[margin,max-width] duration-200",
          isDesktop
            ? isSidebarExpanded
              ? "ml-64 max-w-[calc(100vw-16rem)]"
              : "ml-0 max-w-none"
            : "ml-0 max-w-none"
        )}
      >
        <div className="h-full">{children}</div>
      </main>
    </div>
  )
}

export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <SidebarProvider>
      <DashboardShell>{children}</DashboardShell>
    </SidebarProvider>
  )
}
