import type React from "react"
import { SidebarProvider } from "@/lib/context/sidebar-context"

export default function ModelsLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return <SidebarProvider>{children}</SidebarProvider>
}
