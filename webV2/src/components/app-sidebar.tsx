"use client"

import {
  Home,
  Sparkles,
  Key,
  ScrollText,
  HelpCircle,
  Settings,
  Activity,
  FolderTree,
  LogIn,
} from "lucide-react"
import Link from "next/link"
import { usePathname } from "next/navigation"
import { cn } from "@/lib/utils"
import { SettingsDialog } from "@/components/settings-dialog"
import { useLanguage } from "@/components/language-provider"
import { PlaygroundSidebarMenu } from "@/components/playground-sidebar-menu"

export function AppSidebar() {
  const pathname = usePathname()
  const { t } = useLanguage()

  return (
    <aside className="fixed left-0 top-0 z-40 h-screen w-64 flex-shrink-0 border-r border-border bg-sidebar">
      <div className="flex h-full flex-col">
        {/* Logo */}
        <div className="flex h-16 items-center border-b border-sidebar-border px-6">
          <Link href="/" className="flex items-center gap-2">
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary">
              <Sparkles className="h-5 w-5 text-primary-foreground" />
            </div>
            <span className="text-lg font-semibold text-sidebar-foreground">Bella</span>
          </Link>
        </div>

        {/* Main Navigation */}
        <nav className="flex-1 space-y-1 overflow-y-auto px-3 py-4">
          <div className="space-y-1">
            {[
              { name: t("home"), href: "/", icon: Home },
              { name: t("models"), href: "/models", icon: Sparkles },
            ].map((item) => {
              const isActive = pathname === item.href
              return (
                <Link
                  key={item.name}
                  href={item.href}
                  className={cn(
                    "flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors",
                    isActive
                      ? "bg-sidebar-accent text-sidebar-accent-foreground bg-gray-300"
                      : "text-gray-500 hover:bg-sidebar-accent/50 hover:text-sidebar-foreground",
                  )}
                >
                  <item.icon className="h-5 w-5" />
                  {item.name}
                </Link>
              )
            })}

            {/* Playground Menu Item */}
            <PlaygroundSidebarMenu pathname={pathname} t={t} />

            {[
              { name: t("apiKeys"), href: "/api-keys", icon: Key },
              { name: t("logs"), href: "/logs", icon: ScrollText },
              { name: t("modelStatus"), href: "/model-status", icon: Activity },
              { name: t("metadata"), href: "/metadata", icon: FolderTree }, // Added metadata navigation item
            ].map((item) => {
              const isActive = pathname === item.href || pathname.startsWith(item.href + "/")
              return (
                <Link
                  key={item.name}
                  href={item.href}
                  className={cn(
                    "flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors",
                    isActive
                      ? "bg-sidebar-accent text-sidebar-accent-foreground bg-gray-300"
                      : "text-gray-500 hover:bg-sidebar-accent/50 hover:text-sidebar-foreground",
                  )}
                >
                  <item.icon className="h-5 w-5" />
                  {item.name}
                </Link>
              )
            })}
          </div>
        </nav>

        {/* Bottom Navigation */}
        <div className="border-t border-sidebar-border p-3 space-y-1">
          {[
            { name: t("settings"), component: "settings" },
            { name: t("login"), href: "/login", icon: LogIn }, // 添加登录导航项
            { name: t("help"), href: "/help", icon: HelpCircle },
          ].map((item) => {
            if (item.component === "settings") {
              return (
                <SettingsDialog key={item.name}>
                  <button
                    className={cn(
                      "flex w-full items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors",
                      "text-gray-500 hover:bg-sidebar-accent/50 hover:text-sidebar-foreground",
                    )}
                  >
                    <Settings className="h-5 w-5" />
                    {item.name}
                  </button>
                </SettingsDialog>
              )
            }

            const isActive = pathname === item.href
            return (
              <Link
                key={item.name}
                href={item.href!}
                className={cn(
                  "flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors",
                  isActive
                    ? "bg-sidebar-accent text-sidebar-accent-foreground bg-gray-300"
                    : "text-gray-500 hover:bg-sidebar-accent/50 hover:text-sidebar-foreground",
                )}
              >
                {
                  item.icon === LogIn ? <LogIn className="h-5 w-5" /> :<HelpCircle className="h-5 w-5" />
                }
                {item.name}
              </Link>
            )
          })}
        </div>
      </div>
    </aside>
  )
}
